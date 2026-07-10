from PIL import Image

def find_core():
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    # Bounding box for alpha >= 20
    min_x, min_y = width, height
    max_x, max_y = 0, 0
    
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a >= 20:
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
                
    print(f"Bounding box of alpha >= 20: ({min_x}, {min_y}) to ({max_x}, {max_y})")
    
    # Bounding box for alpha >= 50
    min_x50, min_y50 = width, height
    max_x50, max_y50 = 0, 0
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a >= 50:
                min_x50 = min(min_x50, x)
                min_y50 = min(min_y50, y)
                max_x50 = max(max_x50, x)
                max_y50 = max(max_y50, y)
                
    print(f"Bounding box of alpha >= 50: ({min_x50}, {min_y50}) to ({max_x50}, {max_y50})")

if __name__ == '__main__':
    find_core()
