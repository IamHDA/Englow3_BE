"""Draws a Writing Task 1 chart from the figures the model wrote.

D-IELTS-9: the data comes first, the picture after.
"""

from __future__ import annotations

from pathlib import Path


def png(chart: dict) -> bytes:
    """The chart as PNG bytes, for uploading straight to storage."""
    from io import BytesIO

    buffer = BytesIO()
    _render(chart, buffer)
    return buffer.getvalue()


def draw(chart: dict, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("wb") as handle:
        _render(chart, handle)


def _render(chart: dict, target) -> None:
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    categories = chart["categories"]
    series = chart["series"]
    fig, ax = plt.subplots(figsize=(8, 5), dpi=150)
    colours = ["#4a6fa5", "#e0a030", "#5a9e6f", "#b5566b"]
    if chart["type"] == "bar":
        width = 0.8 / len(series)
        for i, s in enumerate(series):
            xs = [x - 0.4 + width * (i + 0.5) for x in range(len(categories))]
            bars = ax.bar(xs, s["values"], width, label=s["name"], color=colours[i % len(colours)])
            ax.bar_label(bars, fmt="%g", padding=2, fontsize=8)
        ax.set_xticks(list(range(len(categories))), categories)
    else:
        for i, s in enumerate(series):
            ax.plot(
                categories,
                s["values"],
                marker="o",
                label=s["name"],
                color=colours[i % len(colours)],
            )
    ax.set_ylabel(chart["unit"])
    ax.set_title(chart["title"])
    ax.set_ylim(bottom=0)
    if len(series) > 1 or chart["type"] == "line":
        ax.legend(frameon=False)
    ax.spines[["top", "right"]].set_visible(False)
    fig.tight_layout()
    fig.savefig(target, format="png")
    plt.close(fig)
