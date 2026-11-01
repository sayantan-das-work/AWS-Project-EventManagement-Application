# Authentication Module

Backend login for the Event Management app, built with **AWS SAM**. Your own login
page sends an email and password to this API. The API signs the user in with
**Amazon Cognito** and returns tokens. There is no Cognito hosted login page.

## 1. How it works

```
Login page ──POST /auth/login {username,password}──▶ API Gateway ──▶ LoginFn
                                                                      │ AdminInitiateAuth
                                                                      ▼
                                                            Cognito User Pool
                                                                      │ (before issuing tokens)
                                                                      ▼
                                                      PreTokenFn  ◀── SSM Parameter (group → scopes)
                                                                      │ adds scopes to the access token
                                                                      ▼
Login page ◀── { accessToken, idToken, refreshToken, expiresIn } ─────┘
```

After login the frontend:

| Need | Call |
|---|---|
| Call a protected API | Send `Authorization: Bearer <accessToken>` |
| Access token expired (15 min) | `POST /auth/refresh {refreshToken}` |
| Sign out | `POST /auth/logout {refreshToken}` |

## 2. Folder layout

```
AuthenticationModule/
├── template.yaml                 # SAM template: every AWS resource
├── samconfig.toml                # `sam deploy` defaults (stack name, region)
├── auth-handlers/                # the three API Lambdas
│   ├── login_handler.py
│   ├── refresh_handler.py
│   └── logout_handler.py
├── cognito-triggers/
│   └── pre_token_generation.py   # runs inside Cognito, adds scopes
└── lambda_auth_shared_layer/
    └── python/auth_common.py     # shared helpers (a Lambda layer)
```

## 3. Handlers

All three accept and return JSON. Request bodies are never logged, because they
contain passwords and tokens.

### `POST /auth/login` — [login_handler.py](auth-handlers/login_handler.py)
- **Body:** `{"username": "<email>", "password": "..."}`
- **Does:** calls Cognito `AdminInitiateAuth` with the `ADMIN_USER_PASSWORD_AUTH` flow
  and a `SECRET_HASH` (needed because the app client has a secret).
- **200:** `{accessToken, idToken, refreshToken, expiresIn, tokenType}`
- **400** missing fields · **401** wrong credentials (same message for wrong password
  and unknown user) · **403** account needs an extra step (for example
  `NEW_PASSWORD_REQUIRED`) · **429** too many attempts
- **Log:** `login ok sub=<uuid> groups=[...]`

### `POST /auth/refresh` — [refresh_handler.py](auth-handlers/refresh_handler.py)
- **Body:** `{"refreshToken": "..."}`
- **Does:** exchanges the refresh token for new access and id tokens using Cognito's
  `GetTokensFromRefreshToken` API. It needs no username. The response has no new
  refresh token, so keep using the original.
- **Log:** `refresh ok sub=<uuid> groups=[...]`

### `POST /auth/logout` — [logout_handler.py](auth-handlers/logout_handler.py)
- **Body:** `{"refreshToken": "..."}`
- **Does:** calls Cognito `RevokeToken`. This kills the refresh token and every access
  token issued from it. If the token is already invalid, it still returns 200.
- **Why a refresh token?** It is the long-lived credential (30 days). Revoking it is
  what actually ends the session. Deleting tokens in the browser is not enough.
- The route is public on purpose. The refresh token is the credential, so logout works
  even after the access token has expired.

### Pre-token trigger — [pre_token_generation.py](cognito-triggers/pre_token_generation.py)
- Cognito calls it before every token is issued (login and refresh).
- Reads the user's groups, looks up the group → scopes JSON in SSM, and adds those
  scopes to the **access token** (`scopesToAdd`). The mapping is cached for 5 minutes.
- If the mapping cannot be loaded and nothing is cached, it fails and the login fails.
  Tokens should not be issued without the right scopes.

### Shared layer — [auth_common.py](lambda_auth_shared_layer/python/auth_common.py)
Response and JSON body helpers, the `SECRET_HASH` calculation, a cached read of the
client secret, the refresh-token call, error mapping, and `token_identity` (reads
`sub` and groups from a token, for logging only).

## 4. AWS services and their configuration

### Amazon Cognito — User Pool (`UserPool`)
| Setting | Value | Why |
|---|---|---|
| Tier | `ESSENTIALS` | Required for the V2 pre-token trigger |
| Username | email | Users sign in with their email |
| Sign-up | `AllowAdminCreateUserOnly: true` | Only admins create users |
| Password | min length 8 | |
| Trigger | `PreTokenGeneration` V2_0 → `PreTokenFn` | Adds scopes |

**App client (`UserPoolClient`)**
| Setting | Value | Why |
|---|---|---|
| `GenerateSecret` | true | Confidential client; only the backend holds the secret |
| Auth flows | `ADMIN_USER_PASSWORD_AUTH`, `REFRESH_TOKEN_AUTH` | Login and refresh |
| `EnableTokenRevocation` | true | Required by `RevokeToken` |
| `PreventUserExistenceErrors` | ENABLED | Hides whether an email exists |
| Token lifetimes | access 15 min · id 15 min · refresh 30 days | |

**Groups:** `public`, `event_owners`, `event_approvers`, `platform_admins`, `technical`.
**Resource server** `event-management-application` defines the custom scopes
(`users::read`, `events::publish`, `tickets::book`, and so on).

### AWS Lambda
| Function | Handler | Extra IAM permissions |
|---|---|---|
| `LoginFn` | `login_handler.login` | `cognito-idp:DescribeUserPoolClient`, `cognito-idp:AdminInitiateAuth` |
| `RefreshFn` | `refresh_handler.refresh` | `cognito-idp:DescribeUserPoolClient` |
| `LogoutFn` | `logout_handler.logout` | `cognito-idp:DescribeUserPoolClient` |
| `PreTokenFn` | `pre_token_generation.lambda_handler` | `ssm:GetParameter` on the one mapping parameter |

Common settings: Python 3.14, 256 MB, 10 s timeout (5 s for `PreTokenFn`, because
Cognito only waits about 5 seconds for triggers). Environment variables:
`USER_POOL_ID`, `CLIENT_ID` for the API handlers, and the SSM parameter name and
cache TTL for `PreTokenFn`. Each function gets its own CloudWatch log group the
first time it runs.

The client secret is read from Cognito at runtime and cached in memory. It is never
stored in environment variables, the template or SSM.

### Amazon API Gateway (HTTP API, `HttpApi`)
- Routes: `POST /auth/login`, `/auth/refresh`, `/auth/logout`. All three are public.
- **CORS:** only the origin in the `AllowedOrigin` parameter (default
  `http://localhost:3000`); methods `POST, OPTIONS`; headers `Content-Type, Authorization`.
- **Throttling:** 10 requests/second, burst 20.
- **Authorizer `CognitoJwt`** is defined but unused so far. Attach it to a route with
  `Auth: { Authorizer: CognitoJwt }`. It checks the token's issuer and audience.

### AWS Systems Manager — Parameter Store
| Parameter (under `/event-management/auth`) | Holds |
|---|---|
| `group-scope-mapping` | JSON: group → list of scopes (read by `PreTokenFn`) |
| `user-pool-id`, `user-pool-arn`, `client-id`, `api-url` | Published for other modules to read |

### AWS CloudFormation (via SAM)
`template.yaml` is a SAM template. `sam deploy` turns it into a CloudFormation stack
(`auth-module`, region `ap-south-1`, see [samconfig.toml](samconfig.toml)). The
`AWS::LanguageExtensions` transform is used for `Fn::ToJsonString` (it writes the
scope mapping as JSON).

### Amazon CloudWatch Logs
Log lines never contain email, username, password or tokens. They show only the
user's `sub` (an opaque id) and groups.

## 5. Deploy and try it

```bash
cd AuthenticationModule
sam build          # your Python on PATH must match the runtime in template.yaml (3.14)
sam deploy         # reads samconfig.toml; note the ApiUrl in the outputs
```

Create a user (sign-up is admin-only) with a **permanent** password and a group:

```bash
aws cognito-idp admin-create-user --user-pool-id <UserPoolId> \
  --username you@example.com --message-action SUPPRESS
aws cognito-idp admin-set-user-password --user-pool-id <UserPoolId> \
  --username you@example.com --password '<StrongPass1>' --permanent
aws cognito-idp admin-add-user-to-group --user-pool-id <UserPoolId> \
  --username you@example.com --group-name public
```

```bash
curl -X POST <ApiUrl>/auth/login -H "Content-Type: application/json" \
  -d '{"username":"you@example.com","password":"<StrongPass1>"}'
```

To change which scopes a group gets, edit the mapping in `template.yaml` and redeploy.
`PreTokenFn` picks up the change within 5 minutes.

## 6. Learnings for beginners

1. **Hosted UI vs your own login page.** Cognito's hosted login page talks to Cognito
   directly, so your Lambdas are never called. That is why there were no logs at first.
   Your own page must call your API.
2. **A Lambda's log group appears only after its first run.** No log group means the
   function has not been invoked.
3. **Auth flows must be enabled on the app client.** `ADMIN_USER_PASSWORD_AUTH` has to
   be listed in `ExplicitAuthFlows`, and the Lambda also needs the
   `cognito-idp:AdminInitiateAuth` IAM permission. Both are required.
4. **Client secret means `SECRET_HASH`.** If the app client has a secret, every call
   that names a user must include `HMAC-SHA256(secret, username + clientId)`, base64
   encoded. It must use the same username string you send.
5. **Admin-created users start in `FORCE_CHANGE_PASSWORD`.** Set a permanent password
   or login returns a `NEW_PASSWORD_REQUIRED` challenge instead of tokens.
6. **Access, id and refresh tokens do different jobs.** Access token: call APIs
   (short-lived). Id token: who the user is. Refresh token: get new ones, and revoke
   it to log out. Treat the refresh token like a password.
7. **Don't leak whether a user exists.** Wrong password and unknown user both return
   the same 401 (`PreventUserExistenceErrors` plus the handler's mapping).
8. **Never log secrets or PII.** Log the `sub` and groups, not emails, passwords or
   tokens. Decoding a JWT to read claims is fine for display or logs. Only the
   API Gateway authorizer should be trusted to verify one.
9. **Least privilege IAM.** Each function only gets the actions it uses, scoped to
   this user pool (SAM's `Policies:` section writes the IAM role for you).
10. **CORS is enforced by the browser.** `curl` works while the browser fails until
    `AllowOrigins` matches the page's origin exactly (scheme + host + port).
11. **Lambda cold starts.** Code outside the handler (clients, cache) runs once per
    container. That is why the client secret and the SSM mapping are cached there.
12. **Infrastructure as code.** Change `template.yaml`, then `sam build && sam deploy`.
    Avoid clicking in the console, because CloudFormation can overwrite manual edits.
13. **Removing a resource deletes it.** Taking the user pool domain out of the template
    deletes it on deploy. Read the change set (`confirm_changeset = true`) before you
    confirm.
14. **`sessionStorage` is convenient but not safest** for tokens in a browser. For
    production, keep the refresh token in an `HttpOnly` cookie set by the backend.

## 7. Ideas for next steps
- Call `/auth/refresh` automatically before the access token expires.
- Attach the `CognitoJwt` authorizer to the first real API route and check scopes in
  that Lambda.
- Add alarms on `login rejected` counts and Lambda errors.
