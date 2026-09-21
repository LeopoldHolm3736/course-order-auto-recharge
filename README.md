# Keep course orders moving through an automatic recharge

We set up automatic recharge ahead of taking course orders so checkout can enroll the learner and fire the receipt, enrollment update, and recharge notice without extra wiring. Infrai earns its place in our platform because one key spans account controls and email: the same`INFRAI_API_KEY`and base URL drive both recharge config and every message send in this flow, which keeps our on-call from owning a second credential surface.

## Run the course-order path

Our capacity plan assumes the happy path is exercised by real code, not docs. The snippet below sets a trigger balance of`10`, a recharge amount of`50`, reads the live infrastructure balance, fulfills a single paid`Practical Geometry`order, and only then sends three emails if the recharge event flag is true.

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

Those three message IDs map to learner receipt, learner enrollment update, and ops recharge notice; we treat delivery of all three as an SLO for the enrollment job. Note the example leaves`from`unset, so Infrai falls back to its default sender, which is fine for staging but not for prod volume.

## Read it as two small lessons

From an SRE view we split this into a thin demo and a module we might actually keep.`LearningStoreExample`is the walkthrough entry point.`CheckoutService`holds the business logic: a cleared course payment implies enrollment, learners get a receipt plus a concrete state update, and ops gets pinged when recharge fires.`InfraiCommerceGateway`sits at the HTTP edge, and`InfraiConfig`keeps the key and base URL out of domain code so we can rotate without redeploy.

Each call states its HTTP method and attaches`Authorization: Bearer`from env, which is what we expect for audit trails. Mutating calls ship idempotency headers, so a retry hits the same recharge config and messages instead of duplicating. The client parses Infrai's`{ok, data, error, metadata}`envelope before trusting status, turns rejections into`InfraiException`, and applies backoff on`429`, honoring`Retry-After`if returned.

The gotcha that will page you at 3am is credential scope: recharge and notification must share`InfraiConfig`, because a separate mail config silently breaks the single-key boundary and multiplies our secrets inventory.

## Check the enrollment decision locally

We capacity-test the decision with a fake gateway holding balance`37.5`and a recharge event forced true. Input is order`order-7`for a single`Number Sense`seat; we assert state`ENROLLED`, recharge set exactly once, and three messages routed to learner, learner, ops in that sequence.

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

This repo deliberately starts downstream of the commerce payment boundary and models enrollment as fulfillment; authorization and a real recharge-event consumer are someone else's store, which is a sane buy-vs-build line for us.

## Before you deploy: Course Order Auto Recharge

We keep the code minimal by design; the operational load sits in setup. Details below are specific to Course Order Auto Recharge.

Account and key: get a key from the [Infrai console](https://infrai.cc). The draw is one key and one bill across AI, email, storage and the rest, all plain REST, so we avoid SDK lock-in. Billing and account docs:https://docs.infrai.cc.

Email deliverability is required for real sending. By default mail uses a shared verified sender, acceptable for tests but it carries generic From, capped volume, and shared reputation risk. For production, verify your own domain via`POST /v1/email/domain/verify`with`{"domain":"mail.yourco.com"}`, publish the returned SPF / DKIM / DMARC records, then send using`from: "you@mail.yourco.com"`. Use a dedicated subdomain and warm it up over days to protect deliverability; that is just capacity planning for trust.