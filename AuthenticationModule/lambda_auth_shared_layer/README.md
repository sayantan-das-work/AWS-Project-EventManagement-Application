# Authentication shared Lambda layer

Shared code for the login, refresh and logout Lambda functions.

The `python/` folder is the layer's import root. Keep `auth_common.py` there, and
each function can then use `from auth_common import ...` once the layer is attached.

## How it is built and deployed

The layer is defined in [../template.yaml](../template.yaml) as `AuthCommonLayer`
and attached to the three API functions. `sam build` packages this folder and
`sam deploy` publishes a new layer version and updates the functions to use it.
The GitHub Actions workflow `deploy-auth-module.yml` does this on merge to `main`.
There is no separate publish step.

## Dependencies

None to install. `boto3` and `botocore` are already included in the Lambda Python
runtime, and everything else used is from the standard library. If you ever add a
third-party package, put a `requirements.txt` next to `python/` and `sam build`
will install it into the layer.

## Local check

From `AuthenticationModule/`:

```powershell
sam build
```
