import sys
from pathlib import Path

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patheffects as path_effects
from matplotlib.patches import Ellipse
from PIL import Image, ImageDraw

plt.rcParams["font.family"] = ["Yu Gothic", "Meiryo", "DejaVu Sans"]
plt.rcParams["font.serif"] = ["Yu Mincho", "MS Mincho", "DejaVu Serif"]

CREAM = "#FFF6E6"
MINT = "#76DEBE"
PERSIAN_GREEN = "#009688"
DEEP_TEAL = "#063A38"
MUTED_MINT = "#9CC9BE"
ICON_FILE_NAME = "aynime_icon_512.png"
EMBEDDED_ICON_CORNER_SHARE = 0.215


def new_canvas(width, height, transparent=False):
    figure = plt.figure(figsize=(width / 100, height / 100), dpi=100)
    if transparent:
        figure.patch.set_alpha(0)
    ax = figure.add_axes((0, 0, 1, 1))
    ax.set_xlim(0, width)
    ax.set_ylim(0, height)
    ax.axis("off")
    if transparent:
        ax.patch.set_alpha(0)
    return figure, ax


def save(figure, output_path, transparent=False):
    figure.savefig(output_path, dpi=100, transparent=transparent)
    plt.close(figure)


def hex_to_rgb(hex_color):
    return np.array([int(hex_color[index:index + 2], 16) for index in (1, 3, 5)]) / 255


def with_rounded_corners(square_icon, corner_share):
    mask = Image.new("L", square_icon.size, 0)
    radius = round(square_icon.width * corner_share)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, square_icon.width - 1, square_icon.height - 1), radius=radius, fill=255)
    rounded = square_icon.copy()
    rounded.putalpha(mask)
    return rounded


def draw_icon(ax, icon_image, left, bottom, size):
    ax.imshow(icon_image, extent=(left, left + size, bottom, bottom + size), zorder=6, interpolation="lanczos")


def draw_vertical_gradient(ax, width, height, bottom_color, top_color):
    rows = np.linspace(0, 1, 256)[:, None, None]
    gradient = hex_to_rgb(bottom_color) * (1 - rows) + hex_to_rgb(top_color) * rows
    ax.imshow(np.repeat(gradient, 4, axis=1), extent=(0, width, 0, height), origin="lower", aspect="auto", zorder=0)


def draw_mint_glow(ax, width, height, center_x_fraction, center_y_fraction, strength):
    glow_x, glow_y = np.meshgrid(np.linspace(0, 1, width // 4), np.linspace(0, 1, height // 4))
    glow = np.exp(-(((glow_x - center_x_fraction) / 0.45) ** 2 + ((glow_y - center_y_fraction) / 0.35) ** 2))
    glow_image = np.zeros((height // 4, width // 4, 4))
    glow_image[..., :3] = hex_to_rgb(MINT)
    glow_image[..., 3] = glow * strength
    ax.imshow(glow_image, extent=(0, width, 0, height), origin="lower", aspect="auto", zorder=1)


def render_logo(icon_image, output_path):
    figure, ax = new_canvas(1200, 360, transparent=True)
    icon_size = 240
    draw_icon(ax, icon_image, 30, 60, icon_size)
    ax.text(30 + icon_size + 50, 60 + icon_size / 2, "Aynime", color=CREAM, fontsize=110, weight="bold",
            family="serif", va="center", zorder=8,
            path_effects=[path_effects.withSimplePatchShadow(offset=(3, -3), shadow_rgbFace="black", alpha=0.5)])
    save(figure, output_path, transparent=True)


def render_hero(output_path):
    width, height = 1920, 1080
    figure, ax = new_canvas(width, height)
    draw_dawn_sky(ax, width, height)
    draw_sun_glow(ax, width / 2, height * 0.52, height * 0.16)
    draw_soft_clouds(ax, width, height)
    draw_hill(ax, width, height, base=0.36, wave=0.05, phase=0.4, color="#2E8C7E")
    draw_hill(ax, width, height, base=0.26, wave=0.04, phase=2.1, color="#16685F")
    draw_hill(ax, width, height, base=0.15, wave=0.03, phase=4.0, color=DEEP_TEAL)
    save(figure, output_path)


def draw_dawn_sky(ax, width, height):
    rows = np.linspace(0, 1, 256)[:, None, None]
    top_color = hex_to_rgb("#0B4A47")
    middle_color = hex_to_rgb("#3FB8A0")
    horizon_color = hex_to_rgb("#C8F0DE")
    lower_half = horizon_color * (1 - rows / 0.5) + middle_color * (rows / 0.5)
    upper_half = middle_color * (1 - (rows - 0.5) / 0.5) + top_color * ((rows - 0.5) / 0.5)
    sky = np.where(rows < 0.5, lower_half, upper_half)
    ax.imshow(np.repeat(sky, 4, axis=1), extent=(0, width, 0, height), origin="lower", aspect="auto", zorder=0)


def draw_sun_glow(ax, center_x, center_y, radius):
    for glow_scale, alpha in [(3.0, 0.08), (2.0, 0.12), (1.4, 0.2)]:
        ax.add_patch(plt.Circle((center_x, center_y), radius * glow_scale, color="#FFF6E6", alpha=alpha, zorder=1))
    ax.add_patch(plt.Circle((center_x, center_y), radius, color=CREAM, zorder=1))


def draw_soft_clouds(ax, width, height):
    cloud_rows = [(0.18, 0.78, 260), (0.80, 0.82, 300), (0.34, 0.90, 200), (0.66, 0.70, 220)]
    for x_fraction, y_fraction, cloud_width in cloud_rows:
        ax.add_patch(Ellipse((width * x_fraction, height * y_fraction), cloud_width, cloud_width * 0.22,
                             color="#E6FFF5", alpha=0.35, zorder=1))


def draw_hill(ax, width, height, base, wave, phase, color):
    x_values = np.linspace(0, width, 300)
    top = height * (base + wave * np.sin(np.linspace(phase, phase + 5.5, 300)))
    ax.fill_between(x_values, 0, top, color=color, zorder=2)


def center_crop(source_path, output_path, crop_height):
    image = Image.open(source_path)
    top = (image.height - crop_height) // 2
    image.crop((0, top, image.width, top + crop_height)).save(output_path)


def render_cover(icon_image, output_path):
    width, height = 600, 900
    figure, ax = new_canvas(width, height)
    draw_vertical_gradient(ax, width, height, bottom_color="#021E1D", top_color=DEEP_TEAL)
    draw_mint_glow(ax, width, height, center_x_fraction=0.5, center_y_fraction=0.62, strength=0.28)
    icon_size = 260
    draw_icon(ax, icon_image, (width - icon_size) / 2, 430, icon_size)
    ax.text(width / 2, 340, "Aynime", color=CREAM, fontsize=64, weight="bold", family="serif", ha="center", va="center", zorder=8)
    ax.text(width / 2, 262, "アニメで日本語", color=MINT, fontsize=30, weight="bold", family="serif", ha="center", va="center", zorder=8)
    ax.text(width / 2, 205, "Learn Japanese with anime", color=MUTED_MINT, fontsize=19, ha="center", va="center", zorder=8)
    save(figure, output_path)


def main(output_folder):
    folder = Path(output_folder)
    square_icon = Image.open(folder / ICON_FILE_NAME).convert("RGBA")
    icon_image = np.asarray(with_rounded_corners(square_icon, EMBEDDED_ICON_CORNER_SHARE))
    render_logo(icon_image, folder / "aynime_logo.png")
    render_hero(folder / "aynime_hero_1920x1080.png")
    center_crop(folder / "aynime_hero_1920x1080.png", folder / "aynime_hero_1920x620.png", 620)
    render_cover(icon_image, folder / "aynime_cover_600x900.png")


main(sys.argv[1])
