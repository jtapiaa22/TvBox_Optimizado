"""Arma bootanimation.zip: Android exige entradas SIN comprimir (ZIP_STORED)."""
import os
import sys
import zipfile

src, out = sys.argv[1], sys.argv[2]
# texto: una vez y se sostiene 8 cuadros; leo: una vez; loop: hasta que termine de arrancar
desc = "1280 720 20\np 1 8 texto\np 1 0 leo\np 0 0 loop\n"
with zipfile.ZipFile(out, "w", zipfile.ZIP_STORED) as z:
    z.writestr("desc.txt", desc)
    for part in ("texto", "leo", "loop"):
        for name in sorted(os.listdir(os.path.join(src, part))):
            z.write(os.path.join(src, part, name), f"{part}/{name}")
with zipfile.ZipFile(out) as z:
    methods = {i.compress_type for i in z.infolist()}
    print("entradas:", len(z.infolist()), "metodos:", methods, "testzip:", z.testzip())
print("tamano MB:", round(os.path.getsize(out) / 1048576, 1))
