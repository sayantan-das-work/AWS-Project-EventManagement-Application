## User Management Module
This repository contains the user management functionality for the event management application.

It is responsible for handling user registration, authentication, profile management and manage the user's data.

## Signup API

`POST /users/signup` (public, `Content-Type: application/json`). Plan and decisions: [thought_process/plan_signup.md](thought_process/plan_signup.md).

```bash
curl -i -X POST http://localhost:8080/users/signup \
  -H "Content-Type: application/json" \
  -d '{"first_name":"Asha","last_name":"Rao","gender":"female","date_of_birth":"1995-04-12","email":"asha@example.com","password":"Str0ng#Pass","profession":"Engineer","contact_number":"+919876543210"}'
```

Success: `201 {"userId":"<cognito-sub>","message":"User registered. Please verify your email."}`.
Errors: `{"errorCode":"...","message":"...","errors":[...]}` (`errors` only for validation failures). All codes live in `src/main/resources/errors.json`; field rules in `src/main/resources/validation-rules.json`.

What signup does: creates the Cognito user (email as username, `email_verified=false`, no Cognito email, permanent password), adds it to the `public` group, then stores the profile in DynamoDB with `status=UNVERIFIED` and `expires_at` (now + 24h, epoch seconds). If a later step fails, the Cognito user is deleted. Verification and the 24h cleanup of unverified users are handled outside this module.

### Configuration
Nothing about the SSM layout is hard-coded; everything comes from environment variables (all required unless noted):

| Variable | Meaning | Example |
|----------|---------|---------|
| `SSM_PARAM_PREFIX` | SSM path prefix holding the parameters | `/event-management/auth/` |
| `SSM_PARAM_USER_POOL_ID` | Name of the parameter holding the Cognito user pool id | `user-pool-id` |
| `SSM_PARAM_REGION` | Name of the parameter holding the AWS region of Cognito and DynamoDB | `AWS_REGION` |
| `SSM_PARAM_TABLE_NAME` | Name of the parameter holding the DynamoDB table name | `DYNAMODB_TABLE_NAME` |
| `PORT` | HTTP port (optional, default 8080) | `8080` |

The app reads `<SSM_PARAM_PREFIX><name>` for each of the three parameter names. The app fails fast at startup, logging which variables or parameters are missing.
AWS credentials come from the default provider chain (local profile now, IAM role later). The SSM client uses the default region from that chain (e.g. `AWS_REGION` env var or profile). Profiles created by `aws login` work (the SDK `signin` module is included); run `aws login` again if the session expires.

### Pre-requisites
- Cognito pool allows email as username; the `public` group exists.
- DynamoDB table with partition key `user_id` (String).
- IAM permissions: `cognito-idp:AdminCreateUser`, `AdminSetUserPassword`, `AdminAddUserToGroup`, `AdminDeleteUser` on the pool; `dynamodb:PutItem` on the table; `ssm:GetParametersByPath` on the prefix (plus `kms:Decrypt` if a customer key is used).

### Build and run
```bash
mvn test                # unit tests (JUnit 5 + Mockito)
mvn package             # tests + shaded executable jar
export SSM_PARAM_PREFIX=/event-management/auth/ SSM_PARAM_USER_POOL_ID=user-pool-id \
       SSM_PARAM_REGION=AWS_REGION SSM_PARAM_TABLE_NAME=DYNAMODB_TABLE_NAME
java -jar target/user-management-module-1.0-SNAPSHOT.jar
```

### Known vulnerabilities
Checked against OSV on 2026-11-11 for all runtime dependencies (direct and transitive): none reported. Re-check when upgrading.

## Deploying to EC2 with CodeDeploy

CodeDeploy delivers a source bundle to the instance, which builds the jar with Maven and runs it as a systemd service (`usermgmt`).

Files: [`appspec.yml`](appspec.yml) (module root, which is the bundle root), `deploy/scripts/*.sh` (hooks), `deploy/usermgmt.service` (systemd unit), `deploy/usermgmt.env` (environment variables), `deploy/make-bundle.sh` (builds the bundle).

Because `appspec.yml` lives in this module and not at the repository root, CodeDeploy cannot deploy straight from the GitHub repo (that integration only reads the repo root). Build the bundle with `deploy/make-bundle.sh`, upload it to S3 and deploy from there (see Deploy).

| Hook | What it does |
|------|--------------|
| ApplicationStop | stops the `usermgmt` service if running |
| BeforeInstall | checks the pre-installed JDK 17+ and Maven, creates the `usermgmt` user, removes the previous source |
| (copy) | source to `/opt/usermgmt/source`, unit to `/etc/systemd/system`, env file to `/etc/usermgmt` |
| AfterInstall | `mvn clean package -DskipTests` (downloads all Maven dependencies), installs `/opt/usermgmt/app.jar`, writes a systemd drop-in so the service runs with the same Java that built it; on a build failure the previous version is started again and the deployment fails |
| ApplicationStart | enables and restarts the service |
| ValidateService | waits until `GET /users/signup` answers 405 (the app has no health endpoint) |

### Environment variables
Set in [`deploy/usermgmt.env`](deploy/usermgmt.env) and loaded by systemd: `SSM_PARAM_PREFIX`, `SSM_PARAM_USER_POOL_ID`, `SSM_PARAM_REGION`, `SSM_PARAM_TABLE_NAME`, `PORT`. The file is overwritten on every deployment, so change values in git. It is readable only by root and the service user.

### One-time setup
- Instance: Amazon Linux (2023 or 2), JDK 17+ (with `javac`) and Maven pre-installed, CodeDeploy agent running, outbound internet to Maven Central (about 2 GB RAM recommended for the build).
- Instance profile (IAM role) permissions: `cognito-idp:AdminCreateUser`, `AdminSetUserPassword`, `AdminAddUserToGroup`, `AdminDeleteUser` on the pool; `dynamodb:PutItem` on the table; `ssm:GetParametersByPath` on the prefix (plus `kms:Decrypt` for SecureStrings with a customer key). AWS credentials and the region come from the instance profile and instance metadata.
- Security group: allow inbound TCP on `PORT` (8080) from where the API is called.
- CodeDeploy: create the application and deployment group (EC2 instances), and an S3 bucket for the bundles. The instance profile also needs `s3:GetObject` on that bucket.

### Deploy
```bash
deploy/make-bundle.sh                                   # creates target/usermgmt-bundle.tar.gz
aws s3 cp target/usermgmt-bundle.tar.gz s3://<bucket>/usermgmt/<version>.tar.gz
aws deploy create-deployment \
  --application-name <app> --deployment-group-name <group> \
  --s3-location bucket=<bucket>,key=usermgmt/<version>.tar.gz,bundleType=tgz
```
The bundle script packs every file with mode 755, so the hook scripts are executable even when built on Windows. The bundle holds `appspec.yml`, `deploy/`, `pom.xml` and `src/`; the jar is built on the instance.
### Automated deployment (GitHub Actions)
[`.github/workflows/deploy-user-management-module.yml`](../.github/workflows/deploy-user-management-module.yml) runs only when a change under `UserManagementModule/` is merged into `main`: `mvn verify`, build the bundle, upload it to `s3://<bucket>/usermgmt/<commit-sha>.tar.gz`, create the CodeDeploy deployment and wait for it. It signs in to AWS with OIDC. Create a GitHub environment `USER-MGMT-DEPLOY` with the secret `AWS_ROLE_TO_ASSUME` and the variables `AWS_REGION`, `ARTIFACT_BUCKET`, `CODEDEPLOY_APPLICATION`, `CODEDEPLOY_DEPLOYMENT_GROUP` (the IAM permissions are listed at the top of the workflow). Study notes: [thought_process/interview_guide_cicd_and_design.md](thought_process/interview_guide_cicd_and_design.md).

Logs: `journalctl -u usermgmt` (service) and `/var/log/aws/codedeploy-agent/codedeploy-agent.log` plus `/opt/codedeploy-agent/deployment-root/<id>/<deployment-id>/logs/scripts.log` (hooks).
