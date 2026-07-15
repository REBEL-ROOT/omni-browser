from PIL import Image

def process_dark_logo():
    # Load original dark logo
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    # Clean margins to remove the square box / line outline
    # Logo core is at x in [22, 356], y in [90, 286]
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            
            # De-noise margins
            if x < 18 or x > 366 or y < 86 or y > 290:
                pixels[x, y] = (0, 0, 0, 0)
            # De-noise extremely faint pixels
            elif a <= 8:
                pixels[x, y] = (0, 0, 0, 0)
                
    img.save('app/src/main/res/drawable-nodpi/omni_home_logo.webp', 'WEBP', lossless=True)
    print("Dark mode logo cleaned up successfully!")

def process_light_logo():
    # Load the fresh cleaned dark logo as base
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    out_img = Image.new('RGBA', (width, height))
    out_pixels = out_img.load()
    
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            
            # Clean margins and low-alpha noise
            if x < 18 or x > 366 or y < 86 or y > 290:
                out_pixels[x, y] = (0, 0, 0, 0)
                continue
            if a <= 8:
                out_pixels[x, y] = (0, 0, 0, 0)
                continue
                
            # Compute color features
            brightness = (r + g + b) / 3.0
            factor = brightness / 255.0
            
            # Check if it is a blue/cyan pixel (the compass globe, the O ring, blue glow)
            is_blue = (b > r + 15) and (b > 60)
            
            if is_blue:
                # Map to a rich dark blue / royal blue gradient
                new_r = int(5 + factor * (25 - 5))
                new_g = int(35 + factor * (100 - 35))
                new_b = int(120 + factor * (220 - 120))
                out_pixels[x, y] = (new_r, new_g, new_b, a)
            else:
                # Map to a very dark slate charcoal/black color for readability
                new_r = int(10 + factor * (35 - 10))
                new_g = int(12 + factor * (40 - 12))
                new_b = int(18 + factor * (50 - 18))
                out_pixels[x, y] = (new_r, new_g, new_b, a)
                
    out_img.save('app/src/main/res/drawable-nodpi/omni_home_logo_light.webp', 'WEBP', lossless=True)
    print("Light mode logo generated successfully!")

if __name__ == '__main__':
    process_dark_logo()
    process_light_logo()
