import json
import logging
import os
from typing import Any

logger = logging.getLogger()
logger.setLevel(logging.INFO)


# ==========================================================
# Group -> Scope Mapping
# ==========================================================

GROUP_SCOPE_MAPPING_ENV_INPUT = os.environ.get("GROUP_SCOPE_MAPPING")
GROUP_SCOPE_MAPPING: dict[str, list[str]] = json.loads(GROUP_SCOPE_MAPPING_ENV_INPUT) if GROUP_SCOPE_MAPPING_ENV_INPUT else {}
print("GROUP_SCOPE_MAPPING loaded in context:: ", GROUP_SCOPE_MAPPING)


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

    except Exception as e:
        logger.exception(
            "Failed processing pre-token generation trigger",
            e,
            exc_info=True
        )
        raise
