from PIL import Image

def locate():
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    # We want to scan the image and categorize visible pixels (alpha > 50)
    # Categories:
    # 1. Blue/Cyan (B > R + 20 and B > 100)
    # 2. Bright Silver/White (R > 180 and G > 180 and B > 180 and abs(R-B) < 20 and abs(G-B) < 20)
    # 3. Dark/Gray (R < 100 and G < 100 and B < 100)
    
    blue_pixels = []
    silver_pixels = []
    dark_pixels = []
    
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a > 50:
                if b > r + 20 and b > 100:
                    blue_pixels.append((x, y))
                elif r > 180 and g > 180 and b > 180 and abs(r-b) < 20 and abs(g-b) < 20:
                    silver_pixels.append((x, y))
                elif r < 120 and g < 120 and b < 120:
                    dark_pixels.append((x, y))
                    
    def get_bbox(pts):
        if not pts:
            return None
        min_x = min(p[0] for p in pts)
        max_x = max(p[0] for p in pts)
        min_y = min(p[1] for p in pts)
        max_y = max(p[1] for p in pts)
        return (min_x, min_y, max_x, max_y)
        
    print(f"Blue pixels count: {len(blue_pixels)}, BBox: {get_bbox(blue_pixels)}")
    print(f"Silver pixels count: {len(silver_pixels)}, BBox: {get_bbox(silver_pixels)}")
    print(f"Dark pixels count: {len(dark_pixels)}, BBox: {get_bbox(dark_pixels)}")

if __name__ == '__main__':
    locate()
