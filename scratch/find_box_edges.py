from PIL import Image

def analyze():
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    print(f"Image dimensions: {width}x{height}")
    
    # Let's inspect the boundaries.
    # We'll check the outer 5 pixels of the image and count how many have non-zero alpha.
    border_width = 5
    non_zero_borders = []
    
    for y in range(height):
        for x in range(width):
            # If it's in the outer border area
            if x < border_width or x >= width - border_width or y < border_width or y >= height - border_width:
                r, g, b, a = pixels[x, y]
                if a > 0:
                    non_zero_borders.append((x, y, r, g, b, a))
                    
    print(f"Total non-zero alpha pixels in the outer {border_width}px border: {len(non_zero_borders)}")
    if non_zero_borders:
        print("First 20 border pixels:")
        for pixel in non_zero_borders[:20]:
            print(f"  At ({pixel[0]}, {pixel[1]}): RGBA=({pixel[2]}, {pixel[3]}, {pixel[4]}, {pixel[5]})")
            
    # Let's check if there is a constant alpha box.
    # For example, does a box or border exist around some rectangle inside the image?
    # Let's scan each row and see if there are rows or columns where almost all pixels have a tiny alpha.
    print("\nScanning for rows with many low-alpha pixels:")
    for y in range(height):
        low_alpha_count = 0
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if 0 < a < 20:
                low_alpha_count += 1
        if low_alpha_count > width * 0.5:
            print(f"Row {y} has {low_alpha_count} low-alpha (1-19) pixels out of {width}")

    print("\nScanning for cols with many low-alpha pixels:")
    for x in range(width):
        low_alpha_count = 0
        for y in range(height):
            r, g, b, a = pixels[x, y]
            if 0 < a < 20:
                low_alpha_count += 1
        if low_alpha_count > height * 0.5:
            print(f"Col {x} has {low_alpha_count} low-alpha (1-19) pixels out of {height}")

if __name__ == '__main__':
    analyze()
