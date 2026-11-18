import sys
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Polygon

plt.rcParams["font.family"] = ["Yu Gothic", "Meiryo", "DejaVu Sans"]
plt.rcParams["font.serif"] = ["Yu Mincho", "MS Mincho", "DejaVu Serif"]

BACKGROUND_TOP = np.array([0x2A, 0x24, 0x20]) / 255
BACKGROUND_BOTTOM = np.array([0x14, 0x13, 0x12]) / 255
ORANGE = "#D97757"
INK = "#ECE8E0"
MUTED = "#9C968A"
CARD = "#2A2926"
GOOD = "#7FC48D"


def rounded_box(ax, left, bottom, width, height, radius, color, alpha=1.0, edge="none", layer=5):
    ax.add_patch(FancyBboxPatch((left, bottom), width, height, boxstyle=f"round,pad=0,rounding_size={radius}",
                                facecolor=color, edgecolor=edge, linewidth=2, alpha=alpha, zorder=layer))


def draw_background(ax, width, height):
    rows = np.linspace(0, 1, height)[:, None, None]
    gradient = BACKGROUND_BOTTOM * (1 - rows) + BACKGROUND_TOP * rows
    image = np.repeat(gradient, width, axis=1)
    ax.imshow(image, extent=(0, width, 0, height), origin="lower", zorder=0)
    glow_x, glow_y = np.meshgrid(np.linspace(0, 1, width // 4), np.linspace(0, 1, height // 4))
    glow = np.exp(-(((glow_x - 0.72) / 0.35) ** 2 + ((glow_y - 0.55) / 0.6) ** 2))
    glow_image = np.zeros((height // 4, width // 4, 4))
    glow_image[..., :3] = np.array([0xD9, 0x77, 0x57]) / 255
    glow_image[..., 3] = glow * 0.22
    ax.imshow(glow_image, extent=(0, width, 0, height), origin="lower", zorder=1)


def draw_icon(ax, left, bottom, size):
    rounded_box(ax, left, bottom, size, size, size * 0.24, ORANGE)
    unit = size / 108

    def point(x, y):
        return (left + x * unit, bottom + (108 - y) * unit)

    ax.add_patch(Polygon([point(41, 26), point(41, 58), point(68, 42)], closed=True,
                         facecolor="white", edgecolor="white", linewidth=size * 0.05, joinstyle="round", zorder=6))
    rounded_box(ax, left + 30 * unit, bottom + (108 - 75) * unit, 48 * unit, 7 * unit, 3.5 * unit, "white", layer=6)
    rounded_box(ax, left + 39 * unit, bottom + (108 - 87) * unit, 30 * unit, 7 * unit, 3.5 * unit, "white", alpha=0.6, layer=6)


def draw_scene(ax, left, bottom, width, height):
    columns = np.linspace(0, 1, 200)[None, :, None]
    rows = np.linspace(0, 1, 120)[:, None, None]
    sky = np.array([0.98, 0.72, 0.55]) * rows + np.array([0.36, 0.42, 0.62]) * (1 - rows)
    sky = sky + 0 * columns
    ax.imshow(sky, extent=(left, left + width, bottom, bottom + height), origin="lower", zorder=6)
    hill_x = np.linspace(left, left + width, 100)
    far_hill = bottom + height * (0.32 + 0.08 * np.sin(np.linspace(0, 3.2, 100)))
    near_hill = bottom + height * (0.18 + 0.06 * np.cos(np.linspace(0.5, 4.5, 100)))
    ax.fill_between(hill_x, bottom, far_hill, color="#5A4A6A", zorder=6)
    ax.fill_between(hill_x, bottom, near_hill, color="#3A3048", zorder=6)
    ax.add_patch(plt.Circle((left + width * 0.75, bottom + height * 0.68), height * 0.09, color="#FFE2C4", zorder=6))


def draw_device(ax, left, bottom, scale):
    top_width, top_height = 520 * scale, 300 * scale
    bottom_width, bottom_height = 340 * scale, 250 * scale
    gap = 26 * scale
    top_left = left
    top_bottom = bottom + bottom_height + gap
    rounded_box(ax, top_left - 14 * scale, top_bottom - 14 * scale, top_width + 28 * scale, top_height + 28 * scale,
                26 * scale, "#050505", edge="#3A3833")
    draw_scene(ax, top_left, top_bottom, top_width, top_height)
    rounded_box(ax, top_left + top_width * 0.12, top_bottom + 22 * scale, top_width * 0.76, 58 * scale, 10 * scale,
                "black", alpha=0.95, layer=7)
    ax.text(top_left + top_width / 2, top_bottom + 51 * scale, "魔法を集めるのが好きなんだ。", color="white",
            fontsize=21 * scale, weight="bold", ha="center", va="center", zorder=8)

    bottom_left = left + (top_width - bottom_width) / 2
    rounded_box(ax, bottom_left - 14 * scale, bottom - 14 * scale, bottom_width + 28 * scale, bottom_height + 28 * scale,
                26 * scale, "#050505", edge="#3A3833")
    rounded_box(ax, bottom_left, bottom, bottom_width, bottom_height, 12 * scale, "#1F1E1C")
    rounded_box(ax, bottom_left + 16 * scale, bottom + bottom_height - 74 * scale, bottom_width - 32 * scale, 56 * scale,
                10 * scale, CARD, layer=6)
    ax.text(bottom_left + 30 * scale, bottom + bottom_height - 46 * scale, "集める  あつめる", color=INK,
            fontsize=17 * scale, weight="bold", va="center", family="serif", zorder=8)
    rounded_box(ax, bottom_left + bottom_width - 82 * scale, bottom + bottom_height - 58 * scale, 52 * scale, 24 * scale,
                12 * scale, "#4F8FBF", layer=7)
    ax.text(bottom_left + bottom_width - 56 * scale, bottom + bottom_height - 46 * scale, "N4", color="white",
            fontsize=12 * scale, weight="bold", ha="center", va="center", zorder=8)
    ax.text(bottom_left + 30 * scale, bottom + bottom_height - 104 * scale, "to collect, to gather", color=MUTED,
            fontsize=13 * scale, va="center", zorder=8)
    rounded_box(ax, bottom_left + bottom_width - 122 * scale, bottom + 22 * scale, 100 * scale, 36 * scale,
                18 * scale, ORANGE, layer=7)
    ax.text(bottom_left + bottom_width - 72 * scale, bottom + 40 * scale, "+ Anki", color="white",
            fontsize=13 * scale, weight="bold", ha="center", va="center", zorder=8)


def draw_title(ax, left, center_y, scale):
    icon_size = 150 * scale
    draw_icon(ax, left, center_y + 40 * scale, icon_size)
    ax.text(left + icon_size + 34 * scale, center_y + 40 * scale + icon_size / 2, "Aynime", color=INK,
            fontsize=58 * scale, weight="bold", family="serif", va="center", zorder=6)
    ax.text(left, center_y - 20 * scale, "Learn Japanese with anime", color=INK, fontsize=26 * scale,
            va="center", zorder=6)
    ax.text(left, center_y - 70 * scale, "アニメで日本語を学ぼう", color=ORANGE, fontsize=24 * scale,
            va="center", family="serif", weight="bold", zorder=6)
    ax.text(left, center_y - 128 * scale, "Japanese subtitles on top  ·  PlayTranslate below", color=MUTED,
            fontsize=16 * scale, va="center", zorder=6)


def render(width, height, output_path):
    figure = plt.figure(figsize=(width / 100, height / 100), dpi=100)
    ax = figure.add_axes((0, 0, 1, 1))
    ax.set_xlim(0, width)
    ax.set_ylim(0, height)
    ax.axis("off")
    draw_background(ax, width, height)
    is_wide = width / height > 2
    scale = height / 620 if is_wide else 1.2
    title_left = width * 0.06
    draw_title(ax, title_left, height * 0.42, scale)
    device_left = width * (0.56 if is_wide else 0.6)
    device_bottom = (height - (300 + 250 + 26) * scale) / 2
    draw_device(ax, device_left, device_bottom, scale)
    figure.savefig(output_path, dpi=100)
    plt.close(figure)


if __name__ == "__main__":
    render(1920, 620, sys.argv[1])
    render(1920, 1080, sys.argv[2])
