# Transporter Assignment on Lanes

A Spring Boot REST API that assigns transporters to freight lanes at the
lowest possible total cost, subject to a customer-defined limit on the
number of distinct transporters.

-   **Live API:** https://assign-transporters-on-lanes.onrender.com
-   **Swagger UI:** https://assign-transporters-on-lanes.onrender.com/swagger-ui/index.html
-   **Demo video (Loom):** `ADD_LOOM_LINK`

> A free hosting tier may
> put the service to sleep when idle, so the first request after a pause
> can take 30--60 seconds.

## Contents

-   [Features](#features)
-   [Problem statement](#problem-statement)
-   [Tech stack](#tech-stack)
-   [Architecture](#architecture)
-   [Assumptions and business rules](#assumptions-and-business-rules)
-   [Optimization algorithm](#optimization-algorithm)
-   [Database schema](#database-schema)
-   [Run locally](#run-locally)
-   [API usage](#api-usage)
-   [Error responses](#error-responses)
-   [Testing](#testing)
-   [Deployment configuration](#deployment-configuration)
-   [Limitations and future
    improvements](#limitations-and-future-improvements)

## Features

-   Save a complete dataset of lanes, transporters, and quotes.
-   Find a minimum-cost assignment within a maximum transporter count.
-   Treat missing quotes as ineligibility, not as zero-cost quotes.
-   Validate duplicate IDs, duplicate quotes, unknown lane references,
    and negative prices.
-   Persist each successful submission as a new immutable dataset batch.
-   Use PostgreSQL with Flyway-managed migrations.
-   Expose interactive API documentation through Swagger UI.
-   Test the optimizer independently of Spring and the database.

## Problem statement

A customer has several freight lanes (origin to destination). Different
transporters quote different prices for different lanes. The customer
limits how many distinct transporters may be used. The API assigns
exactly one eligible transporter to every lane while minimizing the sum
of the selected quotes.

  -----------------------------------------------------------------------------------
Method                  Endpoint                            Purpose
  ----------------------- ----------------------------------- -----------------------
`POST`                  `/api/v1/transporters/input`        Validate and save a
complete dataset

`POST`                  `/api/v1/transporters/assignment`   Calculate the optimal
assignment for the
latest dataset
  -----------------------------------------------------------------------------------

## Tech stack

| Area | Technology |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 4.1.1 (Spring Web MVC, Spring Data JPA, Bean Validation) |
| Database | PostgreSQL 16 |
| Migrations | Flyway (`src/main/resources/db/migration`) |
| API docs | springdoc-openapi 3.0.3 (Swagger UI) |
| Build | Maven (wrapper included) |
| Tests | JUnit 5, Mockito |
| Packaging | Docker (multi-stage build) |

Packaging                           Docker multi-stage build
-----------------------------------------------------------------------

Dependency versions are managed in `pom.xml`; keep this table aligned
with the checked-in build file.

## Architecture

``` mermaid
flowchart TD
    A[HTTP client / Swagger UI] --> B[REST controller]
    B --> C[InputService]
    C --> D[Input validation]
    C --> E[Spring Data JPA repositories]
    E --> F[(PostgreSQL)]
    B --> G[AssignmentService]
    G --> E
    G --> H[Assignment optimizer]
    H --> I[Optimal assignment]
    I --> G
    G --> B
```

-   **Controller:** HTTP request and response handling.
-   **Service:** validation, persistence orchestration, loading the
    active dataset, and mapping results.
-   **Optimization:** a pure Java algorithm with no Spring, JPA, or
    database dependencies.
-   **Repository:** Spring Data JPA access to persisted entities.
-   **Entity:** database-mapped objects.
-   **DTO:** request and response contracts.
-   **Exception handling:** consistent API error responses.

The optimizer receives plain data structures and returns an assignment
result. It can be unit-tested independently and replaced without
changing persistence or the HTTP layer.

## Assumptions and business rules

1.  **Cost is the primary objective.** Total cost is the sum of the
    selected quotes.
2.  **`maxTransporters` is an upper bound.** A solution may use fewer
    than the maximum. A cheaper solution using fewer transporters wins
    over a more expensive solution using the full limit.
3.  **Exactly one transporter is assigned per lane.** This avoids
    assigning a lane multiple times and double-counting its cost.
4.  **A quote defines eligibility.** A transporter can serve a lane only
    if it submitted a quote for that lane. A missing quote is not a free
    quote.
5.  **Tie-breaking is deterministic.** Among equal-cost solutions, the
    optimizer prefers (a) more distinct transporters, up to the limit,
    then (b) the lexicographically smallest sorted transporter-ID list.
6.  **Lane IDs are supplied by the customer.** Quotes refer to IDs from
    the `lanes` array. Unknown lane references are rejected. Different
    IDs represent different lanes even if their origins and destinations
    are identical.
7.  **Money uses decimal arithmetic.** Java uses `BigDecimal`;
    PostgreSQL uses `NUMERIC(15,2)`. Zero is valid; negative quotes are
    rejected.
8.  **Duplicate data is rejected.** Duplicate lane IDs, transporter IDs,
    or duplicate quotes for the same transporter and lane return a
    client error.
9.  **Datasets are immutable batches.** Each successful submission
    creates a new batch and becomes active. A failed submission must not
    replace the active dataset. Partial updates are not supported.

## Optimization algorithm

### Approach

Once a subset of allowed transporters is fixed, lanes are independent
because there are no transporter capacity constraints. Each lane can use
its cheapest eligible quote among that subset. The optimizer enumerates
transporter subsets and evaluates each one.

1.  Sort lanes and transporters by ID so results do not depend on input
    order.
2.  Build a lane-by-transporter quote matrix; a missing quote represents
    ineligibility.
3.  Enumerate transporter subsets of sizes `1` through `K`, where `K` is
    `maxTransporters`, bounded by the number of available transporters.
4.  For each subset, choose the cheapest eligible quote for every lane.
    Discard the subset if any lane cannot be covered.
5.  Track total cost and apply the tie-breaking rules. For tied cheapest
    quotes, matching determines whether more distinct transporters can
    be used without increasing cost.
6.  If no subset covers every lane, return `NO_FEASIBLE_ASSIGNMENT`.

### Why it works

Every permitted transporter subset is considered. For a fixed subset,
each lane can independently select its cheapest eligible quote because
there are no capacity or interaction constraints between lanes.
Evaluating the subsets and selecting the best valid result therefore
finds the minimum-cost assignment under the stated rules.

### Complexity

Let `T` be the number of transporters, `L` the number of lanes, and `K`
the maximum permitted transporter count. The optimizer examines up to:

`sum(C(T, i), i = 1..K)`

subsets. Each subset requires roughly `O(L × K)` quote evaluations, plus
matching work for tie-breaking. Worst-case runtime is exponential in
`T`, so this approach is intended for small-to-medium datasets rather
than thousands of transporters.

### Worked example

Suppose there are three lanes:

-   T1 quotes only lane 1 for 10.
-   T2 quotes only lane 2 for 11.
-   T3 quotes only lane 3 for 10.
-   T4 quotes all three lanes for 15, 15, and 12.

    Maximum transporters Assignment                                    Total
  ---------------------- ------------------------------------------- -------
                       1 T4 covers all lanes                              42
                       2 T1 covers lane 1; T4 covers lanes 2 and 3        37
                       3 T1, T2, and T3 cover one lane each               31

## Database schema

Flyway manages the schema through SQL migrations in
`src/main/resources/db/migration`. Hibernate validates the schema at
startup with `ddl-auto=validate`; it does not create or update tables.

  ---------------------------------------------------------------------------------------
Table                   Purpose                 Important constraints
  ----------------------- ----------------------- ---------------------------------------
`input_batches`         One row per             Creation timestamp
successfully submitted  
dataset

`lanes`                 Lanes in a dataset      Unique `(batch_id, external_lane_id)`

`transporters`          Transporters in a       Unique
dataset                 `(batch_id, external_transporter_id)`

`lane_quotes`           Price a transporter     Unique `(transporter_id, lane_id)`;
quotes for a lane       quote must be non-negative
  ---------------------------------------------------------------------------------------

The `external_lane_id` and `external_transporter_id` columns store IDs
supplied by the client. Database primary keys are separate, allowing the
same client IDs to be reused in later batches.

## Run locally

### Prerequisites

-   Java 21
-   Docker Desktop with the Docker engine running
-   Git, if cloning the repository

### 1. Start PostgreSQL

From the project root:

``` bash
docker compose up -d
```

The Compose configuration should start PostgreSQL 16 with the local
database `freightfox_assignment` and user `freightfox`.

### 2. Run the application

**Windows PowerShell:**

``` powershell
.\mvnw.cmd spring-boot:run
```

**macOS / Linux / Git Bash:**

``` bash
./mvnw spring-boot:run
```

Flyway runs automatically at startup and applies pending migrations.

### 3. Open Swagger UI

-   Swagger UI: `http://localhost:8080/swagger-ui/index.html`
-   OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### Environment variables

  ----------------------------------------------------------------------------------------------------------
Variable                Local default                                              Purpose
  ----------------------- ---------------------------------------------------------- -----------------------
`DB_URL`                `jdbc:postgresql://localhost:5432/freightfox_assignment`   JDBC connection URL

`DB_USERNAME`           `freightfox`                                               Database username

`DB_PASSWORD`           `local_dev_password`                                       Local development
password only

`PORT`                  `8080`                                                     HTTP port; configure
the app to use
`${PORT:8080}`
  ----------------------------------------------------------------------------------------------------------

**Security:** These database defaults are for local development only.
Use environment variables for deployed services. Do not commit
production credentials or place production connection strings in this
README.

## API usage

### 1. Submit a dataset

**Request:** `POST /api/v1/transporters/input`

``` json
{
  "lanes": [
    { "id": 1, "origin": "Mumbai", "destination": "Delhi" },
    { "id": 2, "origin": "Delhi", "destination": "Bangalore" }
  ],
  "transporters": [
    {
      "id": 1,
      "name": "Transporter T1",
      "laneQuotes": [
        { "laneId": 1, "quote": 5000 },
        { "laneId": 2, "quote": 6000 }
      ]
    },
    {
      "id": 2,
      "name": "Transporter T2",
      "laneQuotes": [
        { "laneId": 1, "quote": 5500 }
      ]
    }
  ]
}
```

**Successful response --- `201 Created`:**

``` json
{
  "status": "success",
  "message": "Input data saved successfully."
}
```

Each successful submission becomes the latest dataset used by the
assignment endpoint.

### 2. Calculate an assignment

**Request:** `POST /api/v1/transporters/assignment`

``` json
{
  "maxTransporters": 2
}
```

The response includes the total cost, one transporter assignment per
lane, and the sorted list of selected transporter IDs. The exact
response fields are defined by the application's response DTO.

### Sample dataset result

For the supplied nine-lane, seven-transporter CSV dataset with
`maxTransporters = 3`, the expected result documented for this project
is a total cost of **134,876**, using transporters **1, 4, and 6**.

``` json
{
  "status": "success",
  "totalCost": 134876,
  "assignments": [
    { "laneId": 1, "transporterId": 4 },
    { "laneId": 2, "transporterId": 1 },
    { "laneId": 3, "transporterId": 4 },
    { "laneId": 4, "transporterId": 4 },
    { "laneId": 5, "transporterId": 4 },
    { "laneId": 6, "transporterId": 1 },
    { "laneId": 7, "transporterId": 1 },
    { "laneId": 8, "transporterId": 6 },
    { "laneId": 9, "transporterId": 6 }
  ],
  "selectedTransporters": [1, 4, 6]
}
```

The independently cross-checked optima documented during development are
201,259 for `maxTransporters = 1` and 148,402 for `maxTransporters = 2`.
Re-run the supplied dataset against the final build before submission to
confirm these values.

## Error responses

Errors use a consistent response shape. For example:

``` json
{
  "timestamp": "2026-10-15T08:30:00Z",
  "status": 400,
  "code": "INVALID_INPUT",
  "message": "Input data failed validation",
  "details": [
    {
      "field": "transporters[0].laneQuotes[1].laneId",
      "message": "Transporter 1 quoted lane 1 more than once"
    }
  ]
}
```

  -------------------------------------------------------------------------------
                   HTTP status Error code                 Meaning
  ---------------------------- -------------------------- -----------------------
                         `400` `VALIDATION_FAILED`        A field fails
                                                          validation, such as a
                                                          negative quote or
                                                          `maxTransporters < 1`

                         `400` `INVALID_INPUT`            Cross-entity rule
                                                          fails, such as
                                                          duplicate IDs, an
                                                          unknown lane reference,
                                                          or a duplicate quote

                         `400` `MALFORMED_REQUEST`        Missing body or invalid
                                                          JSON

                         `404` `DATASET_NOT_FOUND`        Assignment requested
                                                          before any dataset has
                                                          been saved

                         `422` `NO_FEASIBLE_ASSIGNMENT`   No allowed transporter
                                                          subset can cover every
                                                          lane

                         `500` `INTERNAL_ERROR`           Unexpected server
                                                          error; internal details
                                                          are not returned
  -------------------------------------------------------------------------------

Confirm that the error codes and response fields in this table match the
current `GlobalExceptionHandler` before submitting.

## Testing

Run the test suite from the project root.

**Windows PowerShell:**

``` powershell
.\mvnw.cmd clean test
```

**macOS / Linux / Git Bash:**

``` bash
./mvnw clean test
```

### Unit tests

The three unit-test classes cover the optimizer, input service, and
assignment service:

  -------------------------------------------------------------------------
Test class                            Coverage
  ------------------------------------- -----------------------------------
`ExhaustiveAssignmentOptimizerTest`   Minimum cost, transporter limit,
missing quotes, infeasibility,
tie-breaking, zero and large
values, input-order independence,
supplied CSV, and randomized
comparisons against an independent
brute-force implementation

`InputServiceTest`                    Input validation, no writes for
invalid data, trimming, and correct
relationships between lanes,
transporters, and quotes

`AssignmentServiceTest`               Latest batch lookup, optimizer
input mapping, response mapping,
cost formatting, and exception
propagation
  -------------------------------------------------------------------------



## Deployment configuration

The deployed application needs a PostgreSQL database and these
environment variables:

-   `DB_URL` --- JDBC URL beginning with `jdbc:postgresql://`; include
    `sslmode=require` when required by the database provider.
-   `DB_USERNAME` --- database user.
-   `DB_PASSWORD` --- database password.
-   `PORT` --- HTTP port supplied by the hosting platform.

Do not commit credentials to source control. Configure secrets in the
hosting provider's environment-variable settings. After deployment,
verify both endpoints against the deployed database and open the
deployed Swagger UI.

### Docker

Build the image from the project root:

``` bash
docker build -t transporter-assignment .
```

Run it locally against a PostgreSQL instance reachable from the
container:

``` bash
docker run --rm -p 8080:8080 \
  -e DB_URL="jdbc:postgresql://host.docker.internal:5432/freightfox_assignment" \
  -e DB_USERNAME="freightfox" \
  -e DB_PASSWORD="local_dev_password" \
  transporter-assignment
```

The command above uses local-development credentials only. For a hosted
database, pass the provider's JDBC URL and credentials through
environment variables.

## Limitations and future improvements

-   **Exhaustive search:** the number of transporter subsets grows
    exponentially. Branch-and-bound or an integer linear programming
    solver could support larger datasets behind the same optimizer
    interface.
-   **Complete submissions only:** individual lanes, transporters, or
    quotes cannot be appended or edited independently.
-   **No authentication:** API authentication and authorization are out
    of scope.
-   **No capacity constraints:** transporters are assumed to be able to
    serve every lane they quote.
-   **Single transporter per lane:** splitting a lane across multiple
    transporters is not supported.
-   **Batch retention:** historical batches are retained indefinitely; a
    cleanup or retention policy could be added.
-   **Possible extensions:** a Postman collection, load/performance
    tests, and more detailed operational monitoring.
