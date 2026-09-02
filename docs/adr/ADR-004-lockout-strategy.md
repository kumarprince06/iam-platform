# ADR-004: Account Lockout Strategy

## Status
Accepted

## Context
Repeated failed login attempts against a single account are the signature of a
brute-force or credential-stuffing attack. We need a strategy that slows an
attacker down meaningfully without creating an easy denial-of-service vector
against legitimate users (an attacker who knows the lockout rule can deliberately
fail logins to lock a real user out of their own account).

## Options Considered

### Option A — Fixed attempt count, fixed lockout duration
E.g. 5 failed attempts → account locked for 15 minutes, full stop, counter resets
after the lockout window passes.
- **Pros:** Simple to implement and to reason about. Easy to explain to a user
  ("try again in 15 minutes").
- **Cons:** An attacker can script exactly 4 attempts per account per 15-minute
  window indefinitely, which is still a meaningful brute-force budget over days.
  Also fully vulnerable to the DoS concern above — an attacker who only wants to
  lock a legitimate user out (not actually guess the password) can do so with 5
  cheap requests, repeatedly, forever.

### Option B — Exponential backoff on lockout duration
E.g. 1st lockout = 1 min, 2nd = 2 min, 3rd = 4 min, 4th = 8 min... doubling each
time, reset only after a long period of no failed attempts (e.g. 24 hours clean).
- **Pros:** Meaningfully punishes sustained attack attempts — the cost of continuing
  to brute-force grows without bound, which fixed lockout doesn't achieve.
- **Cons:** More state to track (attempt history, not just a counter). Still
  vulnerable to the same DoS concern as Option A in the short term — the first few
  lockouts are cheap for an attacker to trigger against a victim.

### Option C — IP + account combined rate limiting, with fixed lockout as fallback
Rate-limit failed attempts per source IP (independent of which account is being
targeted) *and* per account (independent of source IP), with fixed lockout
(Option A) as the account-level mechanism. An attacker distributing attempts across
many IPs still hits the per-account limit; an attacker hitting many accounts from
one IP hits the per-IP limit first.
- **Pros:** Meaningfully harder to weaponize as either a brute-force tool or a
  targeted DoS against one user, since the two limits catch different attack
  shapes. This is closer to what production systems actually do.
- **Cons:** More moving parts — two separate counters, two separate Redis key
  patterns, and a decision about which limit takes precedence in logging/response
  messaging.

## Decision
**Option C**, using Option A's mechanics for the account-level lockout: **5 failed
attempts locks the account for 15 minutes**, tracked via a Redis key per user with
a matching TTL (so it self-expires — no cleanup job needed). Layered with a
**per-IP limit of 20 failed attempts across any accounts in a 15-minute window**,
tracked the same way, keyed by source IP.

Exponential backoff (Option B) is explicitly not chosen for v1 — it's a genuine
improvement worth naming as a considered future iteration, but it adds tracking
complexity (attempt *history*, not just a count) that isn't justified until the
simpler two-limit approach is proven insufficient. This is a "start simple,
escalate if needed" decision, not a rejection of Option B on merit.

## Consequences
- Two Redis key patterns needed: `lockout:account:{userId}` and
  `lockout:ip:{ipAddress}`, both with TTL = lockout window, both incremented on
  every failed login before the account/IP check runs.
- The login endpoint must check both limits before attempting credential
  validation — if either is tripped, short-circuit with 423 Locked and log which
  limit fired (account or IP), because that distinction matters for tuning
  thresholds later.
- **The concurrency test flagged back in the task list is precisely about this
  mechanism** — two failed logins arriving near-simultaneously must not both read
  the counter as "4" and both proceed, missing the 5th-attempt lockout. This needs
  an atomic Redis `INCR`, not a read-then-write pattern, or the lockout can be
  silently bypassed under concurrent load. Worth calling out explicitly since it's
  the kind of race condition that's easy to miss in a naive implementation and a
  strong interview talking point once you can explain why `INCR` matters here.
- Thresholds (5 attempts / 15 min account, 20 attempts / 15 min IP) are stated as
  starting values, not researched-optimal numbers — worth flagging honestly rather
  than presenting them as if they came from a threat model that doesn't exist yet.

## Related
- ADR-001: JWT storage strategy
- ADR-003: Session invalidation on logout
