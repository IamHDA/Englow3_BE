#!/usr/bin/env python3
"""Kiểm tra taxonomy/concepts.yaml — Phase 1 của work order.

Reject-only: script này KHÔNG sửa dữ liệu. Sai thì báo lỗi và exit khác 0.

Kiểm tra:
  1. YAML hợp lệ, top-level là list
  2. Đủ field bắt buộc, đúng kiểu
  3. concept_id unique, đúng snake_case
  4. domain thuộc enum
  5. cefr_band không rỗng, giá trị thuộc A1..C1, sắp xếp tăng dần
  6. parent_id null hoặc tồn tại; cây cha-con không có cycle
  7. prerequisites tồn tại; đồ thị là DAG (topological sort)
  8. mọi p_* nằm trong khoảng mở (0, 1)
  9. p_guess khớp định dạng đánh giá của concept

Dùng:
    python validators/check_taxonomy.py
    python validators/check_taxonomy.py --report reports/taxonomy_summary.md
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import defaultdict, deque
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
TAXONOMY_PATH = ROOT / "taxonomy" / "concepts.yaml"

DOMAINS = {"grammar", "vocabulary", "reading", "listening", "speaking", "writing"}
CEFR_ORDER = ["A1", "A2", "B1", "B2", "C1"]
CEFR_RANK = {level: i for i, level in enumerate(CEFR_ORDER)}
PRIOR_KEYS = ("p_init", "p_learn", "p_slip", "p_guess")
ID_RE = re.compile(r"^[a-z][a-z0-9_]*$")

REQUIRED_FIELDS = (
    "concept_id",
    "name_en",
    "name_vi",
    "domain",
    "cefr_band",
    "parent_id",
    "prerequisites",
    "bkt_priors",
    "description_vi",
)

# --- Quy ước p_guess (work order Phase 1, mục 3) -----------------------------
# p_guess phải khớp số lựa chọn của dạng câu hỏi đánh giá concept đó.
#
# TOEIC-format Part 2 chỉ có 3 lựa chọn → 1/3. Các concept dưới đây đều được
# đánh giá bằng Part 2, nên p_guess = 0.33. Giữ danh sách này đồng bộ với các
# concept Part 2 trong taxonomy và gen_listening_part2.py.
THREE_OPTION_CONCEPTS = {
    "lc_wh_question",
    "lc_yes_no",
    "lc_indirect_response",
    "lc_negative_question",
    "lc_tag_question",
    "lc_alternative_question",
    "lc_request_offer",
    "lc_suggestion",
    "lc_statement_response",
}
# Speaking/Writing chấm bằng rubric, không phải trắc nghiệm → không đoán được.
PRODUCTIVE_DOMAINS = {"speaking", "writing"}

P_GUESS_THREE_OPTION = 0.33
P_GUESS_FOUR_OPTION = 0.25
P_GUESS_PRODUCTIVE = 0.05
TOL = 1e-9


def show(path: Path) -> str:
    """Đường dẫn gọn khi nằm trong project, nguyên văn khi ở ngoài."""
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


class Report:
    """Gom lỗi và cảnh báo. Lỗi làm fail; cảnh báo thì không."""

    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []

    def error(self, msg: str) -> None:
        self.errors.append(msg)

    def warn(self, msg: str) -> None:
        self.warnings.append(msg)

    @property
    def ok(self) -> bool:
        return not self.errors


def expected_p_guess(concept: dict) -> float:
    if concept["concept_id"] in THREE_OPTION_CONCEPTS:
        return P_GUESS_THREE_OPTION
    if concept.get("domain") in PRODUCTIVE_DOMAINS:
        return P_GUESS_PRODUCTIVE
    return P_GUESS_FOUR_OPTION


def load(path: Path, rep: Report) -> list[dict]:
    if not path.exists():
        rep.error(f"Không tìm thấy {path}")
        return []
    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        rep.error(f"YAML không parse được: {exc}")
        return []
    if not isinstance(data, list):
        rep.error(f"Top-level phải là list, đang là {type(data).__name__}")
        return []
    return data


def check_shape(concepts: list[dict], rep: Report) -> None:
    """Field bắt buộc, kiểu dữ liệu, enum, khoảng giá trị."""
    for i, c in enumerate(concepts):
        if not isinstance(c, dict):
            rep.error(f"[{i}] node không phải mapping")
            continue

        cid = c.get("concept_id", f"<không có concept_id, index {i}>")

        for field in REQUIRED_FIELDS:
            if field not in c:
                rep.error(f"[{cid}] thiếu field bắt buộc '{field}'")

        if "concept_id" in c and not ID_RE.match(str(c["concept_id"])):
            rep.error(f"[{cid}] concept_id không đúng snake_case")

        if c.get("domain") not in DOMAINS:
            rep.error(f"[{cid}] domain '{c.get('domain')}' không hợp lệ, phải thuộc {sorted(DOMAINS)}")

        for field in ("name_en", "name_vi", "description_vi"):
            val = c.get(field)
            if not isinstance(val, str) or not val.strip():
                rep.error(f"[{cid}] '{field}' phải là chuỗi không rỗng")

        bands = c.get("cefr_band")
        if not isinstance(bands, list) or not bands:
            rep.error(f"[{cid}] cefr_band phải là list không rỗng")
        else:
            bad = [b for b in bands if b not in CEFR_RANK]
            if bad:
                rep.error(f"[{cid}] cefr_band chứa giá trị lạ {bad}, chỉ chấp nhận {CEFR_ORDER}")
            else:
                ranks = [CEFR_RANK[b] for b in bands]
                if ranks != sorted(ranks):
                    rep.error(f"[{cid}] cefr_band phải sắp xếp tăng dần, đang là {bands}")
                if len(set(ranks)) != len(ranks):
                    rep.error(f"[{cid}] cefr_band có giá trị lặp: {bands}")

        prereqs = c.get("prerequisites")
        if not isinstance(prereqs, list):
            rep.error(f"[{cid}] prerequisites phải là list (dùng [] nếu không có)")
        elif len(set(prereqs)) != len(prereqs):
            rep.error(f"[{cid}] prerequisites có phần tử lặp")

        priors = c.get("bkt_priors")
        if not isinstance(priors, dict):
            rep.error(f"[{cid}] bkt_priors phải là mapping")
            continue

        missing = [k for k in PRIOR_KEYS if k not in priors]
        if missing:
            rep.error(f"[{cid}] bkt_priors thiếu {missing}")
        extra = [k for k in priors if k not in PRIOR_KEYS]
        if extra:
            rep.error(f"[{cid}] bkt_priors có key lạ {extra}")

        for k in PRIOR_KEYS:
            if k not in priors:
                continue
            v = priors[k]
            if not isinstance(v, (int, float)) or isinstance(v, bool):
                rep.error(f"[{cid}] bkt_priors.{k} phải là số, đang là {type(v).__name__}")
            elif not (0.0 < float(v) < 1.0):
                rep.error(f"[{cid}] bkt_priors.{k} = {v} phải nằm trong khoảng mở (0, 1)")

        if isinstance(priors.get("p_guess"), (int, float)):
            want = expected_p_guess(c)
            got = float(priors["p_guess"])
            if abs(got - want) > 0.005 + TOL:
                rep.error(
                    f"[{cid}] p_guess = {got} nhưng phải là {want} "
                    f"(domain={c.get('domain')}, "
                    f"{'3 lựa chọn' if cid in THREE_OPTION_CONCEPTS else 'rubric' if c.get('domain') in PRODUCTIVE_DOMAINS else '4 lựa chọn'})"
                )


def check_unique(concepts: list[dict], rep: Report) -> dict[str, dict]:
    by_id: dict[str, dict] = {}
    for c in concepts:
        if not isinstance(c, dict) or "concept_id" not in c:
            continue
        cid = c["concept_id"]
        if cid in by_id:
            rep.error(f"concept_id trùng: '{cid}'")
        else:
            by_id[cid] = c
    return by_id


def check_parents(by_id: dict[str, dict], rep: Report) -> None:
    for cid, c in by_id.items():
        parent = c.get("parent_id")
        if parent is None:
            continue
        if parent == cid:
            rep.error(f"[{cid}] parent_id trỏ về chính nó")
        elif parent not in by_id:
            rep.error(f"[{cid}] parent_id '{parent}' không tồn tại")

    # cycle trong cây cha-con
    for cid in by_id:
        seen = set()
        cur = cid
        while cur is not None:
            if cur in seen:
                rep.error(f"[{cid}] cây parent_id có cycle: {' -> '.join(seen)}")
                break
            seen.add(cur)
            node = by_id.get(cur)
            if node is None:
                break
            cur = node.get("parent_id")


def check_prereq_refs(by_id: dict[str, dict], rep: Report) -> None:
    for cid, c in by_id.items():
        prereqs = c.get("prerequisites")
        if not isinstance(prereqs, list):
            continue
        for p in prereqs:
            if p == cid:
                rep.error(f"[{cid}] tự làm prerequisite của chính nó")
            elif p not in by_id:
                rep.error(f"[{cid}] prerequisite '{p}' không tồn tại trong taxonomy")


def topological_sort(by_id: dict[str, dict], rep: Report) -> list[str]:
    """Kahn. Trả về thứ tự topo; nếu có cycle thì báo lỗi và trả về []."""
    indegree: dict[str, int] = {cid: 0 for cid in by_id}
    dependents: dict[str, list[str]] = defaultdict(list)

    for cid, c in by_id.items():
        for p in c.get("prerequisites") or []:
            if p in by_id:
                dependents[p].append(cid)
                indegree[cid] += 1

    queue = deque(sorted(cid for cid, d in indegree.items() if d == 0))
    order: list[str] = []
    while queue:
        cur = queue.popleft()
        order.append(cur)
        for nxt in sorted(dependents[cur]):
            indegree[nxt] -= 1
            if indegree[nxt] == 0:
                queue.append(nxt)

    if len(order) != len(by_id):
        stuck = sorted(set(by_id) - set(order))
        rep.error(
            f"Đồ thị prerequisite CÓ CYCLE. {len(stuck)} concept không xếp được thứ tự: {stuck}"
        )
        return []
    return order


def check_prereq_difficulty(by_id: dict[str, dict], rep: Report) -> None:
    """Cảnh báo: prerequisite khó hơn concept nó dẫn tới thì mô hình hoá sai."""
    for cid, c in by_id.items():
        bands = c.get("cefr_band") or []
        if not bands or bands[0] not in CEFR_RANK:
            continue
        own = CEFR_RANK[bands[0]]
        for p in c.get("prerequisites") or []:
            pn = by_id.get(p)
            if not pn:
                continue
            pb = pn.get("cefr_band") or []
            if not pb or pb[0] not in CEFR_RANK:
                continue
            if CEFR_RANK[pb[0]] > own:
                rep.warn(
                    f"[{cid}] ({bands[0]}) có prerequisite '{p}' ở band cao hơn ({pb[0]})"
                )


def depth_of(cid: str, by_id: dict[str, dict]) -> int:
    depth = 0
    cur = by_id[cid].get("parent_id")
    seen = set()
    while cur is not None and cur in by_id and cur not in seen:
        seen.add(cur)
        depth += 1
        cur = by_id[cur].get("parent_id")
    return depth


def write_summary(by_id: dict[str, dict], order: list[str], path: Path) -> None:
    children: dict[str, list[str]] = defaultdict(list)
    for cid, c in by_id.items():
        if c.get("parent_id"):
            children[c["parent_id"]].append(cid)

    leaves = sorted(cid for cid in by_id if not children[cid])
    containers = sorted(cid for cid in by_id if children[cid])
    max_depth = max(depth_of(cid, by_id) for cid in by_id)

    by_domain: dict[str, list[str]] = defaultdict(list)
    for cid, c in by_id.items():
        by_domain[c["domain"]].append(cid)

    band_counts: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    for cid, c in by_id.items():
        for b in c["cefr_band"]:
            band_counts[c["domain"]][b] += 1

    lines: list[str] = []
    lines.append("# Taxonomy Summary\n")
    lines.append(
        "Sinh tự động bởi `validators/check_taxonomy.py --report`. "
        "Không sửa tay — sửa `taxonomy/concepts.yaml` rồi chạy lại.\n"
    )
    lines.append(f"**Tổng số concept:** {len(by_id)}")
    lines.append(f"**Node lá (mang item):** {len(leaves)}")
    lines.append(f"**Node gom nhóm (không mang item):** {len(containers)}")
    lines.append(f"**Độ sâu cây tối đa:** {max_depth}\n")

    lines.append("## Phân bố theo domain\n")
    lines.append("| Domain | Tổng | Node lá | Node gom |")
    lines.append("|---|---:|---:|---:|")
    for d in sorted(by_domain):
        ids = by_domain[d]
        n_leaf = sum(1 for cid in ids if not children[cid])
        lines.append(f"| {d} | {len(ids)} | {n_leaf} | {len(ids) - n_leaf} |")
    lines.append(f"| **TỔNG** | **{len(by_id)}** | **{len(leaves)}** | **{len(containers)}** |\n")

    lines.append("## Phân bố theo domain × CEFR band\n")
    lines.append(
        "Một concept trải nhiều band sẽ được đếm ở mọi band nó thuộc về, "
        "nên tổng hàng lớn hơn số concept.\n"
    )
    lines.append("| Domain | " + " | ".join(CEFR_ORDER) + " |")
    lines.append("|---" * (len(CEFR_ORDER) + 1) + "|")
    for d in sorted(band_counts):
        row = [str(band_counts[d].get(b, 0)) for b in CEFR_ORDER]
        lines.append(f"| {d} | " + " | ".join(row) + " |")
    totals = [
        str(sum(band_counts[d].get(b, 0) for d in band_counts)) for b in CEFR_ORDER
    ]
    lines.append("| **TỔNG** | " + " | ".join(f"**{t}**" for t in totals) + " |\n")

    lines.append("## Độ sâu cây\n")
    depth_counts: dict[int, int] = defaultdict(int)
    for cid in by_id:
        depth_counts[depth_of(cid, by_id)] += 1
    lines.append("| Độ sâu | Số concept |")
    lines.append("|---:|---:|")
    for d in sorted(depth_counts):
        lines.append(f"| {d} | {depth_counts[d]} |")
    lines.append("")

    lines.append("## Cây concept\n")
    lines.append("```")

    def emit(cid: str, indent: int) -> None:
        c = by_id[cid]
        mark = "" if children[cid] else "  *"
        lines.append(f"{'  ' * indent}{cid}  [{','.join(c['cefr_band'])}]{mark}")
        for ch in sorted(children[cid]):
            emit(ch, indent + 1)

    for cid in sorted(cid for cid in by_id if not by_id[cid].get("parent_id")):
        emit(cid, 0)
    lines.append("```")
    lines.append("`*` = node lá, sẽ mang item.\n")

    lines.append("## Node lá\n")
    lines.append(
        f"{len(leaves)} node dưới đây là nơi item thực sự gắn vào. "
        "Chỉ tiêu 10–30 item mỗi node áp dụng cho danh sách này, không áp cho node gom nhóm.\n"
    )
    for d in sorted(by_domain):
        dl = [cid for cid in leaves if by_id[cid]["domain"] == d]
        if not dl:
            continue
        lines.append(f"**{d}** ({len(dl)})\n")
        for cid in sorted(dl):
            lines.append(f"- `{cid}` — {by_id[cid]['name_vi']}")
        lines.append("")

    lines.append("## Thứ tự topological của prerequisite graph\n")
    lines.append(
        "Không có cycle. Thứ tự dưới đây là một trình tự học hợp lệ: "
        "mọi concept đều đứng sau toàn bộ prerequisite của nó.\n"
    )
    lines.append("```")
    for i, cid in enumerate(order, 1):
        lines.append(f"{i:3d}. {cid}")
    lines.append("```")

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--path", type=Path, default=TAXONOMY_PATH)
    ap.add_argument("--report", type=Path, default=None, help="ghi taxonomy summary ra file")
    args = ap.parse_args()

    rep = Report()
    concepts = load(args.path, rep)
    if not rep.ok:
        for e in rep.errors:
            print(f"ERROR  {e}")
        print(f"\nFAIL — {len(rep.errors)} lỗi")
        return 1

    print(f"Đã đọc {len(concepts)} concept từ {show(args.path)}")

    check_shape(concepts, rep)
    by_id = check_unique(concepts, rep)
    check_parents(by_id, rep)
    check_prereq_refs(by_id, rep)
    check_prereq_difficulty(by_id, rep)

    order = topological_sort(by_id, rep) if rep.ok else []

    for w in rep.warnings:
        print(f"WARN   {w}")
    for e in rep.errors:
        print(f"ERROR  {e}")

    if not rep.ok:
        print(f"\nFAIL — {len(rep.errors)} lỗi, {len(rep.warnings)} cảnh báo")
        return 1

    n_edges = sum(len(c.get("prerequisites") or []) for c in by_id.values())
    n_roots = sum(1 for c in by_id.values() if c.get("parent_id") is None)
    print(f"  concept_id unique          OK  ({len(by_id)} concept)")
    print("  field & kiểu dữ liệu       OK")
    print("  cefr_band hợp lệ           OK")
    print("  bkt_priors trong (0,1)     OK")
    print(f"  p_guess khớp số đáp án     OK  (3 lựa chọn: {len(THREE_OPTION_CONCEPTS)}, rubric: "
          f"{sum(1 for c in by_id.values() if c['domain'] in PRODUCTIVE_DOMAINS)})")
    print(f"  parent_id tồn tại, no cycle OK  ({n_roots} node gốc)")
    print(f"  prerequisite DAG no cycle  OK  ({n_edges} cạnh, topo sort {len(order)} node)")
    print(f"\nPASS — 0 lỗi, {len(rep.warnings)} cảnh báo")

    if args.report:
        out = args.report if args.report.is_absolute() else ROOT / args.report
        write_summary(by_id, order, out)
        print(f"Đã ghi {show(out)}")

    return 0


if __name__ == "__main__":
    reconfigure = getattr(sys.stdout, "reconfigure", None)
    if callable(reconfigure):
        reconfigure(encoding="utf-8")
    sys.exit(main())
