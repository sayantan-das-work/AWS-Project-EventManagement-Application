# Cognito Pre-Token Generation Trigger

`pre_token_generation.py` is an AWS Cognito Pre Token Generation (V2) Lambda trigger. It reads a group-to-scope mapping from AWS Systems Manager Parameter Store and adds the mapped scopes to each user's access token. The mapping is cached in the Lambda execution environment and refreshed after its TTL.

## Configuration

| Environment variable | Required | Description |
| --- | --- | --- |
| `GROUP_SCOPE_MAPPING_SSM_PARAM_NAME` | Yes | Name of the SSM parameter containing a JSON object mapping Cognito group names to arrays of scope strings, e.g. `{"Admins":["events/read","events/write"]}`. |
| `GROUP_SCOPE_MAPPING_SSM_PARAM_CACHE_TTL_SECONDS` | No | Cache lifetime in seconds; defaults to `300`. |

The Lambda execution role needs `ssm:GetParameter` permission for the configured parameter and permission to decrypt it if it uses a customer-managed KMS key.

## Runtime and dependencies

- Python 3.9 or later.
- Third-party dependency: `boto3` 1.43.123 (version in the local development environment). AWS Lambda Python runtimes include Boto3; package it with the function if you need to control its version.
- Uses the standard-library modules `json`, `logging`, `os`, `time`, and `typing`.
