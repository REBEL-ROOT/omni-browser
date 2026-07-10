from PIL import Image

def find_parts():
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    # We want to scan rows from y=90 to y=286 and see what colors are dominant.
    # We can print row metrics: average red, green, blue for visible pixels.
    print("Row analysis (Dominant colors of visible pixels):")
    for y in range(90, 287):
        visible_pixels = []
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a > 50:
                visible_pixels.append((r, g, b))
                
        if visible_pixels:
            avg_r = sum(p[0] for p in visible_pixels) / len(visible_pixels)
            avg_g = sum(p[1] for p in visible_pixels) / len(visible_pixels)
            avg_b = sum(p[2] for p in visible_pixels) / len(visible_pixels)
            print(f"Row {y:03d} (pixels: {len(visible_pixels):3d}): Avg RGB=({avg_r:.1f}, {avg_g:.1f}, {avg_b:.1f})")

if __name__ == '__main__':
    find_parts()
