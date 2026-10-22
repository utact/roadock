<!-- roadock-template: 1 -->
## ROADOCK

[Template source](https://github.com/utact/roadock/blob/main/templates/roadock-agent.md)

- Maintain `TODO.md` under `## Human` and `## Agent`; subheadings inherit the owner.
- Assign ownership by who must act, not importance.
- Human: only actions the agent cannot perform that require user intervention for current or next work.
  Examples: issuing credentials, account setup, granting access, physical-device tests, and decisions that block progress.
- Do not create Human tasks for abstract policy reviews, generic approvals, routine design choices, or speculative future decisions.
- Agent: implementation, investigation, design, technical decisions, and verification.
  Choose reasonable defaults within the user's instructions.
  Unscoped tasks are treated as Agent.
- Focus on actionable current and next steps, with immediately actionable pending tasks first.
  Avoid speculative or duplicate tasks; retain all necessary work even when the list is long.
- Write one self-contained Korean action per task, preferably within 24 characters including spaces, excluding metadata.
  Prioritize clarity over the length target.
- Give each Human task a unique, stable `<!-- roadock:task-id -->` suffix; preserve it when editing or moving the task.
- Add `Requires: task-id` below an Agent task only when an incomplete Human task blocks that action.
  Separate multiple Human IDs with commas.
  Continue independent Agent work.
- Read `TODO.md` before work; update affected tasks and next steps with related changes.
- Never mark Human tasks complete without user confirmation.
- Refresh remote changes before editing or pushing; preserve mobile Human completions when resolving conflicts.
- Add indented plain-text notes directly below a task when its scope or completion criteria need context.
  Indent notes at least two spaces beyond the checkbox line; keep them before child tasks or headings.
  Use short paragraphs and Markdown links to relevant project documents. Never include secrets.
- Roadock displays task text, Human/Agent labels, and waiting labels for `Requires`.
  Notes appear in task details in the app; headings stay hidden. Dependencies are informational, not enforced.
- Commit checklist updates with related changes; push only with explicit approval.

```markdown
## Human
- [ ] API 키 발급 <!-- roadock:api-key -->
  서비스에서 프로젝트용 키를 발급하고 앱의 인증 설정에 등록한다. 키 값은 이 파일에 적지 않는다.

## Agent
- [ ] 인증 방식 비교·설계
  기존 권한과 비용을 비교하고 기본안을 정한다. 키 발급을 기다리지 않고 진행한다.
- [ ] 발급 키로 API 호출 검증
  Requires: api-key
  실제 응답과 인증 실패 처리를 확인한다.
```
<!-- /roadock-template -->
