# Scope — Identity & Access Management Platform

## Problem
Build a standalone authentication and authorization service that other applications
(future projects in this portfolio, and eventually real clients) can integrate with,
similar in spirit to a simplified Auth0/Okta.

## Users
- **End users** — register, log in, manage their own session, reset password.
- **Admins** — same as end users, plus access to admin-only protected routes (used to
  demonstrate RBAC).
- **Consuming services** (future) — other backend services validate a JWT issued by
  this platform instead of implementing their own auth from scratch.

## In Scope — v1
- User registration with email + password
- Login / logout
- JWT-based authentication (short-lived access token + refresh token)
- Refresh token rotation
- Role-based access control — two roles: `USER`, `ADMIN`
- Password reset flow (forgot password → reset via token)
- Account lockout after repeated failed login attempts
- Structured logging for all auth events
- Basic rate limiting on login and forgot-password endpoints

## Explicitly Out of Scope — v1 (parked for v2)
- OAuth2 / social login (Google, GitHub sign-in)
- Multi-factor authentication (MFA/2FA)
- Fine-grained permission policies (ABAC) beyond the two fixed roles
- Email verification on registration (assume email is trusted for v1 — flagged as a
  known gap, not an oversight)
- Admin UI / dashboard — this is an API-only service for v1

## Success Criteria
- A new user can register, log in, access a protected route, refresh their session,
  and log out — entirely through the API, with no manual DB intervention.
- Two users failing login repeatedly get locked out independently (no shared-state bug).
- An `ADMIN`-only route correctly rejects a `USER` token.
- All of the above is provable by an automated test, not just manual `curl` checks.

## Non-Goals
This is not a production-grade identity provider — no SOC2/compliance concerns are in
scope. The goal is to demonstrate the *engineering* of an IAM system (security
fundamentals, JWT lifecycle, RBAC, concurrency-safe lockout) for portfolio and
interview purposes.
