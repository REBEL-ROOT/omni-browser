from PIL import Image
import numpy as np

img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.png').convert('RGBA')
data = np.array(img)

# App background color
target_r, target_g, target_b = 7, 10, 15

# Get the background color from top-left pixel
bg_r, bg_g, bg_b, _ = data[0, 0]

# Calculate the difference
diff_r = int(target_r) - int(bg_r)
diff_g = int(target_g) - int(bg_g)
diff_b = int(target_b) - int(bg_b)

# We only want to adjust pixels that are close to the background color.
# Actually, since it's a glow on a dark background, we can just shift the black point!
# But a simpler way: just make the dark background transparent using a mask, 
# or just add the difference to all pixels! Wait, adding to all pixels changes the logo color slightly.
# Let's just find pixels that are very close to bg_color and replace them, blending for anti-aliasing.

# Better yet, let's just make the background transparent using luma as alpha!
# Since it's a glowing logo on a dark background:
# New Alpha = max(R, G, B) (roughly, to keep glow)
# But wait, the user just said "remove the icon symbol brand bg and bland with the home page".
# Let's just use rembg! Or we can do a simple flood fill to transparency.

