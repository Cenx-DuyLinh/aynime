import sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

plt.rcParams["font.family"] = ["Yu Gothic", "Meiryo", "MS Gothic", "DejaVu Sans"]
plt.rcParams["font.serif"] = ["Yu Mincho", "MS Mincho", "DejaVu Serif"]

CREAM = "#F4F1EA"
CARD = "#FFFFFF"
INK = "#2B2A27"
MUTED = "#8A8578"
ACCENT = "#C96442"
LEVEL_COLORS = {"N5": "#6BAA75", "N4": "#4F8FBF", "N3": "#E0A43A", "N2": "#C96442", "N1": "#8E5BA8"}

fig, ax = plt.subplots(figsize=(11, 13))
ax.set_xlim(0, 11)
ax.set_ylim(0, 13)
ax.axis("off")
fig.patch.set_facecolor("#DDD8CC")

def rounded(x, y, w, h, face, edge="none", radius=0.18, lw=1.2):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle=f"round,pad=0,rounding_size={radius}",
                                facecolor=face, edgecolor=edge, linewidth=lw))

def label(x, y, text, size=10, color=INK, weight="normal", ha="left", family=None):
    ax.text(x, y, text, fontsize=size, color=color, weight=weight, ha=ha, va="center", family=family)

def chip(x, y, text, color):
    rounded(x, y - 0.17, 0.62, 0.34, color, radius=0.15)
    label(x + 0.31, y, text, 8, "white", "bold", "center")

def callout(x, y, text):
    label(x, y, text, 9, "#5A4FCF", "bold")

rounded(0.4, 6.9, 10.2, 5.8, "#111111", radius=0.35)
rounded(0.6, 7.1, 9.8, 5.4, CREAM, radius=0.25)
label(0.9, 12.15, "Start", 18, INK, "bold", family="serif")
rounded(0.9, 11.25, 9.2, 0.55, CARD, "#E2DDD2", radius=0.27)
label(1.2, 11.52, "Search or type a site address", 10, MUTED)
label(0.9, 10.75, "Saved sites", 11, INK, "bold")
for column, name in enumerate(["Site A", "Site B", "Site C", "+ Add"]):
    x = 0.9 + column * 2.35
    rounded(x, 9.15, 2.1, 1.35, CARD, "#E2DDD2")
    rounded(x + 0.75, 9.85, 0.6, 0.5, ACCENT if name != "+ Add" else "#E2DDD2", radius=0.12)
    label(x + 1.05, 9.45, name, 9, INK, ha="center")
label(0.9, 8.75, "Continue watching", 11, INK, "bold")
rounded(0.9, 7.35, 9.2, 1.1, CARD, "#E2DDD2")
rounded(1.05, 7.5, 1.4, 0.8, "#CFC8B8", radius=0.1)
label(2.7, 8.1, "Frieren  -  Episode 5", 10, INK, "bold")
label(2.7, 7.7, "Japanese subtitles ready  |  synced", 9, MUTED)
rounded(2.7, 7.42, 6.6, 0.06, "#E2DDD2", radius=0.03)
rounded(2.7, 7.42, 3.9, 0.06, ACCENT, radius=0.03)
callout(0.6, 12.85, "TOP SCREEN (home page of the browser)")

rounded(1.7, 0.3, 7.6, 6.1, "#111111", radius=0.35)
rounded(1.9, 0.5, 7.2, 5.7, CREAM, radius=0.25)
callout(1.9, 6.6, "BOTTOM SCREEN (while watching)")
rounded(6.85, 5.62, 2.05, 0.4, "#E6F2E8", radius=0.2)
label(7.875, 5.82, "auto-synced  +1.2s", 8, "#3F7A4A", "bold", "center")
label(2.15, 5.82, "12:41", 9, MUTED)
rounded(2.1, 4.25, 6.8, 1.2, CARD, "#E2DDD2")
label(2.35, 5.05, "ふりがな", 7, MUTED)
label(2.35, 4.7, "魔法を 集める のが 好き なんだ", 17, INK, "bold", family=None)
rounded(3.6, 4.42, 0.95, 0.05, ACCENT, radius=0.02)
label(2.15, 3.95, "\"I just like collecting spells.\"   (offline translation)", 9, MUTED)
rounded(2.1, 1.55, 6.8, 2.1, CARD, ACCENT, lw=1.6)
label(2.35, 3.3, "集める", 16, INK, "bold", family="serif")
label(3.6, 3.3, "あつめる", 10, MUTED)
chip(5.0, 3.3, "N4", LEVEL_COLORS["N4"])
chip(5.75, 3.3, "freq 1.2k", "#9A9384")
label(2.35, 2.75, "1. to collect, to gather", 10)
label(2.35, 2.4, "2. to attract (attention)", 10)
label(2.35, 2.0, "pitch:  あ⁀つめる  [0]", 9, MUTED)
rounded(7.55, 1.75, 1.2, 0.45, ACCENT, radius=0.22)
label(8.15, 1.975, "+ Anki", 9, "white", "bold", "center")
for column, text in enumerate(["< line", "replay", "line >", "sync"]):
    x = 2.1 + column * 1.75
    rounded(x, 0.7, 1.55, 0.55, CARD, "#E2DDD2", radius=0.27)
    label(x + 0.775, 0.975, text, 9, INK, ha="center")

fig.savefig(sys.argv[1], dpi=100, bbox_inches="tight", facecolor=fig.get_facecolor())
