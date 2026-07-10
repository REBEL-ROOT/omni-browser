from PIL import Image

img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
width, height = img.size
pixels = img.load()

visible_pixels = []
for y in range(0, height, 10):
    for x in range(0, width, 10):
        r, g, b, a = pixels[x, y]
        if a > 50:
            visible_pixels.append((r, g, b, a))

print("Total sampled visible pixels:", len(visible_pixels))
print("Sample visible pixels (R, G, B, A):")
for p in visible_pixels[:50]:
    print(p)
