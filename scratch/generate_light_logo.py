from PIL import Image

def process_logo():
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    out_img = Image.new('RGBA', (width, height))
    out_pixels = out_img.load()
    
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                out_pixels[x, y] = (0, 0, 0, 0)
                continue
                
            # Calculate metrics
            max_val = max(r, g, b)
            min_val = min(r, g, b)
            diff = max_val - min_val
            brightness = (r + g + b) / 3.0
            
            # Identify if it is a silver/white/light-gray pixel
            # These are mostly neutral or very bright
            is_silver = (diff < 40) or (r > 200 and g > 200 and b > 160)
            
            if is_silver:
                # Map to a premium dark slate/charcoal metallic gradient
                # Original brightness determines where in the gradient it lands
                factor = brightness / 255.0
                
                # Dark slate range: from (24, 32, 48) to (94, 109, 130)
                new_r = int(20 + factor * (70 - 20))
                new_g = int(28 + factor * (82 - 28))
                new_b = int(42 + factor * (98 - 42))
                
                out_pixels[x, y] = (new_r, new_g, new_b, a)
            elif r > 160 and g > 200 and b > 230:
                # This is a very light blue/cyan pixel (highlight of the blue parts)
                # Let's enrich it to a medium/dark blue so it doesn't wash out on light bg
                factor = brightness / 255.0
                new_r = int(10 * factor)
                new_g = int(100 * factor)
                new_b = int(220 * factor)
                out_pixels[x, y] = (new_r, new_g, new_b, a)
            else:
                # Keep regular colors (like electric blue, which already contrast well on light bg)
                out_pixels[x, y] = (r, g, b, a)
                
    out_img.save('app/src/main/res/drawable-nodpi/omni_home_logo_light.webp', 'WEBP')
    print("Successfully generated omni_home_logo_light.webp!")

if __name__ == '__main__':
    process_logo()
