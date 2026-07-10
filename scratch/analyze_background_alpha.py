from PIL import Image

def analyze():
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    # Analyze region y < 80
    max_a_top = 0
    for y in range(80):
        for x in range(width):
            max_a_top = max(max_a_top, pixels[x, y][3])
            
    # Analyze region y > 295
    max_a_bottom = 0
    for y in range(295, height):
        for x in range(width):
            max_a_bottom = max(max_a_bottom, pixels[x, y][3])
            
    # Analyze region x < 20
    max_a_left = 0
    for y in range(height):
        for x in range(20):
            max_a_left = max(max_a_left, pixels[x, y][3])
            
    # Analyze region x > 360
    max_a_right = 0
    for y in range(height):
        for x in range(360, width):
            max_a_right = max(max_a_right, pixels[x, y][3])
            
    print(f"Max alpha in top region (y < 80): {max_a_top}")
    print(f"Max alpha in bottom region (y > 295): {max_a_bottom}")
    print(f"Max alpha in left region (x < 20): {max_a_left}")
    print(f"Max alpha in right region (x > 360): {max_a_right}")

if __name__ == '__main__':
    analyze()
