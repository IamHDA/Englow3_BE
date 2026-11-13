#!/usr/bin/env python3
"""Download each test's Listening recording and cut it into the four part files the loader uploads.

    python split_audio.py --input ~/Downloads/ielts-scrape --sets sets

Run to_englow_sets.py first: this fills in audio/part-1.mp3 ... part-4.mp3 inside each set folder,
where scripts/load_ielts_trial.py --set-dir looks for them. Needs ffmpeg and ffprobe on PATH.

The source has one recording per test. Between two parts it always has the same shape: half a minute
of silence to check answers, a few seconds of "Now turn to part N", then another long silence to read
the next questions. The cut goes at the end of the first silence, so each part opens with its own
introduction. Where the site gives "listen from here" times, a cut must fall between the last answer
of one part and the first answer of the next (one mistyped time per part is tolerated), or the test is not cut at all.

Every result is written to the set's source.json under audio_split. "unconfirmed" means the cuts
came from the silences alone: listen to the three joins before loading. To set the cuts by hand:

    python split_audio.py --input ... --sets sets --only <set folder name> --cuts 7:43,14:50,22:10
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import statistics
import subprocess
import sys
import urllib.request
from pathlib import Path

from to_englow_sets import slug

MIN_SILENCE = 15.0     # seconds; the pauses between parts are 20-60 s, pauses inside speech far shorter
MAX_GAP = 90.0         # the "Now turn to part N" announcement between the two silences lasts 10-60 s
UPLOAD_LIMIT = 11_000_000  # bytes; the backend refuses media over 12 MB
NO_AUDIO_NOTE = "Listening has no per-part audio"


def clock(seconds: float) -> str:
    return f"{int(seconds // 60)}:{int(seconds % 60):02d}"


def seconds_of(text: str) -> float:
    total = 0.0
    for piece in text.strip().split(":"):
        total = total * 60 + float(piece)
    return total


def run(*command: str) -> subprocess.CompletedProcess:
    return subprocess.run(command, capture_output=True, text=True, check=True)


def duration_of(path: Path) -> float:
    out = run("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path))
    return float(out.stdout.strip())


def silences_in(path: Path) -> list[tuple[float, float]]:
    out = run("ffmpeg", "-hide_banner", "-nostats", "-i", str(path),
              "-af", f"silencedetect=noise=-35dB:d={MIN_SILENCE}", "-f", "null", "-")
    starts = [float(x) for x in re.findall(r"silence_start: ([\d.]+)", out.stderr)]
    ends = [float(x) for x in re.findall(r"silence_end: ([\d.]+)", out.stderr)]
    return list(zip(starts, ends))  # a silence still open at the end of the file has no end and is dropped


def answer_times(listening: dict) -> list[list[float]] | None:
    """The site's "listen from here" time of every question, per part; None when a part has none."""
    times = [[seconds_of(q["audio_start"]) for g in part["question_groups"] for q in g["questions"] if q.get("audio_start")]
             for part in listening["parts"]]
    return times if len(times) == 4 and all(times) else None


def find_cuts(silences, total: float, answers) -> tuple[list[float] | None, str, str]:
    """Returns (cuts, status, explanation)."""
    # A part break is two long silences with only an announcement between them, so the candidates
    # are neighbouring silences, ranked by how little sound separates them. The same shape can
    # close the recording ("that is the end of the test"), so a pair running into the end is ignored.
    pairs = sorted((b_start - a_end, a_end) for (_, a_end), (b_start, b_end) in zip(silences, silences[1:])
                   if b_end < total - 5)
    if answers:
        middles = [statistics.median(part) for part in answers]
        cuts = []
        for n in range(3):
            inside = [(gap, at) for gap, at in pairs if middles[n] < at < middles[n + 1] and gap <= MAX_GAP]
            if not inside:
                return None, "failed", f"no part break found between parts {n + 1} and {n + 2}"
            at = inside[0][1]
            # One stray time per part is tolerated: the source has the odd mistyped one.
            late = sum(t > at for t in answers[n])
            early = sum(t < at for t in answers[n + 1])
            if late > 1 or early > 1:
                return None, "failed", (f"the break at {clock(at)} leaves {late} answers of part {n + 1} after it "
                                        f"and {early} of part {n + 2} before it")
            cuts.append(at)
        return cuts, "confirmed", "every cut lies between the answer times of the parts on either side"

    if len(pairs) < 3 or pairs[2][0] > MAX_GAP:
        return None, "failed", "the silences do not show three part breaks, and the source gives no answer times to choose by"
    if len(pairs) > 3 and pairs[3][0] < 1.5 * pairs[2][0]:
        return None, "failed", "a fourth pause looks too much like a part break to tell them apart without answer times"
    cuts = sorted(at for _, at in pairs[:3])
    if min(b - a for a, b in zip([0.0] + cuts, cuts + [total])) < 180:
        return None, "failed", "the breaks found would make a part shorter than three minutes"
    return cuts, "unconfirmed", "cut from the silences alone; the source gives no answer times to check against"


def cut(source: Path, start: float, end: float | None, target: Path) -> None:
    span = ["-ss", f"{start:.2f}"] + (["-to", f"{end:.2f}"] if end is not None else [])
    run("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *span, "-i", str(source), "-c", "copy", str(target))
    if target.stat().st_size > UPLOAD_LIMIT:
        # High-bitrate sources give parts over the upload limit; speech survives 96 kbps mono.
        run("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *span, "-i", str(source),
            "-ac", "1", "-c:a", "libmp3lame", "-b:a", "96k", str(target))
    if target.stat().st_size > UPLOAD_LIMIT:
        raise RuntimeError(f"{target.name} is still {target.stat().st_size / 1e6:.1f} MB after re-encoding")


def download(url: str, target: Path) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    partial = target.with_suffix(".part")
    with urllib.request.urlopen(request, timeout=120) as response, partial.open("wb") as out:
        shutil.copyfileobj(response, out)
    partial.replace(target)


def split(folder: Path, listening: dict, manual: list[float] | None) -> dict:
    audio = folder / "audio"
    audio.mkdir(exist_ok=True)
    source = audio / "source.mp3"
    if not source.is_file():
        urls = [u for u in listening.get("audio_urls", []) if re.search(r"\.mp3($|\?)", u, re.IGNORECASE)]
        if not urls:
            return {"status": "failed", "reason": "the source has no mp3 link (YouTube only)"}
        download(urls[0], source)
    total = duration_of(source)
    silences = silences_in(source)
    if manual:
        cuts, status, reason = manual, "manual", "cuts given with --cuts"
        if not all(a < b for a, b in zip([0.0] + cuts, cuts + [total])):
            raise ValueError(f"--cuts must be three increasing times inside the {clock(total)} recording")
    else:
        cuts, status, reason = find_cuts(silences, total, answer_times(listening))
        if cuts:
            cuts = [c - 1.0 for c in cuts]  # a second of silence before "Now turn to part N"
    result = {"status": status, "reason": reason, "duration": clock(total),
              "long_silences": [f"{clock(a)}-{clock(b)}" for a, b in silences]}
    if not cuts:
        return result
    edges = [0.0] + cuts + [None]
    for n in range(1, 5):
        cut(source, edges[n - 1], edges[n], audio / f"part-{n}.mp3")
    result["cuts"] = [clock(c) for c in cuts]
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", type=Path, required=True, help="folder of JSON files written by the extension")
    parser.add_argument("--sets", type=Path, required=True, help="folder of set folders from to_englow_sets.py")
    parser.add_argument("--only", help="work on this one set folder")
    parser.add_argument("--cuts", help="with --only: three cut times, e.g. 7:43,14:50,22:10")
    parser.add_argument("--force", action="store_true", help="cut again even when the part files exist")
    args = parser.parse_args()
    if args.cuts and not args.only:
        parser.error("--cuts needs --only, since cut times belong to one recording")
    for tool in ("ffmpeg", "ffprobe"):
        if not shutil.which(tool):
            print(f"{tool} is not on PATH; install ffmpeg first.", file=sys.stderr)
            return 2
    manual = [seconds_of(c) for c in args.cuts.split(",")] if args.cuts else None
    if manual and len(manual) != 3:
        parser.error("--cuts takes exactly three times")

    counts = {"confirmed": 0, "unconfirmed": 0, "manual": 0, "failed": 0, "already cut": 0, "no set folder": 0}
    for path in sorted(p for p in args.input.glob("*.json") if not p.name.endswith(".answers.json")):
        collection = json.loads(path.read_text(encoding="utf-8"))
        for test in collection["tests"]:
            folder = args.sets / f"{path.stem}--{slug(test['name'])}"
            if args.only and folder.name != args.only:
                continue
            if "listening" not in test["sections"] or not (folder / "source.json").is_file():
                counts["no set folder"] += 1
                print(f"no set   {folder.name}: run to_englow_sets.py first")
                continue
            if (folder / "audio" / "part-4.mp3").is_file() and not args.force and not manual:
                counts["already cut"] += 1
                continue
            try:
                result = split(folder, test["sections"]["listening"], manual)
            except ValueError as e:  # bad --cuts: nothing was cut, so the set's record stays as it was
                print(e, file=sys.stderr)
                return 2
            except (OSError, subprocess.CalledProcessError, RuntimeError) as e:
                detail = e.stderr.strip().splitlines()[-1] if isinstance(e, subprocess.CalledProcessError) and e.stderr else str(e)
                result = {"status": "failed", "reason": detail}
            counts[result["status"]] += 1

            record = json.loads((folder / "source.json").read_text(encoding="utf-8"))
            record["audio_split"] = result
            notes = [n for n in record["needs_review"] if not n.startswith((NO_AUDIO_NOTE, "Listening audio"))]
            if result["status"] == "failed":
                notes.append(f"Listening audio was not cut ({result['reason']}); the paper loads silent until "
                             "audio/part-1.mp3 to part-4.mp3 exist. Pick the cuts by ear and pass them with --cuts.")
            elif result["status"] == "unconfirmed":
                notes.append(f"Listening audio was cut at {', '.join(result['cuts'])} from the silences alone: "
                             "listen to the start of parts 2-4 before loading.")
            record["needs_review"] = notes
            (folder / "source.json").write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
            print(f"{result['status']:<12} {folder.name}: " + (", ".join(result["cuts"]) if "cuts" in result else result["reason"]))

    print("\n" + ", ".join(f"{n} {name}" for name, n in counts.items() if n))
    return 1 if counts["failed"] or counts["no set folder"] else 0


if __name__ == "__main__":
    sys.exit(main())
