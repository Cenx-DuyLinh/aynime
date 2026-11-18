import sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

INK = "#ECE8E0"
MUTED = "#9C968A"
VOICE_COLOR = "#7FC48D"
SUBTITLE_COLOR = "#D97757"
SPEECH_PARTS = [(1.0, 2.6), (3.4, 4.2), (5.5, 7.8), (8.6, 9.4), (10.5, 12.3)]
FILE_SHIFT_SECONDS = 1.3


def draw_blocks(ax, row_y, parts, shift, color):
    for start, end in parts:
        ax.add_patch(FancyBboxPatch((start + shift, row_y - 0.18), end - start, 0.36,
                                    boxstyle="round,pad=0,rounding_size=0.08", color=color))


def draw_row_label(ax, row_y, text, color):
    ax.text(-0.3, row_y, text, color=color, fontsize=11, ha="right", va="center", weight="bold")


def draw_panel(ax, title, shift):
    ax.set_xlim(-6.2, 14)
    ax.set_ylim(-0.4, 2.4)
    ax.axis("off")
    ax.set_title(title, color=INK, fontsize=12, loc="left")
    draw_row_label(ax, 1.6, "voice in the audio", VOICE_COLOR)
    draw_blocks(ax, 1.6, SPEECH_PARTS, 0, VOICE_COLOR)
    draw_row_label(ax, 0.7, "subtitle lines (file)", SUBTITLE_COLOR)
    draw_blocks(ax, 0.7, SPEECH_PARTS, shift, SUBTITLE_COLOR)
    ax.plot([0, 13.5], [0.1, 0.1], color=MUTED, linewidth=1)
    ax.text(13.5, -0.15, "time", color=MUTED, fontsize=10, ha="right")


def main(output_path):
    figure, axes = plt.subplots(2, 1, figsize=(11, 5.4))
    figure.patch.set_facecolor("#0E0E0D")
    draw_panel(axes[0], "1. Listen ~30-60 s: the subtitle lines start 1.3 s after the voices", FILE_SHIFT_SECONDS)
    axes[0].add_patch(FancyArrowPatch((5.5 + FILE_SHIFT_SECONDS, 1.05), (5.5, 1.05), arrowstyle="-|>",
                                      mutation_scale=14, color=INK, linewidth=1.5))
    axes[0].text(5.5 + FILE_SHIFT_SECONDS / 2, 1.2, "shift", color=INK, fontsize=10, ha="center")
    draw_panel(axes[1], "2. Slide the subtitles until the blocks line up best, then apply that offset (−1.3 s)", 0)
    figure.savefig(output_path, dpi=100, bbox_inches="tight", facecolor=figure.get_facecolor())


main(sys.argv[1])
