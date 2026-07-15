from PIL import Image

def inspect():
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo_light.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    print("Checking omni_home_logo_light.webp rows 262 to 286 (Browser text region):")
    for y in range(262, 287):
        row_colors = []
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a > 50:
                row_colors.append((r, g, b))
        if row_colors:
            avg_r = sum(c[0] for c in row_colors) / len(row_colors)
            avg_g = sum(c[1] for c in row_colors) / len(row_colors)
            avg_b = sum(c[2] for c in row_colors) / len(row_colors)
            print(f"Row {y}: Avg RGB=({avg_r:.1f}, {avg_g:.1f}, {avg_b:.1f})")

    print("\nChecking omni_home_logo_light.webp rows 200 to 220 (OMNI text region):")
    for y in range(200, 221):
        row_colors = []
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a > 50:
                row_colors.append((r, g, b))
        if row_colors:
            avg_r = sum(c[0] for c in row_colors) / len(row_colors)
            avg_g = sum(c[1] for c in row_colors) / len(row_colors)
            avg_b = sum(c[2] for c in row_colors) / len(row_colors)
            print(f"Row {y}: Avg RGB=({avg_r:.1f}, {avg_g:.1f}, {avg_b:.1f})")

if __name__ == '__main__':
    inspect()
