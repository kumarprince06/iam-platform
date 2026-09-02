# ADR-003: Session Invalidation on Logout

## Status
Accepted

## Context
ADR-001 established a hybrid token model: short-lived (15 min) stateless JWT access
tokens validated by signature alone, plus opaque refresh tokens stored server-side
in Redis. This creates a specific problem: **a stateless JWT cannot be revoked**.
Deleting the refresh token stops future sessions, but any access token already
issued remains valid — by signature alone — until it naturally expires, up to 15
minutes later. When a user clicks "logout," or when we need to force-kill a
compromised session, that gap is a real security concern we need an explicit answer
for.

## Options Considered

### Option A — Do nothing; rely on the 15-minute expiry
Logout only deletes the refresh token. The access token quietly remains valid for
up to 15 more minutes.
- **Pros:** Zero additional infrastructure.
- **Cons:** "Logout" doesn't actually mean logged out for up to 15 minutes. For a
  platform whose entire purpose is demonstrating correct security engineering, this
  is not a defensible gap to leave unaddressed.

### Option B — Switch to fully stateful access tokens
Abandon statelessness; validate every access token against a server-side store.
- **Pros:** Trivial, instant revocation.
- **Cons:** This is Option B from ADR-001, already rejected — it throws away the
  entire performance benefit of the JWT approach on every single request, not just
  at logout time.

### Option C — Redis-backed token blacklist, checked only at the point of risk
On logout (or explicit revocation), add the access token's unique identifier
(the `jti` claim) to a Redis set, with a TTL equal to the token's *remaining*
lifetime — so the blacklist entry expires automatically at the same moment the
token would have expired anyway, never growing unbounded. Every authenticated
request checks this blacklist as a cheap Redis `EXISTS` lookup, in addition to
signature validation.
- **Pros:** Full statelessness benefit is preserved for the 99% case (a token that
  was never revoked never touches the blacklist logic in any meaningful way —
  `EXISTS` on an empty/small set is effectively free). Revocation becomes
  immediate, not eventual. TTL-based auto-expiry means the blacklist is
  self-cleaning — no cron job needed.
- **Cons:** Reintroduces exactly one Redis lookup per authenticated request — the
  cost we tried to avoid in ADR-001. This is the direct tradeoff we're accepting.

## Decision
**Option C — Redis-backed blacklist, TTL-matched to remaining token lifetime.**

This is the standard resolution to the "can't revoke a stateless JWT" problem, and
it's a deliberate, acknowledged tradeoff against ADR-001's original goal of
zero-lookup access token validation. We're accepting one cheap Redis call per
request in exchange for actual, immediate revocability — because for an IAM
platform specifically, "logout means logged out" is a correctness requirement, not
a nice-to-have.

## Consequences
- Every authenticated request now does two checks: JWT signature/expiry validation
  (free, local) + Redis blacklist lookup (cheap, but no longer zero). This is worth
  being explicit about in the README's "why two stores" section — it's a direct
  answer to "doesn't this defeat the point of JWT?" which is a fair question an
  interviewer is likely to ask.
- The blacklist entry's TTL must be calculated as `token.exp - now()`, not a fixed
  value — an access token revoked immediately after issuance needs a ~15-minute
  blacklist entry, while one revoked at minute 14 needs only ~1 minute. Getting this
  wrong either leaves stale entries forever (fixed long TTL) or lets a revoked token
  become valid again if the blacklist entry expires before the token itself would
  have (fixed short TTL).
- This same mechanism generalizes to "force logout everywhere" (e.g. after a
  password reset) — blacklist every outstanding `jti` for a user, or alternatively
  track a per-user "tokens issued before this timestamp are invalid" marker, which
  is cheaper than blacklisting many individual tokens. Worth revisiting as a
  follow-up ADR if that feature is built.

## Related
- ADR-001: JWT storage strategy (this ADR exists specifically to close the gap that
  decision created)
