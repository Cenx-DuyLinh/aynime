import sys
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patheffects as path_effects
from matplotlib.patches import FancyBboxPatch, Circle, Polygon

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
SCREEN_WIDTH = 960
SCREEN_HEIGHT = 540


def rounded(ax, x, y, w, h, face, edge="none", radius=8, alpha=1.0, lw=1.2):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle=f"round,pad=0,rounding_size={radius}",
                                facecolor=face, edgecolor=edge, linewidth=lw, alpha=alpha))


def label(ax, x, y, text, size=10, color=INK, weight="normal", ha="left", family=None):
    ax.text(x, y, text, fontsize=size, color=color, weight=weight, ha=ha, va="center", family=family)


def draw_video(ax, top):
    rows = np.linspace(0, 1, 120)[:, None, None]
    sky = np.array([0.40, 0.62, 0.92]) * rows + np.array([0.98, 0.82, 0.65]) * (1 - rows)
    ax.imshow(np.repeat(sky, 4, axis=1), extent=(0, SCREEN_WIDTH, 0, top), origin="lower", aspect="auto", zorder=0)
    hill_x = np.linspace(0, SCREEN_WIDTH, 60)
    ax.fill_between(hill_x, 0, top * 0.30 + 14 * np.sin(np.linspace(0, 5, 60)), color="#5C9A4A", zorder=1)
    ax.add_patch(plt.Rectangle((330, top * 0.30), 300, top * 0.38, color="#D9C2A0", zorder=1))
    ax.add_patch(Polygon([(315, top * 0.68), (480, top * 0.86), (645, top * 0.68)], color="#8C4F4F", zorder=1))


def draw_band(ax, text):
    band = np.zeros((1, 300, 4))
    columns = np.linspace(0, 1, 300)
    band[..., :3] = np.array([0x14, 0x12, 0x10]) / 255
    band[0, :, 3] = np.clip(np.minimum(columns, 1 - columns) / 0.16, 0, 1) * 0.8
    ax.imshow(band, extent=(70, SCREEN_WIDTH - 70, 0, 78), aspect="auto", zorder=3)
    ax.text(SCREEN_WIDTH / 2, 39, text, color="#FFFDF8", fontsize=20, weight="bold", ha="center", va="center", zorder=4,
            path_effects=[path_effects.withSimplePatchShadow(offset=(1.2, -1.2), shadow_rgbFace="black", alpha=0.6)])


def draw_before(ax):
    ax.set_xlim(0, SCREEN_WIDTH)
    ax.set_ylim(0, SCREEN_HEIGHT)
    ax.axis("off")
    draw_video(ax, SCREEN_HEIGHT - 86)
    ax.add_patch(plt.Rectangle((0, SCREEN_HEIGHT - 50), SCREEN_WIDTH, 50, color=BACKGROUND, zorder=5))
    for x, glyph in [(22, "←"), (56, "⌂"), (90, "⟳")]:
        label(ax, x, SCREEN_HEIGHT - 25, glyph, 15)
    rounded(ax, 115, SCREEN_HEIGHT - 41, 560, 32, CARD, BORDER, radius=16)
    label(ax, 132, SCREEN_HEIGHT - 25, "9anime.or.at/sasaki-to-pii-chan-season-2-episode-2/", 9)
    rounded(ax, 690, SCREEN_HEIGHT - 37, 82, 24, "#24362A", radius=12)
    label(ax, 731, SCREEN_HEIGHT - 25, "18 blocked", 8, GOOD, ha="center")
    label(ax, 800, SCREEN_HEIGHT - 25, "☆", 15)
    label(ax, 835, SCREEN_HEIGHT - 25, "⋮", 15)
    ax.add_patch(plt.Rectangle((0, SCREEN_HEIGHT - 86), SCREEN_WIDTH, 36, color=CARD, zorder=5))
    label(ax, 12, SCREEN_HEIGHT - 68, "Subtitles: Sasaki to Pii-chan Season 2 · ep 2 (376 lines)", 8)
    label(ax, 385, SCREEN_HEIGHT - 68, "offset +7.0 s", 8, GOOD)
    for index, text in enumerate(["Sync", "−0.1", "+0.1", "↻", "Subs", "Aa"]):
        rounded(ax, 470 + index * 78, SCREEN_HEIGHT - 80, 68, 24, BACKGROUND, BORDER, radius=12)
        label(ax, 504 + index * 78, SCREEN_HEIGHT - 68, text, 8, ha="center")
    for artist in ax.texts:
        artist.set_zorder(6)
    draw_band(ax, "うん 元気そうで何よりだ")
    ax.set_title("Now: text glyphs, long address, crowded subtitle bar always on", color=INK, fontsize=12)


def icon_button(ax, x, y, glyph, active=False):
    ax.add_patch(Circle((x, y), 15, color=ACCENT if active else CARD, zorder=6))
    label(ax, x, y, glyph, 11, "white" if active else INK, ha="center")


def draw_after(ax):
    ax.set_xlim(0, SCREEN_WIDTH)
    ax.set_ylim(0, SCREEN_HEIGHT)
    ax.axis("off")
    draw_video(ax, SCREEN_HEIGHT)
    rounded(ax, 14, SCREEN_HEIGHT - 52, SCREEN_WIDTH - 28, 40, "#151413", radius=20, alpha=0.88)
    for x, glyph in [(40, "‹"), (76, "⌂"), (112, "↻")]:
        icon_button(ax, x, SCREEN_HEIGHT - 32, glyph)
    rounded(ax, 140, SCREEN_HEIGHT - 47, 470, 30, CARD, radius=15)
    ax.add_patch(Circle((158, SCREEN_HEIGHT - 32), 7, color=ACCENT, zorder=6))
    label(ax, 172, SCREEN_HEIGHT - 32, "9anime  ·  Sasaki to Pii-chan S2 · Ep 2", 9)
    rounded(ax, 622, SCREEN_HEIGHT - 44, 66, 24, "#24362A", radius=12)
    label(ax, 655, SCREEN_HEIGHT - 32, "⛨ 18", 8, GOOD, ha="center")
    icon_button(ax, 712, SCREEN_HEIGHT - 32, "★", active=True)
    icon_button(ax, 748, SCREEN_HEIGHT - 32, "⋯")

    rounded(ax, 200, 96, 560, 44, "#151413", radius=22, alpha=0.9)
    ax.add_patch(Circle((226, 118), 6, color=GOOD, zorder=6))
    label(ax, 240, 118, "S2 · Ep 2", 9)
    rounded(ax, 320, 104, 150, 28, CARD, radius=14)
    label(ax, 345, 118, "−", 12, ha="center")
    label(ax, 395, 118, "+7.0 s", 10, GOOD, "bold", ha="center")
    label(ax, 445, 118, "+", 12, ha="center")
    for x, glyph in [(500, "◎"), (540, "↻"), (580, "字"), (620, "Aa")]:
        icon_button(ax, x, 118, glyph)
    label(ax, 650, 118, "fades away while playing", 8, MUTED)
    for artist in ax.texts:
        artist.set_zorder(7)
    draw_band(ax, "うん 元気そうで何よりだ")
    ax.set_title("Proposal: floating glass bars, icon buttons, offset as one control", color=INK, fontsize=12)


def add_callouts(ax):
    notes = [
        (SCREEN_HEIGHT - 32, "1  floating top bar: real icons,\n    short address (site · anime · ep)"),
        (118, "2  subtitle controls in one pill:\n    status dot, −  offset  +, icons;\n    hides 3 s after play, back on pause"),
        (39, "3  band unchanged (your 80% soft band)"),
    ]
    for y, text in notes:
        ax.text(SCREEN_WIDTH + 20, y, text, color=CALLOUT, fontsize=9, weight="bold", va="center")


def main(output_path):
    figure, axes = plt.subplots(1, 2, figsize=(22, 6.4), gridspec_kw={"width_ratios": [1, 1.28]})
    figure.patch.set_facecolor("#0E0E0D")
    draw_before(axes[0])
    draw_after(axes[1])
    axes[1].set_xlim(0, SCREEN_WIDTH + 300)
    add_callouts(axes[1])
    figure.savefig(output_path, dpi=90, bbox_inches="tight", facecolor=figure.get_facecolor())


main(sys.argv[1])
