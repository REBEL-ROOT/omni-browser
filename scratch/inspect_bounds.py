from PIL import Image

def inspect():
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    # Let's find the bounding box of pixels with alpha > 0
    min_x, min_y = width, height
    max_x, max_y = 0, 0
    
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a > 0:
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
                
    print(f"Bounding box of alpha > 0: ({min_x}, {min_y}) to ({max_x}, {max_y})")
    
    # Let's check if there is a sharp boundary (like a rectangle) where alpha goes from 0 to something,
    # or if there is a border line.
    # Let's print the alpha values along the boundary of the bounding box.
    print(f"Checking top boundary at y={min_y}:")
    alphas_top = [pixels[x, min_y][3] for x in range(min_x, max_x + 1)]
    print(f"  Min alpha: {min(alphas_top)}, Max alpha: {max(alphas_top)}, Average: {sum(alphas_top)/len(alphas_top):.1f}")
    
    print(f"Checking bottom boundary at y={max_y}:")
    alphas_bottom = [pixels[x, max_y][3] for x in range(min_x, max_x + 1)]
    print(f"  Min alpha: {min(alphas_bottom)}, Max alpha: {max(alphas_bottom)}, Average: {sum(alphas_bottom)/len(alphas_bottom):.1f}")
    
    print(f"Checking left boundary at x={min_x}:")
    alphas_left = [pixels[min_x, y][3] for y in range(min_y, max_y + 1)]
    print(f"  Min alpha: {min(alphas_left)}, Max alpha: {max(alphas_left)}, Average: {sum(alphas_left)/len(alphas_left):.1f}")
    
    print(f"Checking right boundary at x={max_x}:")
    alphas_right = [pixels[max_x, y][3] for y in range(min_y, max_y + 1)]
    print(f"  Min alpha: {min(alphas_right)}, Max alpha: {max(alphas_right)}, Average: {sum(alphas_right)/len(alphas_right):.1f}")

if __name__ == '__main__':
    inspect()
