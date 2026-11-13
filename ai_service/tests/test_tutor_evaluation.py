from pathlib import Path

import pytest

from evaluations.run_tutor import validate_output


def test_evaluation_cannot_overwrite_teacher_review(tmp_path: Path):
    reviewed = tmp_path / "reviewed.json"
    reviewed.write_text('{"humanVerdict": "reviewed"}', encoding="utf-8")
    with pytest.raises(ValueError, match="preserved"):
        validate_output(reviewed)
    assert reviewed.read_text(encoding="utf-8") == '{"humanVerdict": "reviewed"}'


@pytest.mark.parametrize("worktree", [False, True])
def test_evaluation_answers_stay_outside_git_checkout(tmp_path: Path, worktree: bool):
    marker = tmp_path / ".git"
    if worktree:
        marker.write_text("gitdir: somewhere", encoding="utf-8")
    else:
        marker.mkdir()
    with pytest.raises(ValueError, match="outside Git"):
        validate_output(tmp_path / "nested" / "answers.json")
