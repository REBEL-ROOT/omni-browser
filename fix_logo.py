from PIL import Image

img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.png').convert('RGBA')
pixels = img.load()
width, height = img.size

bg_color = pixels[0, 0][:3]

for y in range(height):
    for x in range(width):
        r, g, b, a = pixels[x, y]
        
        # Subtract background
        r = max(0, r - bg_color[0])
        g = max(0, g - bg_color[1])
        b = max(0, b - bg_color[2])
        
        alpha = max(r, g, b)
        
        if alpha > 0:
            r = min(255, int(r * 255.0 / alpha))
            g = min(255, int(g * 255.0 / alpha))
            b = min(255, int(b * 255.0 / alpha))
            
        pixels[x, y] = (r, g, b, alpha)

img.save('app/src/main/res/drawable-nodpi/omni_home_logo.png')
print("Image background successfully made transparent using pure Pillow!")
