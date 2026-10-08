# RomM standards policy

Applies when a review covers `../romm` (contract checks or a review run from that repo).
Source: `../romm/AGENTS.md`, `../romm/backend/AGENTS.md`, `../romm/CONTRIBUTING.md`.

## Scope and severity

- Review added and changed lines only. Each finding names `file:line`, the rule and the fix.
- Blocking findings allow REJECT. Suggestions never do.

## API contract (blocking)

- The backend owns the API shape. Does a changed route or response schema leave the
  generated frontend types (`frontend/src/__generated__/`) stale? REJECT.
- Does a response field, status code or error body change without the endpoint test that
  pins it? REJECT.
- Does a client in another repo (Argosy, Sigil bindings) read a field, status or body shape the
  server no longer sends, or send one the server no longer accepts? REJECT, citing both sides.

## Data and migrations (blocking)

- Does a model change land without an Alembic revision, or does a revision's `down_revision`
  chain fork? REJECT.
- Validation limits live on the model and are imported by endpoints. A near-duplicate constant,
  type, regex or identity scheme where a foreign key already exists is REJECT.

## Tests travel with code (blocking)

- New logic without a test, or a new endpoint without an endpoint test, is REJECT.

## Comments and text

- Comments are one or two lines on the non-obvious why; a docstring is one sentence plus
  `Args:`/`Returns:` when needed. Comments that narrate the code or explain a change are a
  suggestion to cut. Em-dashes in comments or text are blocking.

## PR description (blocking when reviewing a RomM PR)

- AI assistance is disclosed with its extent. Issues are linked with `Fixes`/`Closes`.
  Architectural changes carry a mermaid diagram. UI changes carry a screenshot.
