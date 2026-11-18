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
GOOD = "#7FC48D"
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

def pill(x, y, w, text, face, text_color):
    rounded(x, y - 0.2, w, 0.4, face, radius=0.2)
    label(x + w / 2, y, text, 9, text_color, "bold", "center")

def anime_card(y, title_jp, title_en, details, subtitle_note, subtitle_color):
    rounded(0.7, y, 9.3, 1.75, CARD, BORDER)
    rounded(0.85, y + 0.15, 1.05, 1.45, "#45423C", radius=0.08)
    label(1.375, y + 0.875, "cover", 8, MUTED, ha="center")
    label(2.1, y + 1.4, title_jp, 13, INK, "bold", family="serif")
    label(2.1, y + 1.05, title_en, 9, MUTED)
    label(2.1, y + 0.72, details, 9, MUTED)
    label(2.1, y + 0.35, subtitle_note, 9, subtitle_color, "bold")
    pill(6.4, y + 1.3, 1.65, "open on Site A", ACCENT, "white")
    pill(8.2, y + 1.3, 1.65, "open on Site B", "#45423C", INK)

rounded(0.4, 0.3, 9.9, 7.9, "#000000", radius=0.3)
rounded(0.55, 0.45, 9.6, 7.6, BACKGROUND, radius=0.22)

rounded(0.7, 7.3, 9.3, 0.55, CARD, ACCENT, lw=1.5, radius=0.27)
label(1.0, 7.575, "frieren", 11)

label(0.7, 6.85, "Anime", 12, INK, "bold", family="serif")
anime_card(4.9, "葬送のフリーレン", "Frieren: Beyond Journey's End", "TV  |  28 episodes  |  2023",
           "Japanese subtitles: all 28 episodes", GOOD)
anime_card(2.95, "葬送のフリーレン 第2期", "Frieren season 2", "TV  |  airing",
           "Japanese subtitles: up to episode 4", "#E0A43A")

rounded(0.7, 1.75, 9.3, 0.9, CARD, BORDER)
label(1.0, 2.2, "Search the web for \"frieren\"", 10, INK)
label(9.7, 2.2, "DuckDuckGo  >", 9, MUTED, ha="right")
rounded(0.7, 0.7, 9.3, 0.8, CARD, BORDER)
label(1.0, 1.1, "Typed an address?  example-site.com  ->  opens it directly", 9, MUTED)

callouts = [
    (7.575, "1. search bar: anime name, or a site address"),
    (6.2, "2. anime results (from AniList)\n   tap 'open on Site A' = that site's\n   own search page for this anime"),
    (5.2, "3. subtitle check (from Jimaku)\n   before you even start watching"),
    (2.2, "4. normal web search, if you\n   need it (engine you choose)"),
    (1.1, "5. addresses skip all this"),
]
for y, text in callouts:
    label(10.6, y, text, 9.5, CALLOUT, "bold")

fig.savefig(sys.argv[1], dpi=100, bbox_inches="tight", facecolor=fig.get_facecolor())
