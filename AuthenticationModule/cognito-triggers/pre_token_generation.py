import json
import logging
import os
import time
from typing import Any

import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

PARAM_NAME = os.environ["GROUP_SCOPE_MAPPING_SSM_PARAM_NAME"]
CACHE_TTL_SECONDS = int(os.environ.get("GROUP_SCOPE_MAPPING_SSM_PARAM_CACHE_TTL_SECONDS", "300"))

_ssm = boto3.client("ssm")
_cache: dict[str, Any] = {"mapping": None, "loaded_at": 0.0}


def get_mapping() -> dict[str, list[str]]:
    """Group -> scopes mapping from SSM, cached per container with a TTL."""
    now = time.time()
    if (_cache["mapping"] is None) or ((now - _cache["loaded_at"]) > CACHE_TTL_SECONDS):
        try:
            value = _ssm.get_parameter(Name=PARAM_NAME)["Parameter"]["Value"]
            _cache["mapping"] = json.loads(value)
            _cache["loaded_at"] = now
            logger.info("Loaded group-scope mapping, groups=%s", list(_cache["mapping"]))
        except Exception:
            if _cache["mapping"] is None:
                raise  # nothing to fall back on: fail loudly
            logger.exception("SSM refresh failed, using stale mapping")
    return _cache["mapping"]


def extract_groups(event: dict[str, Any]) -> list[str]:
    return (
        event.get("request", {})
        .get("groupConfiguration", {})
        .get("groupsToOverride")
        or []
    )


def resolve_scopes(groups: list[str], mapping: dict[str, list[str]]) -> list[str]:
    scopes: set[str] = set()
    for group in groups:
        group_scopes = mapping.get(group, [])
        if not group_scopes:
            logger.warning("No scope mapping configured for group=%s", group)
        scopes.update(group_scopes)
    return sorted(scopes)


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    """Cognito Pre Token Generation (V2) trigger: adds scopes by group."""
    try:
        sub = event.get("request", {}).get("userAttributes", {}).get("sub", "unknown")
        groups = extract_groups(event)
        scopes = resolve_scopes(groups, get_mapping())
        logger.info("sub=%s groups=%s scope_count=%d", sub, groups, len(scopes))

        event["response"] = {
            "claimsAndScopeOverrideDetails": {
                "accessTokenGeneration": {"scopesToAdd": scopes}
            }
        }
        return event
    except Exception:
        logger.exception("Pre-token generation failed")
        raise