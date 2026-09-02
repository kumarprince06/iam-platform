# ADR-001: JWT Storage Strategy

## Status
Accepted

## Context
The Auth Service needs to issue tokens that let a client stay authenticated across
requests without re-sending credentials every time. We need to decide:
1. Where the **access token** lives between issuance and use.
2. Where the **refresh token** lives, and how it's stored server-side (if at all).
3. How this choice affects our ability to revoke a session early (see ADR-003,
   which builds directly on this decision).

## Options Considered

### Option A — Fully stateless JWT, no server-side storage at all
Access + refresh tokens are both JWTs, validated purely by signature. Nothing is
stored server-side.
- **Pros:** Zero DB/Redis lookups on every request — maximally fast, trivially
  horizontally scalable.
- **Cons:** No way to revoke a token before it naturally expires. A logout doesn't
  actually log anyone out until the token's exp time passes. Unacceptable for an
  IAM platform where "logout" and "force-revoke a compromised session" are explicit
  requirements.

### Option B — Fully stateful sessions (opaque token + server-side session store)
Client holds an opaque random token; server looks up session data in Redis/DB on
every request. No JWT at all.
- **Pros:** Instant revocation, simplest mental model.
- **Cons:** Throws away the reason to use JWT in the first place — every single
  request now requires a store lookup, and we lose the ability for other services
  (future consumers of this platform) to independently verify a token without
  calling back into this service.

### Option C — Hybrid: stateless access token (JWT) + stateful refresh token
Access token: short-lived JWT (~15 min), validated by signature alone, no storage
lookup needed. Refresh token: opaque, stored server-side in Redis with a TTL
matching its intended lifetime (~7 days), rotated on every use.
- **Pros:** Most requests (the common case — an authenticated API call) pay zero
  storage-lookup cost, since the access token is self-contained. Revocation is still
  achievable within 15 minutes worst-case by simply not renewing (deleting the
  refresh token kills future sessions), and combined with ADR-003's blacklist,
  near-immediate revocation is achievable too.
- **Cons:** Slightly more moving parts than either pure option — two token types
  with two different validation paths.

## Decision
**Option C.** Short-lived JWT access token (15 min, stateless, signature-validated)
+ opaque refresh token stored in Redis (7-day TTL, rotated on every refresh).

This is the standard industry pattern (used by Auth0, most OAuth2 implementations)
specifically because it balances the two things Option A and Option B each sacrifice:
performance-at-scale and revocability. Given this project explicitly wants to
demonstrate both, splitting responsibility between the two token types is the
right tradeoff.

## Consequences
- Access token validation requires **no Redis/DB call** — Spring Security filter
  validates the JWT signature and expiry locally. This is the fast path and the one
  hit on every single authenticated request.
- Refresh token validation **does** require a Redis lookup — but this only happens
  once per ~15 minutes per user, not per request.
- Refresh token rotation (see ADR-001 consequence, elaborated further if we write a
  dedicated rotation ADR later) means a stolen refresh token is single-use — if an
  attacker uses it, the legitimate client's next refresh attempt will fail,
  signalling compromise.
- This decision is a direct prerequisite for ADR-003 (session invalidation on
  logout) — that ADR only needs to solve for the *access token* window (15 min),
  not indefinite JWT lifetimes, because of the choice made here.

## Related
- ADR-003: Session invalidation on logout (builds on this token split)
