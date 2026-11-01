param([string]$Src, [string]$Out)
# Animacion: 1) letras rojas MESSI aparecen sobre negro, 2) Leo aparece de a poco, 3) bucle de "respiracion" hasta que arranca.
Add-Type -AssemblyName System.Drawing
Add-Type -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
public static class Mix {
    // Capa de texto: solo el rojo (r - min(g,b)) del original; el dibujo blanco/gris queda en negro.
    public static Bitmap TextOnly(Bitmap src) {
        Bitmap o = new Bitmap(src.Width, src.Height, PixelFormat.Format24bppRgb);
        Rectangle r = new Rectangle(0, 0, src.Width, src.Height);
        BitmapData a = src.LockBits(r, ImageLockMode.ReadOnly, PixelFormat.Format24bppRgb);
        BitmapData b = o.LockBits(r, ImageLockMode.WriteOnly, PixelFormat.Format24bppRgb);
        byte[] ab = new byte[a.Stride * a.Height]; byte[] bb = new byte[b.Stride * b.Height];
        Marshal.Copy(a.Scan0, ab, 0, ab.Length);
        for (int y = 0; y < src.Height; y++) for (int x = 0; x < src.Width; x++) {
            int i = y * a.Stride + x * 3; int B = ab[i], G = ab[i + 1], R = ab[i + 2];
            int red = R - Math.Min(G, B); if (red < 0) red = 0;
            // el rojo del arte es ~puro: se descartan los grises y se reforzaron los bordes
            bb[y * b.Stride + x * 3 + 2] = (byte)red;
        }
        Marshal.Copy(bb, 0, b.Scan0, bb.Length);
        src.UnlockBits(a); o.UnlockBits(b); return o;
    }
    public static Bitmap Lerp(Bitmap p, Bitmap q, double t) {
        Bitmap o = new Bitmap(p.Width, p.Height, PixelFormat.Format24bppRgb);
        Rectangle r = new Rectangle(0, 0, p.Width, p.Height);
        BitmapData a = p.LockBits(r, ImageLockMode.ReadOnly, PixelFormat.Format24bppRgb);
        BitmapData b = q.LockBits(r, ImageLockMode.ReadOnly, PixelFormat.Format24bppRgb);
        BitmapData c = o.LockBits(r, ImageLockMode.WriteOnly, PixelFormat.Format24bppRgb);
        byte[] ab = new byte[a.Stride * a.Height], bb = new byte[b.Stride * b.Height], cb = new byte[c.Stride * c.Height];
        Marshal.Copy(a.Scan0, ab, 0, ab.Length); Marshal.Copy(b.Scan0, bb, 0, bb.Length);
        for (int i = 0; i < cb.Length; i++) cb[i] = (byte)Math.Round(ab[i] + (bb[i] - ab[i]) * t);
        Marshal.Copy(cb, 0, c.Scan0, cb.Length);
        p.UnlockBits(a); q.UnlockBits(b); o.UnlockBits(c); return o;
    }
}
'@ -ReferencedAssemblies System.Drawing

$W = 1280; $H = 720
function Fit($img) {   # la imagen a 1280x720 exactos (cubre la pantalla)
    $bmp = New-Object System.Drawing.Bitmap $W, $H, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp); $g.Clear([System.Drawing.Color]::Black)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $c = [math]::Max($W / $img.Width, $H / $img.Height); $dw = $img.Width * $c; $dh = $img.Height * $c
    $g.DrawImage($img, [single](($W - $dw) / 2), [single](($H - $dh) / 2), [single]$dw, [single]$dh); $g.Dispose(); $bmp
}
function Zoom($bmp, [double]$scale, [double]$bright, [string]$path) {
    $o = New-Object System.Drawing.Bitmap $W, $H, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [System.Drawing.Graphics]::FromImage($o); $g.Clear([System.Drawing.Color]::Black)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $m = New-Object System.Drawing.Imaging.ColorMatrix
    $m.Matrix00 = [single]$bright; $m.Matrix11 = [single]$bright; $m.Matrix22 = [single]$bright; $m.Matrix33 = [single]1; $m.Matrix44 = [single]1
    $ia = New-Object System.Drawing.Imaging.ImageAttributes; $ia.SetColorMatrix($m)
    $dw = $W * $scale; $dh = $H * $scale
    $rect = New-Object System.Drawing.Rectangle ([int](($W - $dw) / 2)), ([int](($H - $dh) / 2)), ([int]$dw), ([int]$dh)
    $g.DrawImage($bmp, $rect, 0, 0, $W, $H, [System.Drawing.GraphicsUnit]::Pixel, $ia); $g.Dispose()
    $o.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $o.Dispose()
}

$raw = [System.Drawing.Image]::FromFile($Src)
$full = Fit $raw; $raw.Dispose()
$text = [Mix]::TextOnly($full)
if (Test-Path $Out) { Remove-Item $Out -Recurse -Force }
New-Item -ItemType Directory -Force "$Out\texto", "$Out\leo", "$Out\loop" | Out-Null
$text.Save("$Out\_solo-texto.png", [System.Drawing.Imaging.ImageFormat]::Png)

# 1) texto: 12 cuadros, las letras entran desde negro con un zoom suave 1.10 -> 1.00 (se sostiene el ultimo con "pausa" en desc.txt)
$n = 12
for ($i = 0; $i -lt $n; $i++) {
    $t = $i / ($n - 1); $e = 1 - [math]::Pow(1 - $t, 3)
    Zoom $text (1.10 - 0.10 * $e) $e ("{0}\texto\{1:D3}.png" -f $Out, $i)
}
# 2) leo: 18 cuadros, del texto solo al arte completo (Leo aparece de a poco)
$n = 18
for ($i = 0; $i -lt $n; $i++) {
    $t = ($i + 1) / $n; $e = $t * $t * (3 - 2 * $t)
    $f = [Mix]::Lerp($text, $full, $e)
    Zoom $f 1.0 1.0 ("{0}\leo\{1:D3}.png" -f $Out, $i); $f.Dispose()
}
# 3) bucle: 20 cuadros, arranca en el mismo brillo/escala con que termina "leo" y vuelve a ellos sin saltos
$n = 20
for ($i = 0; $i -lt $n; $i++) {
    $q = (1 + [math]::Cos(2 * [math]::PI * $i / $n)) / 2
    Zoom $full (1.012 - 0.012 * $q) (0.86 + 0.14 * $q) ("{0}\loop\{1:D3}.png" -f $Out, $i)
}
$full.Dispose(); $text.Dispose()
"cuadros: " + (Get-ChildItem $Out -Recurse -File -Include '0*.png').Count
