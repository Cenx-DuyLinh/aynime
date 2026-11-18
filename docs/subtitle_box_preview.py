import sys
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patheffects as path_effects
from matplotlib.patches import FancyBboxPatch

plt.rcParams["font.family"] = ["Yu Gothic", "Meiryo", "DejaVu Sans"]

SCREEN_WIDTH = 960
SCREEN_HEIGHT = 540
JAPANESE_LINE = "ええ、たぶんね。"
ENGLISH_LINES = "Yes, probably.\nAt least that's what I heard."


def draw_scene(ax):
    rows = np.linspace(0, 1, 120)[:, None, None]
    sky = np.array([0.55, 0.75, 0.95]) * rows + np.array([0.35, 0.62, 0.35]) * (1 - rows)
    image = np.repeat(sky, 200, axis=1)
    ax.imshow(image, extent=(0, SCREEN_WIDTH, 0, SCREEN_HEIGHT), origin="lower", zorder=0)
    ax.add_patch(plt.Rectangle((250, 150), 460, 230, color="#C9A27E", zorder=1))
    ax.add_patch(plt.Rectangle((250, 380), 460, 40, color="#5A6E9A", zorder=1))
    ax.fill_between(np.linspace(0, SCREEN_WIDTH, 50), 0, 120 + 15 * np.sin(np.linspace(0, 6, 50)), color="#4E8A3E", zorder=1)


def draw_english_hardsub(ax):
    ax.text(SCREEN_WIDTH / 2, 92, ENGLISH_LINES, color="white", fontsize=17, ha="center", va="center", zorder=2,
            path_effects=[path_effects.withStroke(linewidth=3, foreground="black")])


def draw_box(ax, bottom, width, height, color, alpha, radius):
    left = (SCREEN_WIDTH - width) / 2
    ax.add_patch(FancyBboxPatch((left, bottom), width, height, boxstyle=f"round,pad=0,rounding_size={radius}",
                                facecolor=color, alpha=alpha, edgecolor="none", zorder=3))
    return bottom + height / 2


def draw_old_style(ax):
    draw_scene(ax)
    draw_english_hardsub(ax)
    center = draw_box(ax, bottom=SCREEN_HEIGHT * 0.12, width=SCREEN_WIDTH * 0.8, height=58, color="black", alpha=0.92, radius=8)
    ax.text(SCREEN_WIDTH / 2, center, JAPANESE_LINE, color="white", fontsize=24, weight="bold", ha="center", va="center", zorder=4)


def draw_new_style(ax):
    draw_scene(ax)
    draw_english_hardsub(ax)
    center = draw_box(ax, bottom=SCREEN_HEIGHT * 0.10, width=SCREEN_WIDTH * 0.72, height=96, color="#141210", alpha=0.75, radius=18)
    ax.text(SCREEN_WIDTH / 2, center, JAPANESE_LINE, color="#FFFDF8", fontsize=25, weight="bold", ha="center", va="center", zorder=4,
            path_effects=[path_effects.withSimplePatchShadow(offset=(1.5, -1.5), shadow_rgbFace="black", alpha=0.6)])


def draw_soft_band(ax):
    draw_scene(ax)
    draw_english_hardsub(ax)
    band_bottom = SCREEN_HEIGHT * 0.10
    band_height = 96
    band_width = SCREEN_WIDTH * 0.86
    left = (SCREEN_WIDTH - band_width) / 2
    columns = np.linspace(0, 1, 400)
    fade = np.clip(np.minimum(columns, 1 - columns) / 0.16, 0, 1)
    band = np.zeros((1, 400, 4))
    band[..., :3] = np.array([0x14, 0x12, 0x10]) / 255
    band[0, :, 3] = fade
    ax.imshow(band, extent=(left, left + band_width, band_bottom, band_bottom + band_height), aspect="auto", zorder=3)
    ax.text(SCREEN_WIDTH / 2, band_bottom + band_height / 2, JAPANESE_LINE, color="#FFFDF8", fontsize=25, weight="bold",
            ha="center", va="center", zorder=4,
            path_effects=[path_effects.withSimplePatchShadow(offset=(1.5, -1.5), shadow_rgbFace="black", alpha=0.6)])


def main(output_path):
    figure, axes = plt.subplots(1, 3, figsize=(20, 4.6))
    figure.patch.set_facecolor("#0E0E0D")
    for ax, title, draw in [(axes[0], "before: solid black, 1 line, 12% up", draw_old_style),
                            (axes[1], "75% see-through box (English shows through)", draw_new_style),
                            (axes[2], "soft band: solid middle, faded sides (my pick)", draw_soft_band)]:
        ax.set_xlim(0, SCREEN_WIDTH)
        ax.set_ylim(0, SCREEN_HEIGHT)
        ax.set_aspect("equal")
        ax.axis("off")
        draw(ax)
        ax.set_xlim(0, SCREEN_WIDTH)
        ax.set_ylim(0, SCREEN_HEIGHT)
        ax.set_aspect("equal")
        ax.set_title(title, color="#ECE8E0", fontsize=12)
    figure.savefig(output_path, dpi=100, bbox_inches="tight", facecolor=figure.get_facecolor())


main(sys.argv[1])
