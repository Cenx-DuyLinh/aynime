import sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Rectangle

plt.rcParams["font.family"] = ["Yu Gothic", "Meiryo", "DejaVu Sans"]

fig, ax = plt.subplots(figsize=(12, 10))
ax.set_xlim(0, 12)
ax.set_ylim(0, 10)
ax.axis("off")
fig.patch.set_facecolor("#0E0E0D")

def rounded(x, y, w, h, face, edge="none", lw=1.2, radius=0.15):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle=f"round,pad=0,rounding_size={radius}",
                                facecolor=face, edgecolor=edge, linewidth=lw))

def label(x, y, text, size=10, color="#ECE8E0", weight="normal", ha="left"):
    ax.text(x, y, text, fontsize=size, color=color, weight=weight, ha=ha, va="center")

label(0.3, 9.7, "TOP SCREEN: our browser app", 12, "#A99BFF", "bold")
rounded(0.3, 5.2, 7.6, 4.2, "#000000", radius=0.3)
rounded(0.5, 5.4, 7.2, 3.8, "#45423C", radius=0.05)
label(4.1, 7.8, "video (full size)", 11, "#ECE8E0", "bold", "center")
rounded(0.9, 5.55, 6.4, 0.6, "#000000", radius=0.08)
label(4.1, 6.42, "(site's own English subtitles hidden underneath)", 8, "#9C968A", ha="center")
label(4.1, 5.85, "魔法を集めるのが好きなんだ", 15, "#FFFFFF", "bold", "center")
ax.add_patch(Rectangle((0.82, 5.47), 6.56, 0.76, fill=False, edgecolor="#E0A43A", linewidth=2.2, linestyle="--"))

label(8.2, 6.4, "subtitle band (overlay)", 11, "#ECE8E0", "bold")
label(8.2, 6.0, "drawn by our app, clear font,\nhigh contrast -> easy to read", 9, "#9C968A")
label(8.2, 5.25, "capture region", 11, "#E0A43A", "bold")
label(8.2, 4.85, "set once in PlayTranslate\n(auto mode watches it)", 9, "#9C968A")

label(2.0, 4.6, "BOTTOM SCREEN: the real PlayTranslate (unchanged)", 12, "#A99BFF", "bold")
rounded(2.0, 0.3, 5.2, 4.0, "#000000", radius=0.3)
rounded(2.2, 0.5, 4.8, 3.6, "#1F1E1C", radius=0.2)
rounded(2.4, 3.1, 4.4, 0.8, "#2A2926", "#3A3833")
label(4.6, 3.5, "魔法を集めるのが好きなんだ", 12, "#ECE8E0", "bold", "center")
rounded(2.4, 2.25, 4.4, 0.65, "#2A2926", "#3A3833")
label(4.6, 2.57, "translation (your 4 GB model)", 9, "#9C968A", ha="center")
rounded(2.4, 0.7, 4.4, 1.35, "#2A2926", "#D97757", lw=1.5)
label(4.6, 1.55, "tap word -> dictionary", 10, "#ECE8E0", ha="center")
label(4.6, 1.05, "+ Anki", 10, "#D97757", "bold", "center")

label(7.8, 3.2, "our app does:", 10, "#ECE8E0", "bold")
label(7.8, 2.4, "- browser + saved sites\n- find & download subtitles\n- auto-sync to the audio\n- draw the subtitle strip", 9, "#9C968A")
label(7.8, 1.2, "PlayTranslate does:", 10, "#ECE8E0", "bold")
label(7.8, 0.65, "- read strip, translate,\n  dictionary, Anki", 9, "#9C968A")

fig.savefig(sys.argv[1], dpi=100, bbox_inches="tight", facecolor=fig.get_facecolor())
