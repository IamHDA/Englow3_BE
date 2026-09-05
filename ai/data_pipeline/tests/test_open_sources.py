import zipfile

import pytest

from generators.fetch_open_sources import _safe_extract


def test_safe_extract_accepts_regular_archive(tmp_path):
    archive = tmp_path / "source.zip"
    destination = tmp_path / "output"
    with zipfile.ZipFile(archive, "w") as bundle:
        bundle.writestr("dataset/records.txt", "verified")

    _safe_extract(archive, destination)

    assert (destination / "dataset" / "records.txt").read_text() == "verified"


@pytest.mark.parametrize("unsafe_path", ["../outside.txt", "/absolute.txt"])
def test_safe_extract_rejects_path_traversal(tmp_path, unsafe_path):
    archive = tmp_path / "source.zip"
    with zipfile.ZipFile(archive, "w") as bundle:
        bundle.writestr(unsafe_path, "untrusted")

    with pytest.raises(RuntimeError, match="Unsafe archive path"):
        _safe_extract(archive, tmp_path / "output")

    assert not (tmp_path / "outside.txt").exists()
