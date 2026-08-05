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

### Example: create a flag

```bash
curl -X POST http://localhost:8080/projects/my-app/flags \
  -H "Content-Type: application/json" \
  -d '{
    "key": "new-checkout",
    "description": "Redesigned checkout flow",
    "state": "ROLLOUT",
    "rolloutPercentage": 25
  }'
```

### Example: evaluate from a consuming app

```java
// Java client snippet
String projectId = "my-app";
String flagKey = "new-checkout";
String userId = currentUser.getId();

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("http://localhost:8080/eval?flag=" + flagKey + "&user=" + userId))
    .header("X-Project-Id", projectId)
    .GET()
    .build();

HttpResponse<String> response = HttpClient.newHttpClient()
    .send(request, HttpResponse.BodyHandlers.ofString());

// Response: {"flag":"new-checkout","user":"user-123","enabled":true}
boolean featureEnabled = objectMapper.readTree(response.body()).get("enabled").asBoolean();
```

```bash
curl "http://localhost:8080/eval?flag=new-checkout&user=user-123" \
  -H "X-Project-Id: my-app"
```

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
