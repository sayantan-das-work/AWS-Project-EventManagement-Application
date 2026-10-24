import logging
from typing import Any

logger = logging.getLogger()
logger.setLevel(logging.INFO)


# ==========================================================
# Group -> Scope Mapping
# ==========================================================

GROUP_SCOPE_MAPPING: dict[str, list[str]] = {
    "public": [
        "event_management_server_identifier/events::read",
        "event_management_server_identifier/tickets::book"
    ],
    "event_owner": [
        "event_management_server_identifier/events::read",
        "event_management_server_identifier/events::publish"
    ],
    "event_approvers": [
        "event_management_server_identifier/events::read",
        "event_management_server_identifier/events::approve"
    ],
    "platform_admins": [
        "event_management_server_identifier/events::read",
        "event_management_server_identifier/events::publish",
        "event_management_server_identifier/events::approve",
        "event_management_server_identifier/users::create",
        "event_management_server_identifier/users::modify",
        "event_management_server_identifier/users::remove"
    ],
    "technical": [
        "event_management_server_identifier/technical::sre",
    ],
}


def extract_groups(event: dict[str, Any]) -> list[str]:
    """
    Extract Cognito groups from trigger event.
    """

    return (
        event.get("request", {})
             .get("groupConfiguration", {})
             .get("groupsToOverride", [])
    )


def resolve_scopes(groups: list[str]) -> list[str]:
    """
    Resolve scopes from Cognito groups.
    """

    scopes: set[str] = set()

    for group in groups:
        group_scopes = GROUP_SCOPE_MAPPING.get(group, [])

        if not group_scopes:
            logger.warning(
                "No scope mapping configured for group=%s",
                group
            )

        scopes.update(group_scopes)

    return sorted(scopes)


def lambda_handler(
    event: dict[str, Any],
    context: Any
) -> dict[str, Any]:
    """
    Cognito Pre Token Generation Trigger.

    Adds OAuth scopes to access token based on
    Cognito group membership.
    """

    try:

        username = (
            event.get("userName")
            or "UNKNOWN_USER"
        )

        groups = extract_groups(event)

        logger.info(
            "Processing user=%s groups=%s",
            username,
            groups
        )

        scopes = resolve_scopes(groups)

        logger.info(
            "Generated scopes=%s",
            scopes
        )

        event["response"] = {
            "claimsAndScopeOverrideDetails": {
                "accessTokenGeneration": {
                    "scopesToAdd": scopes
                }
            }
        }

        return event

    except Exception:
        logger.exception(
            "Failed processing pre-token generation trigger"
        )
        raise
