from PIL import Image

def verify(filename):
    img = Image.open(f'app/src/main/res/drawable-nodpi/{filename}').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    # We expect zero pixels with alpha > 0 outside the core bounding box:
    # x in [18, 366], y in [86, 290]
    non_zero_count = 0
    for y in range(height):
        for x in range(width):
            if x < 18 or x > 366 or y < 86 or y > 290:
                r, g, b, a = pixels[x, y]
                if a > 0:
                    non_zero_count += 1
                    
    print(f"{filename}: Found {non_zero_count} non-zero alpha pixels in the outer margins.")
    
    # Also verify that low-alpha pixels are cleared (alpha <= 8)
    very_low_alpha_count = 0
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if 0 < a <= 8:
                very_low_alpha_count += 1
    print(f"{filename}: Found {very_low_alpha_count} pixels with alpha in range [1, 8].")

if __name__ == '__main__':
    verify('omni_home_logo.webp')
    verify('omni_home_logo_light.webp')
