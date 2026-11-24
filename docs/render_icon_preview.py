"""Rendert eine Vorschau des vektorbasierten Breadcrumbz-Icons.

Dieses Skript ist nur ein Hilfsmittel für die Vorschau im Chat.
Das echte Icon liegt als Android-Vector-Drawable im Projekt.
"""

from PIL import Image, ImageDraw

S = 512 / 108
PETROL = "#0E3540"
RING_1 = "#1C4A56"
RING_2 = "#245C69"
RING_3 = "#2C6B79"
CREAM = "#FFF4DC"
AMBER = "#F5A93B"


def px(p):
    return (p[0] * S, p[1] * S)


def box(cx, cy, r):
    return (cx * S - r * S, cy * S - r * S, cx * S + r * S, cy * S + r * S)


def cubic(p0, p1, p2, p3, steps=40):
    points = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        x = u**3 * p0[0] + 3 * u**2 * t * p1[0] + 3 * u * t**2 * p2[0] + t**3 * p3[0]
        y = u**3 * p0[1] + 3 * u**2 * t * p1[1] + 3 * u * t**2 * p2[1] + t**3 * p3[1]
        points.append(px((x, y)))
    return points


def draw_icon(size=512):
    img = Image.new("RGBA", (size, size), PETROL)
    d = ImageDraw.Draw(img)

    for radius, color, width in ((42, RING_1, 2), (30, RING_2, 1.5), (18, RING_3, 1)):
        d.ellipse(box(54, 54, radius), outline=color, width=max(1, round(width * S)))

    route = []
    route += cubic((30, 80), (48, 80), (62, 70), (52, 58))
    route += cubic((52, 58), (43, 47), (40, 44), (50, 35))
    route += cubic((50, 35), (56, 30), (62, 31), (68, 34))
    d.line(route, fill=CREAM, width=round(7 * S), joint="curve")
    for point in (route[0], route[-1]):
        r = 3.5 * S
        d.ellipse((point[0] - r, point[1] - r, point[0] + r, point[1] + r), fill=CREAM)

    for cx, cy, r in ((30, 80, 4.5), (54, 60, 3.5), (45, 44, 3)):
        d.ellipse(box(cx, cy, r), fill=AMBER)

    pin = []
    pin += cubic((68, 20), (61, 20), (56, 25), (56, 32))
    pin += cubic((56, 32), (56, 40), (68, 50), (68, 50))
    pin += cubic((68, 50), (68, 50), (80, 40), (80, 32))
    pin += cubic((80, 32), (80, 25), (75, 20), (68, 20))
    d.polygon(pin, fill=AMBER)

    d.rounded_rectangle(
        (62 * S, 29 * S, 74 * S, 41 * S),
        radius=round(2 * S),
        fill=CREAM,
    )
    d.ellipse(box(70, 33, 1.6), fill=AMBER)
    return img


icon = draw_icon()
preview = Image.new("RGBA", (1024, 512), (255, 255, 255, 0))
preview.paste(icon, (0, 0))

mask = Image.new("L", (512, 512), 0)
mask_draw = ImageDraw.Draw(mask)
visible = round(72 / 108 * 512)
offset = (512 - visible) // 2
mask_draw.ellipse((offset, offset, offset + visible, offset + visible), fill=255)
preview.paste(icon, (512, 0), mask)

preview.convert("RGB").save("Breadcrumbz/docs/icon-preview.png")
print("Breadcrumbz/docs/icon-preview.png")
