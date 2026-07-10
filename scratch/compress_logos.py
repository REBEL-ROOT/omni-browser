import os
from PIL import Image

def compress_image(path, target_size=(384, 384), quality=80):
    if not os.path.exists(path):
        print(f"File not found: {path}")
        return
        
    old_size = os.path.getsize(path)
    img = Image.open(path)
    
    # Resize using high quality Lanczos filter
    resized_img = img.resize(target_size, Image.Resampling.LANCZOS)
    
    # Save back to same path
    resized_img.save(path, 'WEBP', quality=quality)
    new_size = os.path.getsize(path)
    
    print(f"Compressed {os.path.basename(path)}:")
    print(f"  Old size: {old_size / 1024:.1f} KB")
    print(f"  New size: {new_size / 1024:.1f} KB")
    print(f"  Saved: {(old_size - new_size) / 1024:.1f} KB ({(old_size - new_size)/old_size*100:.1f}%)")

if __name__ == '__main__':
    # Compress dark and light home page logos
    compress_image('app/src/main/res/drawable-nodpi/omni_home_logo.webp')
    compress_image('app/src/main/res/drawable-nodpi/omni_home_logo_light.webp')
    
    # Compress launcher webp icon (512x512 -> 256x256)
    compress_image('app/src/main/res/drawable/ic_omni_logo.webp', target_size=(256, 256))
