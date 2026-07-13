from PIL import Image

def inspect():
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    print("Sampling pixels in the MNI text area (x=240..280, y=160..200):")
    sampled = 0
    for y in range(160, 200, 4):
        for x in range(240, 280, 4):
            r, g, b, a = pixels[x, y]
            if a > 100:
                diff = max(r, g, b) - min(r, g, b)
                print(f"  At ({x}, {y}): RGBA=({r},{g},{b},{a}), diff={diff}")
                sampled += 1
                if sampled >= 30:
                    return

if __name__ == '__main__':
    inspect()
