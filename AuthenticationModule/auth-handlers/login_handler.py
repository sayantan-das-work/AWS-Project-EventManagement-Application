import logging
from typing import Any

from botocore.exceptions import ClientError

from auth_common import (
    CLIENT_ID,
    USER_POOL_ID,
    _error,
    _json_body,
    _response,
    _tokens,
    cognito,
    secret_hash,
    token_identity,
)

logger = logging.getLogger(__name__)


def login(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Sign a user in. Body: {"username": "<email>", "password": "..."}

    Never log the request body: it contains the password.
    """
    try:
        data = _json_body(event)
    except ValueError:
        return _response(400, {"message": "Invalid request body"})

    username, password = data.get("username"), data.get("password")
    if (
        not isinstance(username, str)
        or not isinstance(password, str)
        or not username
        or not password
    ):
        return _response(400, {"message": "username and password are required"})

    try:
        res = cognito.admin_initiate_auth(
            UserPoolId=USER_POOL_ID,
            ClientId=CLIENT_ID,
            AuthFlow="ADMIN_USER_PASSWORD_AUTH",
            AuthParameters={
                "USERNAME": username,
                "PASSWORD": password,
                # required because the app client has a secret; computed from the
                # same username string that is sent in USERNAME
                "SECRET_HASH": secret_hash(username),
            },
        )
    except ClientError as error:
        return _error(error, "login", "Invalid credentials")

    if "AuthenticationResult" not in res:
        # e.g. NEW_PASSWORD_REQUIRED for users created with a temporary password.
        logger.warning("Unhandled challenge=%s", res.get("ChallengeName"))
        return _response(
            403,
            {
                "message": "Additional step required",
                "challenge": res.get("ChallengeName"),
            },
        )
    result = res["AuthenticationResult"]
    sub, groups = token_identity(result)
    logger.info("login ok sub=%s groups=%s", sub, groups)
    return _response(200, _tokens(result))