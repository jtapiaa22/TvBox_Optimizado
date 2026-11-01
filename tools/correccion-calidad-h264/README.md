# Corrección de calidad de video (H.264 High) en cajas con Android 7 — extra, NO es parte de la optimización

Corrige que apps con reproductor Media3/ExoPlayer (XtremeTV, JGX, etc.) **no ofrezcan las calidades altas** (720p/1080p) en
canales cuyo video viene en H.264 *High* (DAZN, Telefe…). Se hace una sola vez en la caja y sirve para **todas** las apps:
no hay que parchear cada app ni repetirlo cuando sale una versión nueva.

Probado en la MXQ Pro 5G (RK3229, `rk322x_box-userdebug 7.1.2 NHG47K eng.ROCKCHIP.20200316.015029`). Con el arreglo, XtremeTV
v36 **sin modificar** pasó de una sola calidad baja a ofrecer 720p/576p/480p/360p/240p en Telefe, y la persona vio
Telefe y DAZN F1 en 1080p. No está probado en otras cajas.

## Síntoma
Un canal ofrece un solo formato de baja calidad y la flecha arriba (menú de calidades) no deja elegir. En el log
(`adb logcat`) el reproductor deja líneas como:
```
MediaCodecInfo: NoSupport [codec.profileLevel, avc1.64001F, video/avc] [OMX.rk.video_decoder.avc.secure, video/avc] ...
```
`avc1.64001F` es H.264 High 3.1 (720p) y `avc1.640028` es High 4.0 (1080p): el reproductor las descarta.

## Causa (comprobada)
- La app le pregunta al sistema qué perfiles/niveles de H.264 acepta cada decodificador. Esta caja respondía Baseline y Main
  completos y, de High, **solo el nivel 1** (tamaño de estampilla). Se ve con `CodecInfo.java` (abajo): `perfil:nivel` terminaba
  en `8:1`.
- El chip **sí** decodifica High: `DecodeTest.java` decodificó 3 s de video de prueba High 720p y 1080p con el decodificador de
  hardware, sin errores y a la misma velocidad que Main. No es una limitación del hardware sino de lo que se informa.
- La librería de Rockchip (`libomxvpu_dec.so`) **sí tiene la tabla completa**: 16 niveles de Baseline, 16 de Main y 16 de High
  (48 entradas, en `0x14010–0x14190`). Pero Android 7 (`ACodec::queryCapabilities`, en `libstagefright.so`) pregunta como mucho
  por 33 posiciones (`kMaxIndicesToCheck = 32`; en el log, "stopping checking profiles after 32"). 16 + 16 + 1 = 33: la lista se
  corta justo después del primer nivel de High.
- Esos 33 aparecen en el código como tres bucles `index+1; cmp r0, #0x21; blo` (instrucción Thumb `0x2821`).

## Arreglo: 3 bytes
`parchear_libstagefright.py` cambia `cmp r0, #0x21` por `cmp r0, #0x41` (tope de 65 posiciones) en esos tres bucles. Ubica la
función por su símbolo y **se niega** a tocar la librería si no encuentra exactamente 3 coincidencias seguidas de una rama
condicional (otra versión de Android, ARM en vez de Thumb, otra librería).
```powershell
adb pull /system/lib/libstagefright.so libstagefright.so            # RESPALDALA ANTES DE NADA
python -I parchear_libstagefright.py libstagefright.so libstagefright-patched.so
```
En la MXQ Pro 5G: original SHA-256 `0d9d1620940e09a65c84bb4aa6425f5a4e77846d1ebc6afc07071fe1fdeb27de` → parcheada
`a38728c23148e20a5e2441d977f27beaa8ab815ea974984bea6d8548b1511c40` (offsets de archivo `0x84620`, `0x8491a`, `0x849ea`; el script
generó un archivo idéntico byte por byte al que se instaló a mano). Las librerías no están en el repo (son del firmware de cada caja).

## Instalación (necesita ADB root un momento; se cierra al terminar)
Compará primero que la librería de la caja sea la que respaldaste y que tu caja tenga el mismo problema (paso "Diagnóstico").
```powershell
adb root ; adb connect <ip>:5555
adb remount
adb shell "cp /system/lib/libstagefright.so /data/local/tmp/libstagefright.so.orig"     # copia dentro de la caja
adb push libstagefright-patched.so /system/lib/libstagefright.so
adb shell "chmod 644 /system/lib/libstagefright.so ; chown root:root /system/lib/libstagefright.so ; sync"
adb pull /system/lib/libstagefright.so verificar.so     # comparar el hash con el del PC (la caja no trae sha256sum)
adb shell "setprop ctl.restart media ; setprop ctl.restart mediacodec ; setprop ctl.restart mediaextractor"
adb unroot
```
Después reiniciá la caja una vez para confirmar que arranca normal (en la MXQ Pro 5G arrancó en ~30 s). `/system` vuelve solo
a lectura con el reinicio.

## Diagnóstico y verificación
Programas chicos de solo lectura que corren con `app_process` (sin instalar nada en la caja). Compilan con el SDK y un JDK:
```powershell
javac --release 8 -cp <android.jar> -d cls CodecInfo.java DecodeTest.java
d8 --min-api 24 --output out cls\CodecInfo.class cls\DecodeTest.class
adb push out\classes.dex /data/local/tmp/diag.dex
adb shell "cd /data/local/tmp && app_process -Djava.class.path=/data/local/tmp/diag.dex /system/bin CodecInfo"
adb shell "cd /data/local/tmp && app_process -Djava.class.path=/data/local/tmp/diag.dex /system/bin DecodeTest /data/local/tmp/prueba.mp4 OMX.rk.video_decoder.avc"
```
- `CodecInfo` lista, por decodificador de H.264/HEVC, los perfiles y niveles que informa. Perfil `8` = High. **Antes** del arreglo
  terminaba en `8:1`; **después** llega a `8:8000` (nivel 5.1), en los decodificadores de hardware, el seguro y el de software.
- `DecodeTest` decodifica un archivo directo con un decodificador por nombre, sin el chequeo de perfil/nivel de las apps. Sirve
  para saber si el chip *puede* con un formato aunque no lo anuncie. Su salida copia los cuadros al procesador, así que **no mide
  si llega a tiempo real**; solo que decodifica bien. Videos de prueba propios: `ffmpeg -f lavfi -i testsrc2=size=1920x1080:rate=30 -t 3 -c:v libx264 -profile:v high -level 4.0 -pix_fmt yuv420p t.mp4`.
- En el log de una app Media3, tras el arreglo ya no aparecen líneas `NoSupport … codec.profileLevel`.

## Volver atrás
`adb root`, `adb remount`, `adb push libstagefright.so /system/lib/libstagefright.so` (el respaldo), `chmod 644`, y reiniciar. La
caja sigue arrancando con ADB por Wi-Fi aunque falle la reproducción, porque lo único que cambia es una constante en un bucle.

## Riesgos y límites
- `libstagefright.so` la usa todo lo que reproduce audio o video. Cambiar una constante es poco probable que lo rompa, pero es una
  librería del sistema: **respaldala antes**.
- Un firmware nuevo la pisa y vuelve el problema (se repite el procedimiento, el script la vuelve a ubicar sola).
- Con el arreglo las apps pueden elegir una calidad que el chip no mueva en tiempo real; en las pruebas de la MXQ Pro 5G no se
  vio ese problema, pero 1080p a 60 cuadros no se probó.

## Plan B: parchear la app (no hace falta si se hizo lo de arriba)
`parchear_app_media3.py` hace que `MediaCodecInfo.isCodecProfileAndLevelSupported` de Media3 devuelva siempre `true`, dentro de
una app concreta (cambia 2 instrucciones del `.dex` y recalcula checksum y firma del dex; hay que volver a firmar el APK con
`apksigner` y la misma clave que la instalada). Es por app y hay que repetirlo en cada versión: por eso se prefirió el arreglo de
sistema. Uso: `python -I parchear_app_media3.py in.apk out-sin-firmar.apk "Landroidx/media3/exoplayer/mediacodec/MediaCodecInfo;" isCodecProfileAndLevelSupported`.

## Lo que NO se arregló
El cierre de XtremeTV por `java.time` en la Grilla (Android 7 no trae esa librería) **no se corrige a nivel sistema** (obligaría a
rehacer la imagen de arranque de Android). La versión 36 de XtremeTV no mostró ese problema en las pruebas, y no se parcheó.
