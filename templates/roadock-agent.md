<!-- roadock-template: 2 -->
## ROADOCK

[Template source](https://github.com/utact/roadock/blob/main/templates/roadock-agent.md)

- Keep `TODO.md` under `## Human` and `## Agent`. Subheadings inherit the owner.
- Human owns actions only the user can perform: credentials, access, device acceptance, and blocking decisions.
- Agent owns implementation, investigation, design, and verification. Choose routine defaults; unscoped tasks belong to Agent.
- Keep current and next actions, with unblocked work first. Merge duplicates and remove canceled work.
- Write one clear Korean action per task, preferably within 24 characters excluding metadata.
- Give Human tasks unique `<!-- roadock:task-id -->` suffixes. Preserve IDs when editing or moving tasks.
- Use `Requires: id, id` only for blocking, incomplete Human tasks. Continue independent work and remove resolved dependencies.
- Read TODO before work and update it with related changes.
- Mark completion with `[x]` and keep recent results visible. Prune completed tasks once they no longer help current or next work.
- Preserve unresolved work and necessary context when pruning. Use Git history for past progress.
- Complete Human tasks only with user confirmation.
- Refresh remote changes before editing or pushing. Preserve mobile Human completions.
- Indent scope notes at least two spaces beyond the checkbox. Place them before child tasks or headings; link to details and exclude secrets.
- Roadock hides headings and shows notes in task details. Dependencies are informational, not enforced.
- Commit checklist updates with related changes. Push only with explicit approval.

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
