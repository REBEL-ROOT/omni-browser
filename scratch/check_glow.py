from PIL import Image

def check():
    img = Image.open('app/src/main/res/drawable-nodpi/omni_home_logo.webp').convert('RGBA')
    width, height = img.size
    pixels = img.load()
    
    # Let's count pixels in different alpha ranges inside the logo core:
    # y in [90, 286], x in [22, 356]
    counts = {
        '0': 0,
        '1-5': 0,
        '6-10': 0,
        '11-15': 0,
        '16-20': 0,
        '21-50': 0,
        '51-100': 0,
        '101-255': 0
    }
    
    for y in range(90, 287):
        for x in range(22, 357):
            r, g, b, a = pixels[x, y]
            if a == 0:
                counts['0'] += 1
            elif 1 <= a <= 5:
                counts['1-5'] += 1
            elif 6 <= a <= 10:
                counts['6-10'] += 1
            elif 11 <= a <= 15:
                counts['11-15'] += 1
            elif 16 <= a <= 20:
                counts['16-20'] += 1
            elif 21 <= a <= 50:
                counts['21-50'] += 1
            elif 51 <= a <= 100:
                counts['51-100'] += 1
            else:
                counts['101-255'] += 1
                
    print("Alpha distribution in logo core:")
    for k, v in counts.items():
        print(f"  Alpha {k}: {v} pixels")

if __name__ == '__main__':
    check()
