import logging
from typing import Any

from auth_common import (
    CognitoApiError,
    _error,
    _json_body,
    _response,
    _tokens,
    get_tokens_from_refresh_token,
    token_identity,
)

logger = logging.getLogger(__name__)


def refresh(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Get new access/id tokens. Body: {"refreshToken": "..."}"""
    try:
        data = _json_body(event)
    except ValueError:
        return _response(400, {"message": "Invalid request body"})

    refresh_token = data.get("refreshToken")
    if not isinstance(refresh_token, str) or not refresh_token:
        return _response(400, {"message": "refreshToken is required"})

    try:
        res = get_tokens_from_refresh_token(refresh_token)
    except CognitoApiError as error:
        return _error(error, "refresh", "Invalid or expired refresh token")

    # Normally no new refresh token comes back (only with refresh token rotation),
    # so the frontend keeps using the original one.
    result = res["AuthenticationResult"]
    sub, groups = token_identity(result)
    logger.info("refresh ok sub=%s groups=%s", sub, groups)
    return _response(200, _tokens(result))