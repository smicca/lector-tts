import os
import math
from PIL import Image, ImageDraw

def render_lector_icon(size=512, bg_color=(22, 37, 68), round_icon=False):
    # Create high-res canvas
    scale = size / 108.0
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Draw rounded rectangle background or circle
    radius = int(size * 0.22) if not round_icon else size // 2
    draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=bg_color)

    # Helper to scale points
    def S(x, y):
        return (x * scale, y * scale)

    # Draw book pages (white fill)
    # Using polygon approximation of the cubic curve
    def get_cubic_point(p0, p1, p2, p3, t):
        x = (1-t)**3 * p0[0] + 3*(1-t)**2 * t * p1[0] + 3*(1-t) * t**2 * p2[0] + t**3 * p3[0]
        y = (1-t)**3 * p0[1] + 3*(1-t)**2 * t * p1[1] + 3*(1-t) * t**2 * p2[1] + t**3 * p3[1]
        return (x, y)

    # Left top: (54, 56) -> (45, 53) -> (37, 55) -> (30, 52)
    top_left = [get_cubic_point((54, 56), (45, 53), (37, 55), (30, 52), i/20.0) for i in range(21)]
    # Left edge: (30, 52) -> (28, 54) -> (28, 71) -> (30, 73)
    left_edge = [(28, 54), (28, 71), (30, 73)]
    # Left bottom: (30, 73) -> (37, 76) -> (45, 74) -> (54, 76)
    bot_left = [get_cubic_point((30, 73), (37, 76), (45, 74), (54, 76), i/20.0) for i in range(21)]
    # Right bottom: (54, 76) -> (63, 74) -> (71, 76) -> (78, 73)
    bot_right = [get_cubic_point((54, 76), (63, 74), (71, 76), (78, 73), i/20.0) for i in range(21)]
    # Right edge: (78, 73) -> (80, 71) -> (80, 54) -> (78, 52)
    right_edge = [(80, 71), (80, 54), (78, 52)]
    # Right top: (78, 52) -> (71, 55) -> (63, 53) -> (54, 56)
    top_right = [get_cubic_point((78, 52), (71, 55), (63, 53), (54, 56), i/20.0) for i in range(21)]

    book_poly_108 = top_left + left_edge + bot_left + bot_right + right_edge + top_right
    book_poly_scaled = [S(x, y) for (x, y) in book_poly_108]

    # Draw book body
    draw.polygon(book_poly_scaled, fill=(255, 255, 255, 255))

    # Spine line
    draw.line([S(54, 56), S(54, 76)], fill=(203, 213, 225, 255), width=max(2, int(2.5 * scale)))

    # Page lines left
    l1 = [get_cubic_point((34, 61), (39, 62), (44, 61), (49, 62), i/10.0) for i in range(11)]
    l2 = [get_cubic_point((34, 67), (39, 68), (44, 67), (49, 68), i/10.0) for i in range(11)]
    draw.line([S(x, y) for (x, y) in l1], fill=(148, 163, 184, 255), width=max(1, int(2 * scale)))
    draw.line([S(x, y) for (x, y) in l2], fill=(148, 163, 184, 255), width=max(1, int(2 * scale)))

    # Page lines right
    r1 = [get_cubic_point((59, 62), (64, 61), (69, 62), (74, 61), i/10.0) for i in range(11)]
    r2 = [get_cubic_point((59, 68), (64, 67), (69, 68), (74, 67), i/10.0) for i in range(11)]
    draw.line([S(x, y) for (x, y) in r1], fill=(148, 163, 184, 255), width=max(1, int(2 * scale)))
    draw.line([S(x, y) for (x, y) in r2], fill=(148, 163, 184, 255), width=max(1, int(2 * scale)))

    # Audio waves
    wave1 = [get_cubic_point((47, 47), (49, 43), (59, 43), (61, 47), i/20.0) for i in range(21)]
    wave2 = [get_cubic_point((41, 40), (45, 33), (63, 33), (67, 40), i/20.0) for i in range(21)]
    wave3 = [get_cubic_point((35, 33), (41, 23), (67, 23), (73, 33), i/20.0) for i in range(21)]

    wave_color = (96, 165, 250, 255) # Celestial Cyan / Lapis Accent #60A5FA
    draw.line([S(x, y) for (x, y) in wave1], fill=wave_color, width=max(2, int(3.5 * scale)))
    draw.line([S(x, y) for (x, y) in wave2], fill=wave_color, width=max(2, int(3.5 * scale)))
    draw.line([S(x, y) for (x, y) in wave3], fill=wave_color, width=max(2, int(3.5 * scale)))

    return img

if __name__ == "__main__":
    os.makedirs("/Users/myhome/Code/mobile/lector-tts/docs", exist_ok=True)
    icon_512 = render_lector_icon(512, bg_color=(22, 37, 68, 255))
    icon_512.save("/Users/myhome/Code/mobile/lector-tts/docs/ic_launcher-playstore.png")

    # Render mipmap densities
    densities = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192
    }
    for name, sz in densities.items():
        res_dir = f"/Users/myhome/Code/mobile/lector-tts/app/src/main/res/mipmap-{name}"
        os.makedirs(res_dir, exist_ok=True)
        img_square = render_lector_icon(sz, bg_color=(22, 37, 68, 255), round_icon=False)
        img_square.save(os.path.join(res_dir, "ic_launcher.png"))
        img_round = render_lector_icon(sz, bg_color=(22, 37, 68, 255), round_icon=True)
        img_round.save(os.path.join(res_dir, "ic_launcher_round.png"))
    print("Icons generated successfully!")
