# Feature Flag Service

A multi-tenant feature flag backend built with Spring Boot 3.3 and MongoDB. Other applications call this service to decide whether a feature is enabled for a given user, with strict isolation between projects (tenants).

## Multi-tenancy model

Every flag belongs to exactly one **project**. The project identifier scopes all data access:

- **CRUD** endpoints use `/projects/{projectId}/flags` — the tenant is explicit in the URL path.
- **Evaluation** uses the fixed spec shape `GET /eval?flag=X&user=Y`, so the tenant is supplied via the required **`X-Project-Id`** request header instead.

There is no authentication in this MVP: the project identifier is treated as a trusted boundary. In production you would pair it with API keys, JWT claims, or mTLS so callers cannot impersonate another tenant.

Isolation is enforced at the repository layer — every query includes `projectId`, and a compound unique index on `(projectId, key)` prevents duplicate keys within a project while allowing the same key name across projects.

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/projects/{projectId}/flags` | Create a flag |
| `GET` | `/projects/{projectId}/flags` | List all flags for a project |
| `GET` | `/projects/{projectId}/flags/{key}` | Get one flag |
| `PUT` | `/projects/{projectId}/flags/{key}` | Update a flag |
| `DELETE` | `/projects/{projectId}/flags/{key}` | Delete a flag |
| `GET` | `/eval?flag={key}&user={userId}` | Evaluate a flag (requires `X-Project-Id` header) |

### Flag states

| State | Evaluation behavior |
|-------|---------------------|
| `ENABLED` | Always on |
| `DISABLED` | Always off |
| `ROLLOUT` | On for users whose stable bucket is below `rolloutPercentage` |

## Rollout bucketing

For `ROLLOUT` flags, the service hashes `flagKey + ":" + userId` with SHA-256 and maps the result to a bucket in `[0, 99]`. The same user always lands in the same bucket for a given flag, so evaluation is stable across requests. A user is enabled when `bucket < rolloutPercentage` (e.g. 25% rollout enables buckets 0–24).

## Prerequisites

- Java 17+
- Maven 3.9+
- MongoDB (local or Atlas)

## MongoDB configuration

Set the connection URI via environment variable:

```bash
export MONGODB_URI="mongodb://localhost:27017/featureflags"
```

For **MongoDB Atlas**, paste your connection string into `MONGODB_URI`. Atlas's default dialog often omits the database name — you **must** include it in the URI path or startup fails with *"Database name must not be empty"*:

```bash
# Correct — database name in the path
export MONGODB_URI="mongodb+srv://user:pass@cluster.mongodb.net/featureflags?retryWrites=true&w=majority"
```

Never commit credentials. Use `.env` locally (gitignored) or your platform's secret store.

## Run locally

```bash
cd Feature-Flag-Service
mvn spring-boot:run
```

The app starts on port 8080. Without `MONGODB_URI`, it defaults to `mongodb://localhost:27017/featureflags`.

## Run tests

Tests use embedded MongoDB and run fully offline — no Atlas connection required:

```bash
mvn clean test
```

## Project structure

```
src/main/java/com/example/featureflags/
├── controller/     REST endpoints (CRUD + evaluation)
├── service/        Business logic and evaluation
├── repository/     Project-scoped MongoDB queries
├── model/          FeatureFlag document and FlagState enum
├── dto/            Request/response objects
├── util/           RolloutBucket (SHA-256 bucketing)
├── exception/      Domain exceptions
└── advice/         Global exception → HTTP status mapping
```

# Testing Guide — Feature Flag Service

This walks through everything you should check before submitting: the
automated test suite, then a manual curl walkthrough of every behavior the
exercise asks for (CRUD, evaluation, tenant isolation, rollout stability,
error handling).

## 1. Run the automated tests first

```powershell
cd "c:\Users\mgmst\Desktop\MVP Start\Feature-Flag-Service\Feature-Flag-Service"
mvn clean test
```

This needs no MongoDB Atlas connection — the integration tests spin up an
embedded, in-memory MongoDB automatically. You should see all tests pass,
including `crossTenantIsolation_sameKeyDifferentProjectsDoNotLeak`. If
anything fails here, fix it before moving on to manual testing.

## 2. Start the app

Set your Atlas connection string (make sure the database name is in the
path, e.g. `/featureflags`, or the app will fail to start):

```powershell
$env:MONGODB_URI = "mongodb+srv://user:pass@cluster.mongodb.net/featureflags?retryWrites=true&w=majority"
mvn spring-boot:run
```

Or skip Atlas entirely and point at a local MongoDB if you have one
running on the default port — the app falls back to
`mongodb://localhost:27017/featureflags` if `MONGODB_URI` isn't set.

Leave this running and open a second terminal for the curl commands below.

## 3. Basic CRUD walkthrough

**Create a flag for project `acme`:**

```powershell
curl -X POST http://localhost:8080/projects/acme/flags `
  -H "Content-Type: application/json" `
  -d '{\"key\":\"new-checkout\",\"description\":\"New flow\",\"state\":\"ENABLED\",\"rolloutPercentage\":0}'
```

Expect: `201 Created`, JSON body with `"key":"new-checkout"`.

**Read it back:**

```powershell
curl http://localhost:8080/projects/acme/flags/new-checkout
```

Expect: `200 OK`, same flag details.

**List all flags for the project:**

```powershell
curl http://localhost:8080/projects/acme/flags
```

Expect: a JSON array containing the one flag you just created.

**Update it:**

```powershell
curl -X PUT http://localhost:8080/projects/acme/flags/new-checkout `
  -H "Content-Type: application/json" `
  -d '{\"description\":\"Updated\",\"state\":\"DISABLED\",\"rolloutPercentage\":0}'
```

Expect: `200 OK`, `"state":"DISABLED"` in the response.

**Delete it:**

```powershell
curl -X DELETE http://localhost:8080/projects/acme/flags/new-checkout
```

Expect: `204 No Content`. Then confirm it's gone:

```powershell
curl http://localhost:8080/projects/acme/flags/new-checkout
```

Expect: `404 Not Found`.

## 4. Evaluation

Recreate the flag first (delete above removed it):

```powershell
curl -X POST http://localhost:8080/projects/acme/flags `
  -H "Content-Type: application/json" `
  -d '{\"key\":\"new-checkout\",\"description\":\"New flow\",\"state\":\"ENABLED\",\"rolloutPercentage\":0}'
```

**Evaluate it for a user:**

```powershell
curl -H "X-Project-Id: acme" "http://localhost:8080/eval?flag=new-checkout&user=user-123"
```

Expect: `{"flag":"new-checkout","user":"user-123","enabled":true}` since the
flag is `ENABLED`.

**Switch it to DISABLED and re-check:**

```powershell
curl -X PUT http://localhost:8080/projects/acme/flags/new-checkout `
  -H "Content-Type: application/json" `
  -d '{\"description\":\"New flow\",\"state\":\"DISABLED\",\"rolloutPercentage\":0}'

curl -H "X-Project-Id: acme" "http://localhost:8080/eval?flag=new-checkout&user=user-123"
```

Expect: `"enabled":false` now.

## 5. Rollout stability (the "same user, same answer" requirement)

Set the flag to a 50% rollout:

```powershell
curl -X PUT http://localhost:8080/projects/acme/flags/new-checkout `
  -H "Content-Type: application/json" `
  -d '{\"description\":\"New flow\",\"state\":\"ROLLOUT\",\"rolloutPercentage\":50}'
```

Call evaluation for the **same user** several times in a row:

```powershell
curl -H "X-Project-Id: acme" "http://localhost:8080/eval?flag=new-checkout&user=user-123"
curl -H "X-Project-Id: acme" "http://localhost:8080/eval?flag=new-checkout&user=user-123"
curl -H "X-Project-Id: acme" "http://localhost:8080/eval?flag=new-checkout&user=user-123"
```

Expect: `enabled` is identical every time — this is the core stability
guarantee.

Now try a handful of **different** users and confirm you get a mix of true
and false (not all one or the other) at roughly a 50/50 split:

```powershell
curl -H "X-Project-Id: acme" "http://localhost:8080/eval?flag=new-checkout&user=user-1"
curl -H "X-Project-Id: acme" "http://localhost:8080/eval?flag=new-checkout&user=user-2"
curl -H "X-Project-Id: acme" "http://localhost:8080/eval?flag=new-checkout&user=user-3"
curl -H "X-Project-Id: acme" "http://localhost:8080/eval?flag=new-checkout&user=user-4"
```

## 6. Multi-tenant isolation (the most important check)

Create the **same flag key** under a second, different project:

```powershell
curl -X POST http://localhost:8080/projects/globex/flags `
  -H "Content-Type: application/json" `
  -d '{\"key\":\"new-checkout\",\"description\":\"Different rollout\",\"state\":\"DISABLED\",\"rolloutPercentage\":0}'
```

Confirm each project evaluates independently:

```powershell
curl -H "X-Project-Id: acme"   "http://localhost:8080/eval?flag=new-checkout&user=user-123"
curl -H "X-Project-Id: globex" "http://localhost:8080/eval?flag=new-checkout&user=user-123"
```

Expect: different `enabled` values, since `acme`'s flag is `ROLLOUT` and
`globex`'s is `DISABLED` — proves the two don't share state.

Confirm project isolation on reads too — a project should never see another
project's flags:

```powershell
curl http://localhost:8080/projects/acme/flags
curl http://localhost:8080/projects/globex/flags
```

Expect: each list shows only its own flag, and a made-up project sees
nothing:

```powershell
curl http://localhost:8080/projects/does-not-exist/flags
```

Expect: `200 OK` with an empty array `[]`.

## 7. Error handling

**Duplicate key within the same project → 409:**

```powershell
curl -X POST http://localhost:8080/projects/acme/flags `
  -H "Content-Type: application/json" `
  -d '{\"key\":\"new-checkout\",\"description\":\"dup\",\"state\":\"ENABLED\",\"rolloutPercentage\":0}'
```

Expect: `409 Conflict`.

**Missing project header on eval → 400:**

```powershell
curl "http://localhost:8080/eval?flag=new-checkout&user=user-123"
```

Expect: `400 Bad Request` (no `X-Project-Id` header sent).

**Unknown flag → 404:**

```powershell
curl -H "X-Project-Id: acme" "http://localhost:8080/eval?flag=does-not-exist&user=user-123"
```

Expect: `404 Not Found`.

**Invalid flag key format → 400:**

```powershell
curl -X POST http://localhost:8080/projects/acme/flags `
  -H "Content-Type: application/json" `
  -d '{\"key\":\"has a space\",\"description\":\"bad\",\"state\":\"ENABLED\",\"rolloutPercentage\":0}'
```

Expect: `400 Bad Request` (key fails the allowed-character pattern).
