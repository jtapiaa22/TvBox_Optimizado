"""Parche minimo de un metodo en un .dex: lo hace devolver `true` sin cambiar el tamano.

Uso: python -I patch_dex.py <entrada.apk> <salida_sin_firmar.apk> <Lpaquete/Clase;> <metodo>
Reemplaza las dos primeras unidades de codigo del metodo por `const/4 v0, 1` + `return v0`
(el resto queda como codigo muerto) y recalcula checksum (Adler32) y firma (SHA-1) del dex.
Se copian todas las demas entradas del APK con su mismo metodo de compresion; las firmas se descartan
(hay que volver a firmar con apksigner).
"""
import hashlib
import struct
import sys
import zipfile
import zlib

apk_in, apk_out, cls_desc, meth = sys.argv[1:5]


def uleb(b, p):
    r = s = 0
    while True:
        x = b[p]; p += 1
        r |= (x & 0x7F) << s; s += 7
        if not x & 0x80:
            return r, p


def find_method(d):
    (str_n, str_off, type_n, type_off, proto_n, proto_off, field_n, field_off,
     meth_n, meth_off, cls_n, cls_off) = struct.unpack_from("<12I", d, 0x38)

    def sstr(i):
        off = struct.unpack_from("<I", d, str_off + 4 * i)[0]
        _, p = uleb(d, off)
        e = d.index(b"\0", p)
        return d[p:e].decode("utf-8", "replace")

    def tname(i):
        return sstr(struct.unpack_from("<I", d, type_off + 4 * i)[0])

    for c in range(cls_n):
        ci, _, _, _, _, _, data_off, _ = struct.unpack_from("<8I", d, cls_off + 32 * c)
        if tname(ci) != cls_desc or not data_off:
            continue
        p = data_off
        sf, p = uleb(d, p); inf, p = uleb(d, p); dm, p = uleb(d, p); vm, p = uleb(d, p)
        for _ in range(sf + inf):
            _, p = uleb(d, p); _, p = uleb(d, p)
        idx = 0
        for n in (dm, vm):
            idx = 0
            for _ in range(n):
                diff, p = uleb(d, p); idx += diff
                _, p = uleb(d, p)
                code_off, p = uleb(d, p)
                _, _, name_i = struct.unpack_from("<HHI", d, meth_off + 8 * idx)
                if sstr(name_i) == meth:
                    yield idx, code_off
    return


data = {}
with zipfile.ZipFile(apk_in) as z:
    infos = z.infolist()
    for i in infos:
        data[i.filename] = z.read(i.filename)

patched = 0
for name in sorted(data):
    if not (name.startswith("classes") and name.endswith(".dex")):
        continue
    d = bytearray(data[name])
    hits = list(find_method(d))
    if not hits:
        continue
    for idx, code_off in hits:
        regs, ins, outs, tries, dbg, size = struct.unpack_from("<HHHHII", d, code_off)
        first = struct.unpack_from("<HH", d, code_off + 16)
        print(f"{name}: metodo #{idx} code_off=0x{code_off:x} registros={regs} ins={ins} unidades={size} "
              f"primeras={first[0]:04x} {first[1]:04x}")
        if regs < 1 or size < 2:
            sys.exit("el metodo no admite el parche")
        struct.pack_into("<HH", d, code_off + 16, 0x1012, 0x000F)   # const/4 v0,#1 ; return v0
        patched += 1
    d[12:32] = hashlib.sha1(bytes(d[32:])).digest()
    struct.pack_into("<I", d, 8, zlib.adler32(bytes(d[12:])) & 0xFFFFFFFF)
    data[name] = bytes(d)

if patched != 1:
    sys.exit(f"se esperaba parchear exactamente 1 metodo y se encontraron {patched}")

with zipfile.ZipFile(apk_out, "w") as zo:
    for i in infos:
        if i.filename.startswith("META-INF/") and (i.filename.endswith((".SF", ".RSA", ".DSA", ".EC", ".MF"))):
            continue
        zi = zipfile.ZipInfo(i.filename, date_time=i.date_time)
        zi.compress_type = i.compress_type
        zi.external_attr = i.external_attr
        zo.writestr(zi, data[i.filename])
print("OK ->", apk_out)
