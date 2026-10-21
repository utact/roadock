"""Test Roadock commit format and hook exit codes."""

import runpy
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HOOK = Path(__file__).with_name("commit-msg").resolve()
validate = runpy.run_path(str(HOOK))["validate"]

SUBJECT = "fix(github-sync): 조회 실패 시 체크리스트 유지"
BODY = "- 캐시 갱신을 조회 성공 시점으로 이동"
TRAILER = "Refs: #12"
SUBJECT_ERROR = "제목: type(scope): 한글 제목 (scope 생략 가능, 소문자 영문 kebab-case, ! 금지)"
SEPARATOR_ERROR = "제목 뒤에 빈 줄을 둔다"
BODY_ERROR = "본문 항목은 '- '로 시작한다"
BODY_SPACING_ERROR = "본문 항목 사이에 빈 줄을 넣지 않는다"
TRAILER_SEPARATOR_ERROR = "Git trailer 앞에 빈 줄을 둔다"
TRAILER_ORDER_ERROR = "Git trailer 뒤에 본문을 쓰지 않는다"
PERIOD_ERROR = "제목과 본문 항목 끝에 마침표를 쓰지 않는다"


class CommitMessageTest(unittest.TestCase):
    def assert_rejected_variant(self, accepted, rejected, error):
        self.assertEqual([], validate(accepted), accepted)
        self.assertEqual([error], validate(rejected), rejected)

    def test_subject_types(self):
        messages = (
            "build(android): APK 빌드 설정",
            "chore: 개발 환경 정리",
            "ci(release): APK 배포 작업 추가",
            "docs: 프로젝트 범위 정리",
            "feat(widget): 좌우 버튼으로 프로젝트 전환",
            SUBJECT,
            "perf(checklist): 체크박스 파싱 속도 개선",
            "refactor(checklist): 체크박스 파서 분리",
            "revert(widget): 프로젝트 전환 변경 되돌리기",
            "style(widget): 위젯 코드 형식 정리",
            "test(checklist): 체크박스 파싱 검증",
        )
        for message in messages:
            with self.subTest(message=message):
                self.assertEqual([], validate(message))
        self.assert_rejected_variant(
            SUBJECT, "unknown(github-sync): 조회 실패 시 체크리스트 유지", SUBJECT_ERROR,
        )

    def test_subject_requires_type(self):
        self.assert_rejected_variant(
            "fix: 조회 실패 시 체크리스트 유지", "조회 실패 시 체크리스트 유지", SUBJECT_ERROR,
        )

    def test_subject_requires_korean_summary(self):
        for summary in ("Preserve checklist on refresh failure", "", "   "):
            with self.subTest(summary=summary):
                self.assert_rejected_variant(SUBJECT, "fix(github-sync): " + summary, SUBJECT_ERROR)

    def test_optional_scope(self):
        for scope in ("widget", "github-sync", "widget2", None):
            prefix = "fix" if scope is None else f"fix({scope})"
            with self.subTest(scope=scope):
                self.assertEqual([], validate(prefix + ": 체크리스트 표시 수정"))
        for scope in (
            "GitHub-sync", "github.sync", "github/sync", "github_sync",
            "github--sync", "github-", "2github", "",
        ):
            with self.subTest(scope=scope):
                self.assert_rejected_variant(
                    SUBJECT, f"fix({scope}): 체크리스트 표시 수정", SUBJECT_ERROR,
                )

    def test_breaking_change_marker(self):
        for prefix in ("fix", "fix(widget)"):
            with self.subTest(prefix=prefix):
                self.assert_rejected_variant(
                    prefix + ": 체크리스트 표시 수정",
                    prefix + "!: 체크리스트 표시 수정", SUBJECT_ERROR,
                )

    def test_subject_terminal_period(self):
        for period in (".", "。"):
            with self.subTest(period=period):
                self.assert_rejected_variant(SUBJECT, SUBJECT + period, PERIOD_ERROR)

    def test_subject_separator(self):
        for content in (BODY, TRAILER):
            with self.subTest(content=content):
                self.assert_rejected_variant(
                    SUBJECT + "\n\n" + content, SUBJECT + "\n" + content, SEPARATOR_ERROR,
                )

    def test_body_marker(self):
        for marker in ("", "* "):
            with self.subTest(marker=marker):
                self.assert_rejected_variant(
                    SUBJECT + "\n\n" + BODY, SUBJECT + "\n\n" + marker + BODY[2:], BODY_ERROR,
                )

    def test_empty_body_item(self):
        self.assert_rejected_variant(SUBJECT + "\n\n" + BODY, SUBJECT + "\n\n- ", BODY_ERROR)

    def test_body_terminal_period(self):
        for period in (".", "。"):
            with self.subTest(period=period):
                message = SUBJECT + "\n\n" + BODY
                self.assert_rejected_variant(message, message + period, PERIOD_ERROR)
        self.assertEqual([], validate(SUBJECT + "\n\n- TODO.md 조회 경로 수정\n" + BODY))

    def test_body_spacing(self):
        first = SUBJECT + "\n\n" + BODY
        second = "- 오프라인 재조회 검증"
        for separator in ("\n\n", "\n \n", "\n\n\n"):
            with self.subTest(separator=repr(separator)):
                self.assert_rejected_variant(
                    first + "\n" + second, first + separator + second, BODY_SPACING_ERROR,
                )

    def test_trailers_with_and_without_body(self):
        trailers = TRAILER + "\nCo-authored-by: Contributor <contributor@example.com>"
        for body in (BODY + "\n\n", ""):
            with self.subTest(body=body):
                self.assertEqual([], validate(SUBJECT + "\n\n" + body + trailers))
        self.assertEqual([], validate(SUBJECT + "\n\n" + BODY + "\n\n"))

    def test_trailer_separator(self):
        message = SUBJECT + "\n\n" + BODY
        self.assert_rejected_variant(
            message + "\n\n" + TRAILER, message + "\n" + TRAILER, TRAILER_SEPARATOR_ERROR,
        )

    def test_body_after_trailer(self):
        self.assert_rejected_variant(
            SUBJECT + "\n\n" + BODY + "\n\n" + TRAILER,
            SUBJECT + "\n\n" + TRAILER + "\n\n" + BODY,
            TRAILER_ORDER_ERROR,
        )

    def test_malformed_trailer(self):
        for trailer in ("Refs #12", "Refs:"):
            with self.subTest(trailer=trailer):
                self.assert_rejected_variant(
                    SUBJECT + "\n\n" + TRAILER, SUBJECT + "\n\n" + trailer, BODY_ERROR,
                )

    def test_git_generated_exceptions(self):
        for message in (
            "fixup! " + SUBJECT, "squash! " + SUBJECT, "amend! " + SUBJECT,
            'Revert "feat(widget): 좌우 버튼으로 프로젝트 전환"',
        ):
            with self.subTest(message=message):
                self.assertEqual([], validate(message))
        merge_subject = "Merge branch 'codex/widget'"
        self.assertEqual([], validate(merge_subject, merge=True))
        self.assertEqual([SUBJECT_ERROR], validate(merge_subject))

    def test_empty_messages(self):
        for message in ("", "\n \n", "# On branch main"):
            with self.subTest(message=message):
                self.assertEqual([], validate(message))

    def test_leading_blank_lines_do_not_bypass_validation(self):
        for prefix in ("\n", " \n\n"):
            with self.subTest(prefix=repr(prefix)):
                self.assert_rejected_variant(
                    prefix + SUBJECT, prefix + "fix(widget): English only", SUBJECT_ERROR,
                )
                self.assertEqual(
                    [BODY_ERROR], validate(prefix + SUBJECT + "\n\n캐시 갱신 시점 변경"),
                )

    def test_editor_comments(self):
        self.assertEqual([], validate(SUBJECT + "\n\n# On branch main\n" + BODY))

    def test_verbose_diff_after_scissors(self):
        self.assertEqual([], validate(
            SUBJECT + "\n\n# ------------------------ >8 ------------------------\n"
            "diff --git a/TODO.md b/TODO.md\n+이 내용은 커밋 본문이 아님\n"
        ))

    def run_hook(self, message):
        with tempfile.TemporaryDirectory(dir=HOOK.parent) as directory:
            path = Path(directory) / "COMMIT_EDITMSG"
            path.write_text(message, encoding="utf-8")
            return subprocess.run(
                [sys.executable, str(HOOK), str(path)], cwd=HOOK.parent.parent,
                capture_output=True, text=True, encoding="utf-8",
            )

    def test_hook_accepts_valid_message(self):
        result = self.run_hook(SUBJECT + "\n\n" + BODY)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("", result.stdout + result.stderr)

    def test_hook_rejects_invalid_message(self):
        result = self.run_hook("\nfix(widget): English only")
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertEqual("", result.stdout)
        self.assertEqual("commit-msg: " + SUBJECT_ERROR + "\n", result.stderr)


if __name__ == "__main__":
    unittest.main()
