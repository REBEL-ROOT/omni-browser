from PIL import Image

img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.png').convert('RGBA')
pixels = img.load()
width, height = img.size

# The harsh line is at the top. Let's fade out the top 20 pixels.
fade_height = 40

for y in range(fade_height):
    # Alpha multiplier goes from 0.0 at y=0 to 1.0 at y=fade_height
    alpha_mult = y / float(fade_height)
    # Actually, we want a smooth curve (ease-in)
    alpha_mult = alpha_mult ** 2
    
    for x in range(width):
        r, g, b, a = pixels[x, y]
        pixels[x, y] = (r, g, b, int(a * alpha_mult))

# Also let's check if there's a harsh line at the bottom, left, or right just in case
for y in range(height - fade_height, height):
    dist_from_bottom = height - 1 - y
    alpha_mult = (dist_from_bottom / float(fade_height)) ** 2
    for x in range(width):
        r, g, b, a = pixels[x, y]
        pixels[x, y] = (r, g, b, int(a * alpha_mult))

for x in range(fade_height):
    alpha_mult = (x / float(fade_height)) ** 2
    for y in range(height):
        r, g, b, a = pixels[x, y]
        pixels[x, y] = (r, g, b, int(a * alpha_mult))

for x in range(width - fade_height, width):
    dist_from_right = width - 1 - x
    alpha_mult = (dist_from_right / float(fade_height)) ** 2
    for y in range(height):
        r, g, b, a = pixels[x, y]
        pixels[x, y] = (r, g, b, int(a * alpha_mult))

img.save('app/src/main/res/drawable-nodpi/omni_home_logo.png')
print("Edges faded out successfully!")
