# Commit Convention

## Subject

- Format: `type(scope): 한글 제목`; no `!`
- Types: `build`, `chore`, `ci`, `docs`, `feat`, `fix`, `perf`, `refactor`, `revert`, `style`, `test`
- Scope: English `kebab-case` starting with a lowercase letter; omit when unclear
- Summary: describe the change in Korean; no trailing period

## Body

- Omit when the subject is sufficient
- Separate from the subject with a blank line
- Items: start with `- `; one fact per line; no blank lines between items or trailing periods
- Avoid subject repetition, per-file change lists, and filler
- Git trailers: `Key: Value` at the end, preceded by a blank line; no bullet prefix or Korean requirement

## Review

- One review purpose per commit, including related code, docs, and validation
- Beyond format checks, review redundancy and agreement with the diff
- Do not rewrite past commits solely to apply these rules
- Format exemptions: Git-generated merge, `Revert `, `fixup! `, `squash! `, and `amend! ` messages

## Tools

- Enable the hook (requires Python): `git config core.hooksPath .githooks`
- Test the validator: `python .githooks/test_commit_msg.py`
