# Authentication shared Lambda layer

This directory contains the shared authentication module and the dependencies
used by the login, refresh, and logout Lambda functions. The `python` directory
is the layer's Python import root; keep `auth_common.py` there so each function
can import it with `from auth_common import ...` when the layer is attached.

## GitHub Actions publishing

The workflow at
`.github/workflows/publish-auth-shared-layer.yml` builds and publishes a new
layer version when files in this directory change on the default branch. It
can also be run manually with **Actions → Publish auth shared Lambda layer →
Run workflow**.

Configure these repository (or organization) settings in **Settings → Secrets
and variables → Actions**:

| Name | Type | Purpose |
| --- | --- | --- |
| `AWS_ROLE_TO_ASSUME` | Secret | ARN of the IAM role that GitHub Actions assumes through OIDC |
| `AWS_REGION` | Variable | AWS Region where the Lambda functions are deployed |
| `AUTH_SHARED_LAYER_NAME` | Variable | Layer name to publish, such as `event-auth-shared` |

Create an IAM OIDC provider for `token.actions.githubusercontent.com` in the
AWS account if one does not already exist. Give the role a trust policy scoped
to this repository and the `main` branch (or the repository's actual default
branch), with audience `sts.amazonaws.com`. This workflow runs on `main`; if
the default branch changes, update the workflow branch filter and this trust
policy to match. For example, replace the account, organization, and repository
placeholders:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:<ORG>/<REPO>:ref:refs/heads/main"
        }
      }
    }
  ]
}
```

Attach a permissions policy to that role allowing `lambda:PublishLayerVersion`
on the intended layer and its versions, for example
`arn:aws:lambda:<REGION>:<ACCOUNT_ID>:layer:<LAYER_NAME>:*`. The workflow only
publishes layer versions; it does not change function configuration. The
published layer version ARN is displayed in the workflow summary.

The workflow builds a ZIP with this structure:

```text
python/
  auth_common.py
  boto3/
  botocore/
  ...
```

After a successful run, use the published layer version ARN in each function's
configuration. Attach it to the login, refresh, and logout functions in the
same Region and account, and ensure the layer's Python runtime (currently
Python 3.12 in the workflow) matches the functions. Keep the corresponding
handler modules in their individual function deployment packages, with handler
settings `login_handler.login`, `refresh_handler.refresh`, and
`logout_handler.logout`.

The function execution roles still need their own Cognito permissions and
environment variables (`USER_POOL_ID` and `CLIENT_ID`). Publishing a layer
does not grant these permissions or configure those variables.

## Local layer build

From the repository root in a Python 3.12 environment:

```powershell
$layer = "AuthenticationModule\lambda_auth_shared_layer"
$stage = Join-Path $env:TEMP "auth-shared-layer"
New-Item -ItemType Directory -Force "$stage/python" | Out-Null
Copy-Item "$layer\python\auth_common.py" "$stage\python\"
python -m pip install -r "$layer\requirements.txt" --target "$stage\python"
Compress-Archive -Path "$stage\python" -DestinationPath "$stage\auth-shared-layer.zip" -Force
```
