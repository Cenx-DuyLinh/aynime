import sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

plt.rcParams["font.family"] = ["Yu Gothic", "Meiryo", "DejaVu Sans"]
plt.rcParams["font.serif"] = ["Yu Mincho", "MS Mincho", "DejaVu Serif"]

BACKGROUND = "#1F1E1C"
CARD = "#2A2926"
BORDER = "#3A3833"
INK = "#ECE8E0"
MUTED = "#9C968A"
ACCENT = "#D97757"
WARNING = "#E0A43A"
CALLOUT = "#A99BFF"

fig, ax = plt.subplots(figsize=(14, 8.6))
ax.set_xlim(0, 14)
ax.set_ylim(0, 8.6)
ax.axis("off")
fig.patch.set_facecolor("#0E0E0D")


def rounded(x, y, w, h, face, edge="none", lw=1.2, radius=0.15):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle=f"round,pad=0,rounding_size={radius}",
                                facecolor=face, edgecolor=edge, linewidth=lw))


def label(x, y, text, size=10, color=INK, weight="normal", ha="left", family=None):
    ax.text(x, y, text, fontsize=size, color=color, weight=weight, ha=ha, va="center", family=family)


def section_heading(y, text):
    label(0.7, y, text, 12, INK, "bold", family="serif")


def empty_result_card(y, text):
    rounded(0.7, y, 9.3, 0.8, CARD, BORDER)
    label(1.0, y + 0.4, text, 10, MUTED)


rounded(0.4, 0.3, 9.9, 7.9, "#000000", radius=0.3)
rounded(0.55, 0.45, 9.6, 7.6, BACKGROUND, radius=0.22)
rounded(0.7, 7.3, 9.3, 0.55, CARD, ACCENT, lw=1.5, radius=0.27)
label(1.0, 7.575, "9anime", 11)

section_heading(6.85, "Your saved sites")
empty_result_card(5.75, 'No saved site matches "9anime"')

section_heading(5.3, "Anime")
empty_result_card(4.2, 'No anime called "9anime"')

section_heading(3.75, "Web")
rounded(0.7, 2.5, 9.3, 0.95, CARD, ACCENT, lw=1.5)
label(1.0, 3.0, 'Search the web for "9anime"', 11, INK, "bold")
label(9.7, 3.0, "DuckDuckGo  >", 9, MUTED, ha="right")

rounded(0.7, 0.7, 9.3, 1.5, "#3A2A22", WARNING, lw=1.2)
label(1.0, 1.8, "Heads-up", 10, WARNING, "bold")
label(1.0, 1.35, "The original 9anime closed in 2024. Sites using its name now", 9, INK)
label(1.0, 1.0, "are copies, and many push scam ads. The blocker stays on.", 9, INK)

callouts = [
    (6.3, "1. saved sites checked first"),
    (5.95, "   (empty here: nothing saved yet)"),
    (4.75, "2. AniList finds no anime"),
    (4.4, "   with that name"),
    (3.15, "3. so the main option is a"),
    (2.8, "   normal web search"),
    (1.6, "4. short warning, because the"),
    (1.25, "   name is a known pirate brand"),
]
for y, text in callouts:
    label(10.6, y, text, 9.5, CALLOUT, "bold")

fig.savefig(sys.argv[1], dpi=100, bbox_inches="tight", facecolor=fig.get_facecolor())
