import base64
import hashlib
import hmac
import json
import logging
import os
import urllib.error
import urllib.request
from typing import Any

import boto3
from botocore.exceptions import ClientError

logger = logging.getLogger()
logger.setLevel(logging.INFO)

USER_POOL_ID = os.environ.get("USER_POOL_ID")
CLIENT_ID = os.environ.get("CLIENT_ID")
REGION = os.environ.get("AWS_REGION")  # set automatically by Lambda
HTTP_TIMEOUT_SECONDS = 5

cognito = boto3.client("cognito-idp")
_client_secret: str | None = None  # cached per container, never logged


class CognitoApiError(Exception):
    """Error returned by a direct HTTPS call to the Cognito API."""

    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


# ---------------------------------------------------------------- responses

def _response(status: int, body: dict[str, Any]) -> dict[str, Any]:
    return {
        "statusCode": status,
        "headers": {"Content-Type": "application/json", "Cache-Control": "no-store"},
        "body": json.dumps(body),
    }


def _json_body(event: dict[str, Any]) -> dict[str, Any]:
    raw = event.get("body") or "{}"
    if event.get("isBase64Encoded"):
        raw = base64.b64decode(raw).decode("utf-8")
    data = json.loads(raw)
    if not isinstance(data, dict):
        raise ValueError("Body must be a JSON object")
    return data


def _tokens(result: dict[str, Any]) -> dict[str, Any]:
    """Map Cognito's AuthenticationResult to the API response (camelCase)."""
    mapping = {
        "accessToken": result.get("AccessToken"),
        "idToken": result.get("IdToken"),
        "refreshToken": result.get("RefreshToken"),
        "expiresIn": result.get("ExpiresIn"),
        "tokenType": result.get("TokenType"),
    }
    return {key: value for key, value in mapping.items() if value is not None}


def token_identity(result: dict[str, Any]) -> tuple[str | None, list[str]]:
    """Return (sub, groups) from an AuthenticationResult, for logging only.

    Reads the access token payload without verifying it: the token was just
    issued by Cognito to this function, and the value is never used for access
    decisions. Returns (None, []) if it cannot be read. No email or username.
    """
    try:
        payload = result["AccessToken"].split(".")[1]
        claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        return claims.get("sub"), claims.get("cognito:groups", [])
    except (KeyError, IndexError, ValueError, AttributeError):
        return None, []


# ------------------------------------------------------------ client secret

def get_client_secret() -> str:
    """Read the app client's secret from Cognito once per container and cache it.

    Needs cognito-idp:DescribeUserPoolClient on the pool (granted in template.yaml).
    The secret is not stored in env vars, SSM or the template.
    """
    global _client_secret
    if _client_secret is None:
        res = cognito.describe_user_pool_client(
            UserPoolId=USER_POOL_ID, ClientId=CLIENT_ID
        )
        _client_secret = res["UserPoolClient"]["ClientSecret"]
    return _client_secret


def secret_hash(username: str) -> str:
    """SECRET_HASH = Base64(HMAC-SHA256(key=client secret, msg=username + client id))."""
    digest = hmac.new(
        get_client_secret().encode("utf-8"),
        (username + CLIENT_ID).encode("utf-8"),
        hashlib.sha256,
    ).digest()
    return base64.b64encode(digest).decode("ascii")


# ------------------------------------------------------------------ refresh

def get_tokens_from_refresh_token(refresh_token: str) -> dict[str, Any]:
    """Call Cognito's GetTokensFromRefreshToken API with the client secret.

    It needs no username and no SECRET_HASH. It is an unsigned (no IAM) API, so it
    is called over HTTPS directly; that keeps it independent of which boto3 version
    the Lambda runtime happens to bundle.
    """
    payload = json.dumps(
        {
            "ClientId": CLIENT_ID,
            "ClientSecret": get_client_secret(),
            "RefreshToken": refresh_token,
        }
    ).encode("utf-8")
    request = urllib.request.Request(
        url=f"https://cognito-idp.{REGION}.amazonaws.com/",
        data=payload,
        method="POST",
        headers={
            "Content-Type": "application/x-amz-json-1.1",
            "X-Amz-Target": "AWSCognitoIdentityProviderService.GetTokensFromRefreshToken",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT_SECONDS) as res:
            return json.loads(res.read().decode("utf-8"))
    except urllib.error.HTTPError as http_error:
        code = "UnknownError"
        try:
            code = json.loads(http_error.read().decode("utf-8")).get("__type", code)
            code = code.split("#")[-1]
        except ValueError:
            pass
        raise CognitoApiError(code) from None
    except (urllib.error.URLError, TimeoutError):
        raise CognitoApiError("Unreachable") from None


# ------------------------------------------------------------------- errors

def _error_code(error: Exception) -> str:
    if isinstance(error, ClientError):
        return error.response["Error"]["Code"]
    if isinstance(error, CognitoApiError):
        return error.code
    return type(error).__name__


def _error(error: Exception, action: str, unauthorized_msg: str) -> dict[str, Any]:
    code = _error_code(error)
    if code in (
        "NotAuthorizedException",
        "UserNotFoundException",
        "RefreshTokenReuseException",
    ):
        # same answer for "wrong password" and "no such user": no user enumeration
        # (the log records only the action and Cognito code, never who tried)
        logger.info("%s rejected, cognito_error=%s", action, code)
        return _response(401, {"message": unauthorized_msg})
    if code in ("PasswordResetRequiredException", "UserNotConfirmedException"):
        return _response(403, {"message": "Account requires action"})
    if code in ("TooManyRequestsException", "LimitExceededException"):
        return _response(429, {"message": "Too many attempts, try again later"})
    logger.error("%s failed, cognito_error=%s", action, code)
    return _response(500, {"message": "Internal error"})