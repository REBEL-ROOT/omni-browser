import zipfile
import re

aar_path = '/Users/moc/.gradle/caches/modules-2/files-2.1/org.mozilla.geckoview/geckoview/145.0.20251124145406/654f59db0099f34d786272cb437db2b98da29c6c/geckoview-145.0.20251124145406.aar'

# Open the aar as a zip
with zipfile.ZipFile(aar_path, 'r') as aar_zip:
    # Read classes.jar inside the aar
    classes_jar_data = aar_zip.read('classes.jar')
    
    # Open classes.jar from memory
    import io
    classes_jar_file = io.BytesIO(classes_jar_data)
    
    with zipfile.ZipFile(classes_jar_file, 'r') as jar_zip:
        class_names = jar_zip.namelist()
        
        # Look for PDF or Print related classes
        pdf_classes = [c for c in class_names if re.search(r'(pdf|print)', c, re.IGNORECASE)]
        print("Total classes:", len(class_names))
        print("\nPDF/Print related classes:")
        for c in pdf_classes[:30]:
            print("  ", c)
