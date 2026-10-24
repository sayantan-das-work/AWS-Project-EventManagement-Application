# Deploy the Cognito Pre Token Generation Lambda

This folder contains a Python AWS Lambda function that runs before Amazon Cognito issues tokens. It maps a user's Cognito groups to OAuth scopes and adds those scopes to the user's **access token**.

## What you need

- An AWS account and permission to create Lambda functions and configure a Cognito user pool.
- An Amazon Cognito user pool in the AWS Region where you plan to create the function.
- A user pool on the **Essentials** or **Plus** feature plan. Access-token customization requires a version 2 (`V2_0`) pre-token-generation event.
- PowerShell (the commands below are for Windows).

## 1. Create the deployment ZIP

Open PowerShell in this folder:

```powershell
cd "C:\path\to\Cognito-PreAuthToken-Lambda-Trigger"
Compress-Archive -Path .\lambda_handler.py -DestinationPath .\lambda.zip -Force
```

Replace the example path with the actual location of this folder. The ZIP must contain `lambda_handler.py` at its top level. Do not include `.venv`, `.idea`, or the whole project.

This function uses only Python's standard library, so no dependencies need to be installed or packaged.

## 2. Create the Lambda function

1. Sign in to the [AWS Management Console](https://console.aws.amazon.com/) and select the same AWS Region as your Cognito user pool.
2. Open **Lambda** and choose **Create function**.
3. Select **Author from scratch**.
4. Enter a function name, such as `cognito-pre-token-generation`.
5. Select **Python 3.14** as the runtime.
6. For **Permissions**, create a new execution role with basic Lambda permissions. This gives the function permission to write its logs to CloudWatch; this code does not need access to other AWS services.
7. Choose **Create function**.

## 3. Upload the code

1. On the function page, open the **Code** tab.
2. Choose **Upload from** → **.zip file**, select the `lambda.zip` you created, and confirm the upload.
3. Choose **Deploy**.
4. In **Runtime settings**, choose **Edit** and set **Handler** to:

   ```text
   lambda_handler.lambda_handler
   ```

   Save the setting if it was not already set.

## 4. Configure the group-to-scope mapping

1. On the function page, open **Configuration** → **Environment variables**.
2. Choose **Edit** → **Add environment variable**.
3. Set the key to `GROUP_SCOPE_MAPPING`.
4. Set the value to valid JSON mapping Cognito group names to lists of scope names, for example:

   ```json
   {"EventManagers":["events/read","events/write"],"Attendees":["events/read"]}
   ```

5. Save the change.

Use the exact Cognito group names. If a user's group has no entry in the mapping, the function logs a warning and adds no scopes for that group.

## 5. Connect the function to Cognito

1. Open **Amazon Cognito** and select **User pools**.
2. Open your user pool and find its **Lambda triggers** or **Extensions → Lambda triggers** settings (the console layout may vary).
3. Choose **Pre token generation** and select the Lambda function you created.
4. Set the trigger event version to **V2_0** / **Basic features + access token customization for user identities**.
5. Save the trigger configuration. Allow Cognito to invoke the Lambda when prompted.

The `V2_0` event is needed because this function returns `scopesToAdd` for access-token customization. `V2_0` requires the Essentials or Plus user-pool feature plan.

## 6. Make sure Cognito knows the scopes

If you are using custom scopes such as `events/read` or `events/write`:

1. In the user pool, create or confirm a **Resource server** and define those scopes.
2. In the app client's allowed OAuth scopes, select the scopes your application needs.
3. Confirm your application uses the relevant app client and requests the appropriate OAuth flow.

The Lambda adds scopes based on a user's groups; it does not create scopes or configure the app client.

## 7. Test the trigger

1. Make sure a test user belongs to one or more of the mapped Cognito groups.
2. Sign in through your application or another supported Cognito sign-in flow to obtain a new token.
3. Inspect the **access token** (not just the ID token) and confirm its `scope` claim includes the expected scopes.
4. If the scopes are missing, check the function's **Monitor** tab and open its CloudWatch logs. Look for invocation errors or a `No scope mapping configured` warning, and verify the group names, environment-variable JSON, trigger version, and app-client scope configuration.

## Updating the function later

After changing `lambda_handler.py`, recreate the ZIP with the command in step 1, upload it to the existing function, and choose **Deploy**. Update `GROUP_SCOPE_MAPPING` through the Lambda environment-variable settings if the mapping changes.

## Important notes

- Lambda and the Cognito user pool must be in the same AWS Region for this trigger configuration.
- Do not put passwords, API keys, or other secrets in the mapping or source code.
- Lambda invocations and CloudWatch logs may incur AWS charges. Review AWS pricing and set a billing alert if you are learning on a personal account.
