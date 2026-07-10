from PIL import Image

def find_lines():
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    # We want to look for horizontal lines:
    # A horizontal line of length >= 50 pixels where alpha is relatively low but constant,
    # or where there's a line.
    print("Horizontal line segments:")
    for y in range(height):
        current_len = 0
        start_x = 0
        for x in range(width):
            r, g, b, a = pixels[x, y]
            # Let's say a line has alpha > 0 (and maybe it's faint, e.g. < 40)
            # and is part of a straight line.
            # To be more precise, let's check if the pixel has a > 0.
            if a > 0:
                if current_len == 0:
                    start_x = x
                current_len += 1
            else:
                if current_len >= 100:
                    print(f"  Row {y}: x from {start_x} to {x-1} (len {current_len}), average alpha: {sum(pixels[xi, y][3] for xi in range(start_x, x)) / current_len:.1f}")
                current_len = 0
        if current_len >= 100:
            print(f"  Row {y}: x from {start_x} to {width-1} (len {current_len}), average alpha: {sum(pixels[xi, y][3] for xi in range(start_x, width)) / current_len:.1f}")

    print("\nVertical line segments:")
    for x in range(width):
        current_len = 0
        start_y = 0
        for y in range(height):
            r, g, b, a = pixels[x, y]
            if a > 0:
                if current_len == 0:
                    start_y = y
                current_len += 1
            else:
                if current_len >= 100:
                    print(f"  Col {x}: y from {start_y} to {y-1} (len {current_len}), average alpha: {sum(pixels[x, yi][3] for yi in range(start_y, y)) / current_len:.1f}")
                current_len = 0
        if current_len >= 100:
            print(f"  Col {x}: y from {start_y} to {height-1} (len {current_len}), average alpha: {sum(pixels[x, yi][3] for yi in range(start_y, height)) / current_len:.1f}")

if __name__ == '__main__':
    find_lines()
