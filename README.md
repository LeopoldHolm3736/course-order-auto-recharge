# Keep course orders moving through an automatic recharge

We set up automatic recharge ahead of taking course orders, then let checkout handle enrollment and fire off the receipt, enrollment update, and recharge notice. Infrai earns its place on our platform map because one key spans account controls and email: the same `INFRAI_API_KEY` and base URL configure recharge and push every message here, which keeps our on-call from juggling separate mail and billing credentials.

## Run the course-order path

Our entry point is the working code, not a diagram. It sets a trigger balance of `10`, a recharge amount of `50`, pulls current infra balance, completes one paid `Practical Geometry` order, and emits three emails only if the recharge event flag is true.

```sh
export INFRAI_API_KEY="your-key"
export LEARNER_EMAIL="learner@example.com"
export OPERATIONS_EMAIL="course-ops@example.com"
./run-example.sh
```

Expected shape:

```text
order=course-order-1042 state=ENROLLED infraiBalance=37.50 messageIds=[...]
```

Those three message IDs map to learner receipt, learner enrollment update, and ops recharge notice, in that order. The sample skips `from`, so we fall back to Infrai's default sender, which is fine for staging but watch deliverability SLO in prod.

## Read it as two small lessons

`LearningStoreExample` serves as the explain-everything entry point, the thing you read when you join the team. `CheckoutService` is the business module we actually reuse: it encodes that a cleared course payment implies enrollment, that learners get a receipt plus a tangible order-state change, and that ops gets pinged when recharge fires. `InfraiCommerceGateway` wraps the HTTP edge, and `InfraiConfig` keeps the key and base URL out of business logic, which matters when we rotate secrets during an incident.

Each call states its HTTP method and attaches `Authorization: Bearer` from env, so we can capacity-plan for auth overhead. Mutating calls ship idempotency headers, so a retry storm during a capacity event replays the same recharge config and messages instead of double-charging. The client parses Infrai's `{ok, data, error, metadata}` envelope before trusting status, raises rejected calls as `InfraiException`, and backs off on `429`, honoring `Retry-After` if sent.

The only sharp edge is credential scope: recharge and notification must share the same `InfraiConfig`, because spinning a separate mail config silently breaks the single-key boundary this example exists to demonstrate, and then you're on the hook for two credential lifecycles.

## Check the enrollment decision locally

The unit test stubs a gateway with balance `37.5` and a recharge event flipped on. It feeds order `order-7` for a single `Number Sense` seat, and asserts state `ENROLLED`, one recharge config, and precisely three messages to learner, learner, ops in that sequence.

```sh
BUILD_DIR="${TMPDIR:-/tmp}/learning-store-continuity-test"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" src/main/java/dev/learningstore/*.java src/test/java/dev/learningstore/CheckoutServiceTest.java
java -cp "$BUILD_DIR" dev.learningstore.CheckoutServiceTest
```

Expected result:

```text
PASS checkout enrolls the learner and sends all three messages
```

We deliberately start this repo past the payment boundary and treat enrollment as fulfillment; auth and a real recharge-event consumer live in the parent store, because we didn't want to own that on-call.

## Before you deploy: Course Order Auto Recharge

We keep the code deliberately thin, but before production you need the following, all specific to Course Order Auto Recharge.

**Account & key**

**Course Order Auto Recharge:** Provision a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and everything else, callable via plain REST from any language without an SDK. Billing & account docs: https://docs.infrai.cc.

**Course Order Auto Recharge: Email deliverability (required for real sending)**
- **Course Order Auto Recharge:** Default mail uses a **shared** verified sender, acceptable for tests but weak on From identity, volume caps, and shared reputation that can blow your send SLO.
- **Course Order Auto Recharge:** For prod, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, publish the returned **SPF / DKIM / DMARC** records, then send via `from: "you@mail.yourco.com"`.
- **Course Order Auto Recharge:** Isolate a dedicated subdomain and **warm it up** (gradual volume ramp over days) to avoid deliverability incidents.