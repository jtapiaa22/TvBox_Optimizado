# ver-tvbox: ver la pantalla del TV box en el PC, con sonido (experimental)

> **No forma parte de la optimización de la caja.** Es una herramienta aparte para quien trabaja con la caja por ADB.
> No instala nada permanente en ella: solo corre mientras la ventana está abierta. La caja funciona igual sin esto.

Para cuando no tienes una tele a mano: muestra por Wi-Fi la imagen de la caja (como una capturadora) y su sonido, usando
solo lo que ya hay: ADB por red, `screenrecord` de la caja y `ffplay` del PC. Probado en una MXQ Pro 5G (Android 7.1.2).

```
.\ver-tvbox.cmd                       # imagen + sonido
.\ver-tvbox.ps1 -SinAudio             # solo imagen, sin root
.\ver-tvbox.ps1 -Suave                # imagen más fluida a cambio de ~1,5 s de retraso (el audio se atrasa lo mismo)
.\ver-tvbox.ps1 -RetrasoAudioMs 400   # si el sonido llega antes que la imagen
```
Cierra la ventana "TV box (ip)" para terminar: el script corta todo, también en la caja.

## Requisitos
- ADB abierto en la caja (`adb connect <ip>:5555`).
- `ffplay` en el PC (por ejemplo `winget install Gyan.FFmpeg`). La ruta está al inicio del script.
- Para el audio: firmware que permita `adb root` (userdebug) y una política de audio con `r_submix`
  (`/system/etc/audio_policy.conf`, módulo `audio.r_submix.default.so`).

## Cómo funciona
- **Imagen**: `adb exec-out screenrecord --output-format=h264 ... | ffplay`. Android limita `screenrecord` a 3 minutos: el
  script lo reinicia solo (la ventana parpadea un instante).
- **Audio**: Android 7 no trae captura de audio de salida para el usuario. `tv-audio/AudioCap.java` lee la entrada
  `REMOTE_SUBMIX` con `AudioRecord`, se compila a dex (`javac --release 8` + `d8`) y se corre con `app_process` **como root**
  (el usuario `shell` no tiene el permiso). Escribe PCM 16 bits, 48 kHz, estéreo por la salida estándar.
- **La tele no se queda sin sonido**: al capturar, Android crea un hilo `DUPLICATING` y sigue enviando el audio por HDMI
  (medido con `dumpsys media.audio_flinger`).

## Qué se midió
- Con video en movimiento, `screenrecord` entregó el ritmo completo del canal (25 cuadros por segundo) a 1280×720 y 3 Mbps,
  con ~20 % de CPU de la caja y unos 2 Mbps de red. Con la pantalla quieta casi no manda cuadros (es normal).
- 854×480 no lo acepta el codificador de esa caja. 960×540 bajó a ~22 fps.
- Imagen y audio van por caminos distintos: en la prueba se vieron sincronizados sin ajustar nada.
- **No se midió** la fluidez a ojo: si se ve algo trabado, probar `-Suave`.

## Seguridad
Mientras la ventana está abierta, **la caja tiene root por ADB** y el ADB no pide autorización: cualquiera en la red podría
conectarse como root. Al cerrar se ejecuta `adb unroot`. Con `-SinAudio` no se usa root.

## Trampas con las que se tropezó (no repetir)
- En PowerShell 5.1, `"$var:texto"` se lee como variable con unidad de disco: usar `"${var}: texto"`.
- Un `|` dentro de un `.cmd` corta el comando como una tubería: `-af "adelay=1500|1500"` va **entre comillas**.
- Redirigir un binario con `>` en PowerShell 5.1 lo corrompe: encadenar `adb | ffplay` dentro de un `.cmd`.
- Este `ffplay` (ffmpeg 9) ya no acepta `-ac`: usar `-sample_rate 48000 -ch_layout stereo`.
- `head -c` no existe en el Android 7 de estas cajas: usar `dd bs=N count=1`.
- Al cerrar la ventana, el flujo y `screenrecord` de la caja siguen hasta los 3 minutos si no se matan: el script los corta.
