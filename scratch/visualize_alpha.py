from PIL import Image

def visualize():
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    # dividing the image into a 40x40 grid and print the maximum alpha in each grid cell
    grid_size = 10
    grid_w = (width + grid_size - 1) // grid_size
    grid_h = (height + grid_size - 1) // grid_size
    
    print("ASCII Map of Alpha Values ('.' = 0, '1'-'9' = low alpha, '#' = high alpha):")
    for gy in range(grid_h):
        line = []
        for gx in range(grid_w):
            max_a = 0
            for dy in range(grid_size):
                y = gy * grid_size + dy
                if y >= height:
                    continue
                for dx in range(grid_size):
                    x = gx * grid_size + dx
                    if x >= width:
                        continue
                    max_a = max(max_a, pixels[x, y][3])
            
            if max_a == 0:
                line.append('.')
            elif max_a < 25:
                line.append('-')
            elif max_a < 100:
                line.append('+')
            else:
                line.append('#')
        print("".join(line))

if __name__ == '__main__':
    visualize()
