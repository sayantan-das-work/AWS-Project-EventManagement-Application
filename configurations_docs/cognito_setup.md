### Region
**ap-south-1** which is Mumbai

### User pool details
**pool id**: ap-south-1_NmU5lr5kZ

**pool name**: User pool - ahhif

**ARN**: arn:aws:cognito-idp:ap-south-1:542850952858:userpool/ap-south-1_NmU5lr5kZ

### App client details
**name**: Event-Management

**id**: 27j0jd3dj7nfum1jl61igng22a

**OAuth grant type**: Authorization code grant

### Resource server details
**name**: Event-Management-App

**identifier**: event-management-application

### Custom scopes
- **users::create** : create new user account with user details and sign in information.
- **users::modify** : modify the user's details, sign in password or email
- **users::remove** : delete user account
- **events::read**
    - **events::read::published** - browse events published on the platform
    - **events::read::awaiting_approval** - browse events waiting for approval to be published
    - **events::read::owned** - browse events that belongs to the owners of the request.
    - **events::read::all** - browse all events existing on the platform.
- **events::publish** : request to host events on the platform for other users to checkout.
- **events::approve** : approve an event which has been requested for published.
- **events::reject** : reject an event which has been requested for published.
- **tickets::book** : pay  book tickets on the platform for any event.
- **tickets::cancel** : cancel ticket booked for an event.
- **technical::sre** : for use by the technical team only for varous technical/functional tasks related to platform.


### User group & scope relations
- **public**
    - Represent users who visit the platform, sign themselves up to browse the events and book tickets to those events.They can manage their account.
    - `users::create, users::modify, users::remove, events::read::published, tickets::book, tickets::cancel`
- **event_owners**
    - Represent users who are either organisation/individuals, who want to publish some events on the platform for users to visit.
    - `events::read::owned,  events::publish`
- **event_approvers**
     - Represents user who work for the platform and are authorised to review the newly published events before they are published for users to browse and book tickets.
     - `events::read::awaiting_approval, events::approve, events::reject`
- **platform_admins**
    - Represents users who work for the platform and are allowed to take any action on the platform.
    - `*` *meaning all access*
- **technical**
    -  Represents the technical team who monitor the platform for errors and keep the systems up & running.
    - `technical::sre`

### JSON mappings
```json
{
    "public": [
        "event-management-application/users::create",
        "event-management-application/users::modify",
        "event-management-application/users::remove",
        "event-management-application/events::read::published",
        "event-management-application/tickets::book",
        "event-management-application/tickets::cancel"
    ],
    "event_owners": [
        "event-management-application/events::read::owned",
        "event-management-application/events::publish"
    ],
    "event_approvers": [
        "event-management-application/events::read::awaiting_approval",
        "event-management-application/events::approve",
        "event-management-application/events::reject"
    ],
    "platform_admins": [
        "event-management-application/users::create",
        "event-management-application/users::modify",
        "event-management-application/users::remove",
        "event-management-application/events::read::all",
        "event-management-application/events::publish",
        "event-management-application/events::approve",
        "event-management-application/events::reject",
        "event-management-application/tickets::book",
        "event-management-application/tickets::cancel"
    ],
    "technical": [
        "event-management-application/technical::sre"
    ]
}
```