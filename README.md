# AWS-Project-EventManagement-Application
Project for hands-on learning of AWS.

### Services used:
#### Lambda
<a id="cognito-group-to-scopes-pre-token-generation-trigger-lambda"></a>
- **cognito-group-to-scopes-pre-token-generation-trigger-lambda**
    - created in region: ap-south-1
    - Alias created
    - Version published
    - Created to run cognito pre-Authentication Token Generation trigger occurs. It adds scopes to the access token based on the user group of the logged in cognito user.

#### API Gateway
- CognitoAccessTokenTestApi
    - created in region: ap-south-1
    - api id: ochigs3t6f


#### Cognito
- User pool name: `User pool - ahhif`
    - created in region: ap-south-1
    - pool id: ap-south-1_NmU5lr5kZ
    - App clients associated with this pool
        - name: Event-Management | id: 27j0jd3dj7nfum1jl61igng22a
    - Created User groups, 3 dev users to test the configurations & pre-auth token generation trigger pointed a [lambda function](#cognito-group-to-scopes-pre-token-generation-trigger-lambda)

#### DynamoDB
- Table name: **event-management-user-details**
    - partition_key: `user_id` (string)
    - Customised settings for table
        - Table class: `Standard`
        - Read/Write capacity mode: `On-Demand`
        - Max RRU/WRU: 5/5
        - Warm throughput: `default` value for read & write
    - Encryption at rest: `AWS-owned key`
    - Delete protection: `Enabled`
    - ARN: `arn:aws:dynamodb:ap-south-1:542850952858:table/event-management-user-details`
    - Tags:
        - project: event-management