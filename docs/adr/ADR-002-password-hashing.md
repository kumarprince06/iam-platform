# ADR-002: Password Hashing Algorithm

## Status
Accepted

## Context
User passwords must never be stored in plaintext or in a reversible format. We need
a hashing algorithm that is deliberately slow (to resist brute-force and rainbow
table attacks) and a work factor that balances security against login latency and
server load.

## Options Considered

### Option A — BCrypt
Industry-standard adaptive hash function, built into Spring Security out of the box
(`BCryptPasswordEncoder`).
- **Pros:** Zero extra dependencies — Spring Security ships it. Well-understood,
  battle-tested since 1999, tunable work factor (log rounds). Resistant to GPU-based
  cracking better than plain SHA-family hashes.
- **Cons:** Fixed 72-byte input limit (rarely an issue for passwords, but worth
  knowing). Not memory-hard, so it's somewhat more parallelizable on custom ASIC/FPGA
  hardware than newer algorithms — a real but marginal concern outside
  nation-state-level threat models.

### Option B — Argon2 (specifically Argon2id)
Winner of the 2015 Password Hashing Competition, memory-hard by design.
- **Pros:** Explicitly designed to resist both GPU and ASIC cracking by requiring
  large amounts of memory per hash attempt, not just CPU time. Generally considered
  the strongest current choice for new systems.
- **Cons:** Not built into Spring Security's default encoder set without adding
  `spring-security-crypto`'s `Argon2PasswordEncoder` (available, but needs explicit
  configuration and a bit more tuning knowledge — memory cost, parallelism, and
  iteration count all need deliberate values, not just one work-factor knob).

### Option C — SHA-256 / SHA-512 (with manual salting)
- **Pros:** Fast, simple.
- **Cons:** Immediately disqualified — "fast" is exactly the wrong property for a
  password hash. These are cryptographic hashes designed for speed and integrity
  checking, not for resisting brute force. Never use general-purpose hash functions
  for passwords.

## Decision
**Option A — BCrypt**, via Spring Security's built-in `BCryptPasswordEncoder`, with
a work factor (log rounds) of **12**.

Argon2 is arguably the stronger long-term choice, and it's worth explicitly noting
that as a considered alternative rather than an oversight. BCrypt is chosen here
because: (1) it ships with Spring Security with no extra configuration surface to
get wrong, which matters for a project meant to demonstrate correct, idiomatic use
of the framework, not hand-tuned cryptographic parameters; (2) it remains
industry-acceptable for this threat model (a portfolio/interview project, not a
system defending against nation-state adversaries); (3) migrating to Argon2 later is
a contained, well-scoped follow-up (a "v1.1 upgrade" story) rather than a blocker
for v1.

## Consequences
- Every login pays the cost of a work-factor-12 BCrypt comparison — deliberately
  slow (roughly 200-300ms on typical hardware), which is the point, but worth
  knowing this contributes measurable latency to the login endpoint and should be
  accounted for in any load testing.
- Work factor 12 is a value that should be revisited periodically — as hardware
  gets faster, the "right" work factor increases over time. This is a parameter
  worth calling out explicitly as a maintenance concern, not a set-once constant.
- If a future security review calls for Argon2, this is an isolated change:
  swap the `PasswordEncoder` bean implementation. Because Spring Security's
  `DelegatingPasswordEncoder` prefixes stored hashes with their algorithm ID
  (e.g. `{bcrypt}$2a$12$...`), a migration to Argon2 for new passwords can coexist
  with existing BCrypt hashes without a forced mass password reset.

## Related
- ADR-001: JWT storage strategy
