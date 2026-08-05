# Implementation Plan — Feature Flag Service

Feed this into Cursor one step at a time, not all at once. Each step maps
to one commit. Review the diff, adjust anything you'd do differently, run
the relevant tests, then commit with a message reflecting what you actually
did.

Steps 3 and 6 are flagged as ones to decide yourself before prompting —
they're the design calls the write-up asks you to defend, and the two most
likely follow-up-interview questions ("why does isolation actually hold?"
and "why does the same user always get the same answer?").

---

## Step 0 — Project scaffold

**Prompt for Cursor:**
> Set up a new Spring Boot 3.3 Maven project in Java 17 called
> `feature-flags`, group `com.example`. Add dependencies:
> spring-boot-starter-web, spring-boot-starter-data-mongodb,
> spring-boot-starter-validation, spring-boot-starter-test. Add a
> `.gitignore` for a standard Maven/Java project that also excludes `.env`
> and `application-secrets.yml`. No business logic yet — just get it
> building and running.

**Acceptance criteria:** `mvn spring-boot:run` starts cleanly.

**Commit:** `chore: scaffold Spring Boot project`

---

## Step 1 — MongoDB Atlas connection, externalized

**Prompt for Cursor:**
> Configure `application.yml` so the MongoDB URI comes from a `MONGODB_URI`
> environment variable (fallback to a local instance for dev), never
> hardcoded. Add a README section on setting `MONGODB_URI` for Atlas,
> including a note that the database name must be part of the URI path
> (Atlas's default connection-string dialog omits it, which causes a
> "Database name must not be empty" startup failure if pasted as-is).

**Your manual check:** confirm no credentials made it into a tracked file.
Set `MONGODB_URI` locally (with a database name in the path) and confirm
the app connects to Atlas on startup.

**Commit:** `feat: externalize MongoDB Atlas connection config`

---

## Step 2 — Data model

**Prompt for Cursor:**
> Create a `FlagState` enum (ENABLED, DISABLED, ROLLOUT) and a `FeatureFlag`
> Mongo document with fields: id, projectId (indexed), key, description,
> state, rolloutPercentage (int), createdAt, updatedAt. Add a compound
> unique index on (projectId, key) so the same key can exist in different
> projects but not be duplicated within one project.

**Commit:** `feat: add FeatureFlag document model with per-project unique index`

---

## Step 3 — Decide and implement the multi-tenancy boundary

**Decide first:** the spec's eval endpoint is fixed as
`GET /eval?flag=X&user=Y` — no room for a project field in that URL without
deviating from the given shape. Where does the tenant identifier come from
instead? (This plan uses a required `X-Project-Id` header for eval, and the
URL path for CRUD — `/projects/{projectId}/flags` — but make this call
yourself and know why.)

Also decide: is the project identifier itself sufficient, or does it need
to be paired with some form of auth? (This plan treats it as a trusted
header and documents that as a known gap, rather than building real auth,
given the time box — but say so explicitly in your write-up either way.)

**Prompt for Cursor:**
> Add a `FeatureFlagRepository` with every query method scoped by
> projectId: findAllByProjectId, findByProjectIdAndKey,
> existsByProjectIdAndKey, deleteByProjectIdAndKey. There should be no
> method that looks up a flag by key alone.

**Commit:** `feat: add project-scoped repository layer`

---

## Step 4 — Stable evaluation logic

**Decide first:** how should a "default"/rollout state behave? A flag that's
on for 25% of users needs to give the *same* user the *same* answer every
time, not a coin flip per request.

**Prompt for Cursor:**
> Add a `RolloutBucket` utility that deterministically buckets a
> (flagKey, userId) pair into 0-99 using SHA-256 (not String.hashCode(),
> which clusters for short similar strings) so the same user always lands
> in the same bucket for a given flag. Add unit tests: determinism across
> repeated calls, values stay in [0,99], and a rough distribution check
> across a large sample so a badly skewed hash would get caught.

**Commit:** `feat: add stable hash-based rollout bucketing`

---

## Step 5 — Core service: CRUD + evaluate

**Prompt for Cursor:**
> Implement `FeatureFlagService`/`FeatureFlagServiceImpl` with create (409
> on duplicate key within a project, with a DuplicateKeyException fallback
> for races), listAll, get (404 if missing or belongs to another project),
> update, delete (verify ownership before deleting), and evaluate (looks up
> the flag scoped by project, then returns true/false/bucketed result based
> on ENABLED/DISABLED/ROLLOUT). Add Mockito unit tests covering: create
> success, duplicate rejection, same key allowed across different projects,
> get returns not-found for a flag under a different project, evaluate for
> each state, rollout evaluation stability, 0% always off, 100% always on,
> delete verifies ownership first.

**Commit:** `feat: implement CRUD and evaluation service logic`

---

## Step 6 — REST layer

**Prompt for Cursor:**
> Add CreateFlagRequest/UpdateFlagRequest/FlagResponse/EvalResponse DTOs
> with validation (key: 1-64 chars, letters/digits/hyphen/underscore;
> rolloutPercentage: 0-100). Add `FeatureFlagController` for
> `/projects/{projectId}/flags` (POST/GET/GET-one/PUT/DELETE) and a
> separate `EvaluationController` for `GET /eval?flag=X&user=Y` reading a
> required `X-Project-Id` header (400 if missing). Add a
> `@RestControllerAdvice` mapping FlagNotFoundException→404,
> DuplicateFlagKeyException→409, MissingProjectIdException→400, validation
> errors→400.

**Commit:** `feat: add REST endpoints for CRUD and evaluation`

---

## Step 7 — Integration tests, including the isolation test

This is the test the exercise explicitly calls out as required — don't let
this be an afterthought.

**Prompt for Cursor:**
> Add the embedded MongoDB test dependency
> (de.flapdoodle.embed:de.flapdoodle.embed.mongo.spring3x) scoped to test.
> Write a @SpringBootTest(webEnvironment=RANDOM_PORT) integration test
> covering: create→get round trip, duplicate key→409, update reflected in
> evaluation, delete removes the flag, evaluation respects
> enabled/disabled/is stable across repeated calls, eval without the
> project header→400, unknown flag→404. Add one test specifically named
> around cross-tenant isolation: create the same flag key in two different
> projects with opposite states, and assert each project's evaluation and
> listing only ever sees its own flag — including a third, unrelated
> project seeing neither.

**Acceptance criteria:** `mvn test` passes fully offline, no real Atlas
connection needed.

**Commit:** `test: add integration tests including cross-tenant isolation check`

---

## Step 8 — Polish pass (do this one yourself, lightly assisted)

- Re-read every class. Anywhere Cursor's naming, error messages, or
  structure doesn't match how you'd explain it live, change it.
- Specifically re-check the isolation logic by hand — trace through what
  happens if someone requests `/projects/proj-b/flags/checkout` for a key
  that only exists under `proj-a`. Confirm it 404s, not 200s with someone
  else's data.
- Confirm `.gitignore` actually keeps secrets out — check `git status`
  after setting `MONGODB_URI` locally.
- Run `mvn test` one more time end to end.

**Commit:** `refactor: polish and review pass`

---

## Step 9 — README and write-up

**Prompt for Cursor (README only):**
> Write a README covering: what the service does, the multi-tenancy model
> and why the project identifier is a header on /eval but a path segment on
> CRUD, the API endpoints, the evaluation logic (including the rollout
> bucketing approach), a client code snippet showing how a consuming app
> would call /eval, and how to run/test locally.

**Don't let Cursor write `WRITEUP.md` for you.** Write it last, in your own
words, once the whole thing is fresh — it's the one part that's actually
being read as evidence of your judgment, not your code quality.

**Commit:** `docs: add README and write-up`

---

## Before you submit

- [ ] `mvn clean test` passes with no external network/Atlas dependency.
- [ ] `git log --oneline` shows the incremental story above, not one squashed commit.
- [ ] The cross-tenant isolation test exists and actually asserts something meaningful (not just "no exception thrown").
- [ ] No secrets anywhere in the repo history.
- [ ] Atlas password rotated if it was ever pasted anywhere outside your own machine.
- [ ] `WRITEUP.md` filled in with real trade-offs, referencing what actually happened.
- [ ] You can explain, live, why one tenant can't read another's flags — not just that a test passes, but the actual mechanism (query scoping + unique index).
