---
name: create-crud
description: Create a new CRUD feature package in epos-api (entity, repository, service, controller, DTOs, mapper, tests). Use when asked to build a new CRUD endpoint group such as product, stock, store, or user.
---

# Create a CRUD feature package

Argument: the feature name and the matching section in `docs/api-spec.md`
(for example "product, section 3").

## Steps

1. Read the matching section in `docs/api-spec.md` and the table definition
   in PRD point 14. Do not invent fields. Field names, status codes, and
   error `code` values are already used by the frontend.
2. Check whether the table already exists in the migrations. If the schema
   needs to change, create a NEW migration file. Never edit an existing one.
3. Create these under `com.yoedi.epos.<feature>/`:
   - entity and repository
   - service (all business logic lives here)
   - controller (no business logic)
   - `dto/` with request and response classes
   - `mapper/` using MapStruct
4. Add any new exception and register its mapping in `GlobalExceptionHandler`
   with the exact `code` from `docs/api-spec.md`.
5. Apply `@PreAuthorize` according to the permissions table in
   `docs/api-spec.md`.
6. Write tests:
   - MockMvc: status codes, response shape (including `code` on errors),
     and role restrictions
   - an integration test (Testcontainers) when there are custom queries
     or database constraints involved

## Follow the existing pattern

`category/` is the reference implementation. Match its naming style,
duplicate handling (409), and test layout.

## Hard rules

- Layered architecture only: Controller -> Service -> Repository.
  Do not add `domain/`, `port/`, `application/`, or use-case classes.
- Entities never leave the service layer; controllers use DTOs only.
- Money is `long` rupiah. Never `double` or `float`.
- JSON fields are `snake_case`, handled globally by `JacksonConfig`.
  Do not add `@JsonProperty` per field.
- Error `detail` messages are written in Indonesian.
- Code comments are written in English.
- Do not add dependencies.

## When done

Report briefly: files created, endpoints added, and anything that deviates
from `docs/api-spec.md`.
