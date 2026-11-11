---
name: check-contract
description: Check that implemented endpoints in epos-api match docs/api-spec.md. Use after finishing a group of endpoints and before committing.
---

# Check conformance with the API contract

Argument: an endpoint group such as "products" or "sales", or empty for all.

The frontend is already built against `docs/api-spec.md`, so any drift in the
backend breaks it silently. This skill only REPORTS differences. It never
changes code.

## Steps

1. Read the matching section of `docs/api-spec.md`.
2. Read the corresponding controllers, request/response DTOs, exception
   classes, and `GlobalExceptionHandler`.
3. Compare and report ONLY mismatches in these areas:
   - field names (snake_case), types, and missing or extra fields
   - HTTP status code for each condition
   - the error `code` value for each failure
   - role restrictions (`@PreAuthorize`) against the permissions table
   - pagination parameters and `PageResponse` shape
   - fields that must never leak: `password_hash`, and `cost_price` for
     the CASHIER role
4. For each mismatch give: the endpoint, what the spec says, what the code
   does, and the file and line.
5. Do not modify any file.
6. If you think the code's behavior is better than the spec, list it
   separately under "Suggestions". Do not count it as a finding.

## Output format

A short table of findings, then the suggestions list. If everything matches,
say so in one line.
