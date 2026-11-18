import sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Polygon, Circle

ICON_SIZE = 108
ORANGE = "#D97757"
WHITE = "#FFFFFF"


def flip(y):
    return ICON_SIZE - y


def rounded_bar(ax, left, top, width, height, alpha):
    ax.add_patch(FancyBboxPatch((left, flip(top + height)), width, height,
                                boxstyle=f"round,pad=0,rounding_size={height / 2}", color=WHITE, alpha=alpha))


def draw_icon(ax, background_shape):
    ax.set_xlim(0, ICON_SIZE)
    ax.set_ylim(0, ICON_SIZE)
    ax.set_aspect("equal")
    ax.axis("off")
    ax.add_patch(background_shape)
    ax.add_patch(Polygon([(47, flip(30)), (47, flip(56)), (68.5, flip(43))], closed=True,
                         facecolor=WHITE, edgecolor=WHITE, linewidth=4, joinstyle="round"))
    rounded_bar(ax, 35, 64, 38, 5, 1.0)
    rounded_bar(ax, 42, 73, 24, 5, 0.6)


def main(output_path):
    figure, axes = plt.subplots(1, 2, figsize=(7, 3.8))
    figure.patch.set_facecolor("#0E0E0D")
    draw_icon(axes[0], FancyBboxPatch((10, 10), 88, 88, boxstyle="round,pad=0,rounding_size=24", color=ORANGE))
    draw_icon(axes[1], Circle((54, 54), 44, color=ORANGE))
    axes[0].set_title("rounded-square launcher", color="#ECE8E0", fontsize=11)
    axes[1].set_title("round launcher", color="#ECE8E0", fontsize=11)
    figure.savefig(output_path, dpi=110, bbox_inches="tight", facecolor=figure.get_facecolor())


main(sys.argv[1])
