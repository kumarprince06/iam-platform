```mermaid
erDiagram
  USERS ||--o{ USER_ROLES : has
  ROLES ||--o{ USER_ROLES : assigned_to
  USERS ||--o{ LOGIN_ATTEMPTS : attempts

  USERS {
    uuid id PK
    string email
    string password_hash
    timestamp created_at
  }
  ROLES {
    uuid id PK
    string name
  }
  USER_ROLES {
    uuid user_id FK
    uuid role_id FK
  }
  LOGIN_ATTEMPTS {
    uuid id PK
    uuid user_id FK
    string ip_address
    boolean success
    timestamp attempted_at
  }
```

## Notes

- **`USER_ROLES` is a join table**, not a single `role` column on `USERS` — this
  makes the schema genuinely support a user holding multiple roles, which a flat
  column would block.
- **`LOGIN_ATTEMPTS` logs every attempt** (success and failure), not just a running
  counter — this is the durable audit trail the observability requirements (Phase 7)
  read from, distinct from the ephemeral Redis lockout counters described in
  ADR-004. The two serve different purposes: Redis answers "should this request be
  blocked right now," Postgres answers "what happened, historically."
- `password_hash` stores the BCrypt output described in ADR-002, never plaintext.
