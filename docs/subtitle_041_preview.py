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
BAND_COLOR = np.array([0x14, 0x12, 0x10]) / 255
DARKNESS = 0.80
BOTTOM_MARGIN = 0
BAND_HEIGHT = SCREEN_HEIGHT * 0.15
BAND_WIDTH = SCREEN_WIDTH * 0.86
SOFT_EDGE_FRACTION = 0.16
TEXT_SIZE = 31
LINE_STEP = 40


def draw_scene(ax):
    rows = np.linspace(0, 1, 160)[:, None, None]
    sky = np.array([0.98, 0.80, 0.62]) * (1 - rows) + np.array([0.45, 0.62, 0.90]) * rows
    ax.imshow(np.repeat(sky, 4, axis=1), extent=(0, SCREEN_WIDTH, 0, SCREEN_HEIGHT), origin="lower", aspect="auto", zorder=0)
    ax.add_patch(plt.Rectangle((300, 150), 360, 210, color="#D9C2A0", zorder=1))
    for column in range(5):
        ax.add_patch(plt.Rectangle((330 + column * 66, 260), 34, 52, color="#6F86B0", zorder=1))
    ax.add_patch(plt.Polygon([(285, 360), (480, 450), (675, 360)], color="#8C4F4F", zorder=1))
    hill_x = np.linspace(0, SCREEN_WIDTH, 80)
    ax.fill_between(hill_x, 0, 150 + 18 * np.sin(np.linspace(0, 5, 80)), color="#5C9A4A", zorder=1)
    ax.fill_between(hill_x, 0, 95 + 12 * np.cos(np.linspace(0, 4, 80)), color="#4A8040", zorder=1)


def draw_burned_in_english(ax):
    ax.text(SCREEN_WIDTH / 2, BOTTOM_MARGIN + 46, "Yes, probably.\nAt least that's what I heard.", color="white", fontsize=17,
            ha="center", va="center", zorder=2, linespacing=1.3,
            path_effects=[path_effects.withStroke(linewidth=3, foreground="black")])


def band_image(soft_edges):
    columns = np.linspace(0, 1, 400)
    if soft_edges:
        alpha = np.clip(np.minimum(columns, 1 - columns) / SOFT_EDGE_FRACTION, 0, 1) * DARKNESS
    else:
        alpha = np.full(400, DARKNESS)
    image = np.zeros((1, 400, 4))
    image[..., :3] = BAND_COLOR
    image[0, :, 3] = alpha
    return image


def band_height_for(text):
    line_count = len(text.split(chr(10)))
    return max(BAND_HEIGHT, line_count * LINE_STEP + 26)


def draw_band(ax, soft_edges, band_height):
    left = (SCREEN_WIDTH - BAND_WIDTH) / 2
    if soft_edges:
        ax.imshow(band_image(True), extent=(left, left + BAND_WIDTH, BOTTOM_MARGIN, BOTTOM_MARGIN + band_height),
                  aspect="auto", zorder=3)
    else:
        ax.add_patch(FancyBboxPatch((left, BOTTOM_MARGIN), BAND_WIDTH, band_height, boxstyle="round,pad=0,rounding_size=18",
                                    facecolor=BAND_COLOR, alpha=DARKNESS, edgecolor="none", zorder=3))


def draw_japanese_line(ax, text):
    bottom_line_center = BOTTOM_MARGIN + band_height_for(text) / 2 - (len(text.split(chr(10))) - 1) * LINE_STEP / 2
    lines = text.split("\n")
    for index, line in enumerate(reversed(lines)):
        ax.text(SCREEN_WIDTH / 2, bottom_line_center + index * LINE_STEP, line, color="#FFFDF8", fontsize=TEXT_SIZE,
                weight="bold", ha="center", va="center", zorder=4,
                path_effects=[path_effects.withSimplePatchShadow(offset=(1.5, -1.5), shadow_rgbFace="black", alpha=0.6)])


def draw_screen(ax, title, soft_edges, japanese_text):
    ax.set_xlim(0, SCREEN_WIDTH)
    ax.set_ylim(0, SCREEN_HEIGHT)
    ax.axis("off")
    draw_scene(ax)
    draw_burned_in_english(ax)
    draw_band(ax, soft_edges, band_height_for(japanese_text))
    draw_japanese_line(ax, japanese_text)
    ax.set_xlim(0, SCREEN_WIDTH)
    ax.set_ylim(0, SCREEN_HEIGHT)
    ax.set_aspect("equal")
    ax.set_title(title, color="#ECE8E0", fontsize=12)


def main(output_path):
    figure, axes = plt.subplots(1, 3, figsize=(21, 4.8))
    figure.patch.set_facecolor("#0E0E0D")
    draw_screen(axes[0], "0.4.1: 15% of screen, 80%, one line", True, "ええ、たぶんね。")
    draw_screen(axes[1], "0.4.1: two lines (band grows)", True, "少なくとも\n私はそう聞いたよ。")
    draw_screen(axes[2], "0.4.1: rounded box option", False, "ええ、たぶんね。")
    figure.savefig(output_path, dpi=100, bbox_inches="tight", facecolor=figure.get_facecolor())


main(sys.argv[1])
