import logging
from typing import Any

from botocore.exceptions import ClientError

from auth_common import (
    CLIENT_ID,
    _error,
    _json_body,
    _response,
    cognito,
    get_client_secret,
)

logger = logging.getLogger(__name__)


def logout(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Revoke the refresh token (and the access tokens issued from it).

    Body: {"refreshToken": "..."}
    The frontend should also discard its copy of the tokens.
    """
    try:
        data = _json_body(event)
    except ValueError:
        return _response(400, {"message": "Invalid request body"})

    refresh_token = data.get("refreshToken")
    if not isinstance(refresh_token, str) or not refresh_token:
        return _response(400, {"message": "refreshToken is required"})

    try:
        cognito.revoke_token(
            Token=refresh_token,
            ClientId=CLIENT_ID,
            ClientSecret=get_client_secret(),
        )
    except ClientError as error:
        if error.response["Error"]["Code"] == "InvalidParameterException":
            # Token already revoked/expired/invalid: the session is over either way.
            logger.info("Revoke rejected, treating as signed out")
        else:
            return _error(error, "logout", "Invalid refresh token")

    # The refresh token is opaque, so the user's sub is not available here.
    logger.info("logout ok")
    return _response(200, {"message": "Signed out"})
