# Sigil standards policy

Applies when a review covers `../argosy-sigil` (contract checks or a review run from that repo).
Source: `../argosy-sigil/README.md`, `docs/sync.md`, `docs/save-units.md`,
`notes/save-sync-review.md`. Argosy vendors Sigil as a submodule; edits belong in
`../argosy-sigil`, never in the submodule checkout.

## Scope and severity

- Review added and changed lines only. Each finding names `file:line`, the rule and the fix.
- Blocking findings allow REJECT. Suggestions never do.

## Division of duties (blocking)

- Sigil finds and places saves; the client decides which save wins. Collect reports facts
  (`changed`, `restore_again`) and never a conflict. Code in either repo that makes Sigil pick a
  winner, or makes the client re-derive what Sigil already reports, is REJECT.
- The client always names the emulator and its save root; Sigil never searches the drive.
- A refusal writes nothing. A restore or collect that partially writes before returning an error
  is REJECT.

## Wire shapes (blocking)

- A save unit is one file, several files zipped, or the game's save folders zipped, as
  `docs/save-units.md` defines per platform. A change to a unit's shape invalidates every save
  already on a server: REJECT unless the old shape still reads back.
- Error codes, result fields and request flags exist in C and every binding (Python, Go,
  Kotlin/JNI). A change made in one place only is REJECT.

## Tests (blocking)

- A behavior change needs a test that fails against the unfixed code. Missing samples fail once a
  platform's manifest loads; a test that silently skips is REJECT.
