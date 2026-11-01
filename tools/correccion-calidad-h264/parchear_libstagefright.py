"""Sube el tope de la lista de perfiles/niveles que Android 7 lee de los decodificadores.

Problema (Android 7.x, 32 bits Thumb): ACodec::queryCapabilities revisa como maximo 33 posiciones
(`kMaxIndicesToCheck = 32`) al preguntar al decodificador que perfiles y niveles de H.264 acepta. Los decodificadores
de Rockchip tienen 48 (Baseline 16 + Main 16 + High 16): la lista se corta justo despues del primer nivel de High, y las
apps (ExoPlayer/Media3) descartan los videos High de 720p/1080p.

Que hace este script: ubica ACodec::queryCapabilities en libstagefright.so (por su simbolo dinamico), busca en esa funcion
las instrucciones `cmp r0, #0x21` (0x2821) que cierran los bucles de consulta y las cambia por `cmp r0, #0x41` (0x2841):
el tope pasa de 33 a 65 posiciones. Son 3 bytes. No cambia ningun tamano.

Uso:
    python -I parchear_libstagefright.py <libstagefright.so original> <salida parcheada>

Se niega a parchear (sale con error) si lo que encuentra no es exactamente lo esperado: 3 coincidencias, cada una seguida
de una rama condicional. Asi no toca una libreria distinta (otro Android, otro compilador, modo ARM en vez de Thumb).
"""
import struct
import sys

src, out = sys.argv[1], sys.argv[2]
b = bytearray(open(src, "rb").read())

if b[:4] != b"\x7fELF" or b[4] != 1 or struct.unpack_from("<H", b, 0x12)[0] != 40:
    sys.exit("No es un ELF de 32 bits ARM: no se parchea.")

shoff = struct.unpack_from("<I", b, 0x20)[0]
shentsize, shnum = struct.unpack_from("<HH", b, 0x2E)
secs = [struct.unpack_from("<10I", b, shoff + i * shentsize) for i in range(shnum)]
# (name, type, flags, addr, offset, size, link, info, addralign, entsize)
dynsym = next((s for s in secs if s[1] == 11), None)  # SHT_DYNSYM
if dynsym is None:
    sys.exit("La libreria no tiene tabla de simbolos dinamicos.")
dynstr = secs[dynsym[6]]


def cstr(off):
    e = b.index(b"\0", off)
    return b[off:e].decode("ascii", "replace")


func = None
for i in range(dynsym[5] // 16):
    name, value, size, info, other, shndx = struct.unpack_from("<IIIBBH", b, dynsym[4] + 16 * i)
    n = cstr(dynstr[4] + name)
    # el metodo real, no el "thunk" (_ZThn8_...)
    if n.startswith("_ZN7android6ACodec17queryCapabilities") and shndx:
        func = (n, value & ~1, size)
        break
if func is None:
    sys.exit("No encontre ACodec::queryCapabilities: no es la libreria esperada.")
print(f"funcion: {func[0][:60]}...  vaddr={func[1]:#x} tamano={func[2]}")

# vaddr -> offset en el archivo
phoff = struct.unpack_from("<I", b, 0x1C)[0]
phentsize, phnum = struct.unpack_from("<HH", b, 0x2A)
loads = []
for i in range(phnum):
    t, off, va, pa, fsz, msz, fl, al = struct.unpack_from("<8I", b, phoff + i * phentsize)
    if t == 1:
        loads.append((va, off, fsz))


def foff(va):
    for v, o, s in loads:
        if v <= va < v + s:
            return va - v + o
    sys.exit("direccion fuera de los segmentos cargados")


start = foff(func[1])
hits = []
for o in range(start, start + func[2] - 4, 2):
    if struct.unpack_from("<H", b, o)[0] == 0x2821:  # cmp r0, #0x21 (Thumb)
        nxt = struct.unpack_from("<H", b, o + 2)[0]
        if (nxt & 0xF000) == 0xD000 or (nxt & 0xF800) == 0xF000:  # rama condicional (16 o 32 bits)
            hits.append(o)
print("coincidencias:", [hex(h) for h in hits])
if len(hits) != 3:
    sys.exit(f"Se esperaban exactamente 3 comparaciones y hay {len(hits)}: no se parchea.")
for h in hits:
    b[h] = 0x41  # 0x2821 -> 0x2841
open(out, "wb").write(b)
print("escrito:", out, len(b), "bytes (3 bytes cambiados)")
