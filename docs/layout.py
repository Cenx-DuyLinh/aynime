import sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

plt.rcParams["font.family"] = ["Yu Gothic", "MS Gothic", "DejaVu Sans"]
fig, ax = plt.subplots(figsize=(13, 9))
ax.set_xlim(0, 13)
ax.set_ylim(0, 9)
ax.axis("off")

def box(x, y, w, h, text, face, edge="#333", size=10, weight="normal"):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.05,rounding_size=0.15",
                                facecolor=face, edgecolor=edge, linewidth=1.5))
    ax.text(x + w / 2, y + h / 2, text, ha="center", va="center", fontsize=size, weight=weight, wrap=True)

def arrow(x1, y1, x2, y2, label="", color="#c0392b"):
    ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2), arrowstyle="-|>", mutation_scale=18,
                                 color=color, linewidth=2))
    if label:
        ax.text((x1 + x2) / 2 + 0.1, (y1 + y2) / 2, label, fontsize=9, color=color, va="center")

ax.text(0.3, 8.6, "Ayn Thor", fontsize=15, weight="bold")
box(0.3, 4.6, 6.4, 3.8, "", "#1e1e1e")
ax.text(3.5, 8.15, "TOP SCREEN: lightweight browser (WebView)", ha="center", color="white", fontsize=11, weight="bold")
box(0.6, 6.4, 5.8, 1.4, "any streaming site\n(video plays, NO subtitles drawn on it)", "#5d6d7e", size=10)
box(0.6, 4.85, 2.7, 1.25, "injected script:\nreads video time\n+ page title", "#f9e79f", size=9)
box(3.6, 4.85, 2.8, 1.25, "ad / popup blocker", "#d5dbdb", size=9)

box(1.0, 0.3, 5.2, 3.6, "", "#1e1e1e")
ax.text(3.6, 3.6, "BOTTOM SCREEN: subtitle + study panel", ha="center", color="white", fontsize=11, weight="bold")
box(1.2, 2.55, 4.8, 0.8, "subtitle line:  今日は いい 天気 だね", "#fdfefe", size=11)
box(1.2, 1.65, 4.8, 0.7, "sentence translation (offline model)", "#d6eaf8", size=10)
box(1.2, 0.5, 2.6, 0.95, "tap a word ->\ndictionary popup", "#d5f5e3", size=9)
box(3.95, 0.5, 2.05, 0.95, "[+ Anki]\n[< prev] [next >]\n[offset -/+]", "#fadbd8", size=9)

box(8.2, 6.6, 4.4, 1.6, "SUBTITLE FINDER\n1. guess anime + episode from page title\n2. you confirm / pick\n3. download Japanese .srt/.ass\n   (Jimaku / kitsunekko mirror)", "#e8daef", size=9)
box(8.2, 4.4, 4.4, 1.6, "SYNC ENGINE\nvideo time (+ your offset)\n-> which subtitle line is now", "#fcf3cf", size=10)
box(8.2, 2.2, 4.4, 1.6, "REUSED FROM PLAYTRANSLATE (fork)\nYomitan dictionaries, offline translate,\ndual-screen UI, AnkiDroid export", "#d4efdf", size=9)
box(8.2, 0.3, 4.4, 1.4, "AnkiDroid\n(card: word, reading, meaning,\nsentence, translation, screenshot)", "#fdebd0", size=9)

arrow(6.7, 7.1, 8.2, 7.3, "page title")
arrow(3.3, 5.2, 8.2, 5.0, "currentTime", "#b7950b")
arrow(10.4, 6.6, 10.4, 6.0, "subtitle file", "#7d3c98")
arrow(8.2, 4.7, 6.0, 3.0, "current line", "#b7950b")
arrow(8.2, 2.9, 6.0, 1.9, "", "#1e8449")
arrow(6.0, 0.9, 8.2, 0.9, "add card", "#ca6f1e")

fig.savefig(sys.argv[1], dpi=110, bbox_inches="tight")
