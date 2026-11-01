# Animación de arranque personalizada (extra, NO es parte de la optimización)

Personalización: cambia lo que se ve al encender la caja. No hace falta para optimizar nada y la caja funciona igual sin esto.
Hecho en la MXQ Pro 5G (RK3229, Android 7.1.2, pantalla 1280×720).

La animación: primero aparecen las letras rojas **MESSI** sobre negro (con un zoom suave), después **Leo** con los brazos
abiertos va apareciendo de a poco, y queda en un "latido" suave de brillo hasta que el sistema termina de arrancar.

## Cómo está hecho (hechos comprobados en esa caja)
- El arranque lee **solo** `/system/media/bootanimation.zip`. Copiarlo a `/data/local/` **no sirve** (ahí el binario solo
  busca un video `.ts`). Había un solo archivo, de fábrica: el logo "MBOX – Make your life colorful" (la carpeta que lo
  contiene se llama `android`, pero adentro está el logo MBOX).
- El zip **tiene que ir sin comprimir** (método *stored*): el binario avisa "bootanimation.zip is compressed; must be only
  stored". Por eso `make-zip.py` usa `ZIP_STORED`.
- `desc.txt`: `1280 720 20` (ancho, alto, cuadros por segundo) y una línea `p VECES PAUSA carpeta` por parte. Aquí:
  `p 1 8 texto` (una vez, sostiene 8 cuadros), `p 1 0 leo` (una vez), `p 0 0 loop` (en bucle hasta que arranca).
- Cada cuadro es un PNG de 1280×720 y se carga como textura: son 12 + 18 + 20 cuadros (unos 25 MB en total).
- **No se toca** el primer logo que aparece apenas se enciende: está en la partición de arranque del chip (`uboot`/`resource`)
  y un error ahí puede dejar la caja sin encender.

## Generarla
Hace falta la imagen (no está en el repo): un arte con letras **rojas** sobre fondo negro y el dibujo en blanco y negro. El
script separa el rojo (capa de texto) del resto (el dibujo) y mezcla de una a otra.
```powershell
.\make-frames.ps1 -Src arte.jpg -Out frames          # cuadros PNG (usa .NET, sin instalar nada)
python -I make-zip.py frames bootanimation-messi.zip # zip sin comprimir
```
Las letras que el dibujo tapa en el original no se pueden recuperar: en la primera fase se ve la silueta de la figura
recortada dentro de las letras, y después el dibujo la llena.

## Instalarla (necesita ADB root: se abre un momento y se cierra)
1. **Respaldá el original primero**: `adb pull /system/media/bootanimation.zip bootanimation-original.zip`.
2. `adb root` · `adb remount` · `adb push bootanimation-messi.zip /system/media/bootanimation.zip`
3. `adb shell chmod 644 /system/media/bootanimation.zip` · `adb unroot`
4. Verificá trayéndolo de vuelta (`adb pull`) y comparando el hash: la caja no tiene `sha256sum`.

## Probarla sin reiniciar
Con root: `adb shell "setprop service.bootanim.exit 0; setprop ctl.start bootanim"`, sacar capturas con `screencap`, y al
terminar `setprop service.bootanim.exit 1`. (Si no se pone `exit 0`, el servicio se cierra al instante porque el sistema
ya arrancó.)

## Volver al original
Con el mismo procedimiento (`adb root`, `adb remount`, `adb push` del `bootanimation-original.zip` guardado). No hay forma
de recuperar el original de la caja si no se respaldó antes.

## Estado
Probada con el servicio de animación (capturas) y en un **reinicio real** de la MXQ Pro 5G: la persona la vio en el
televisor al encender, y la caja arrancó normal (~25 s, `/system` de vuelta en solo lectura, launcher al terminar).
No probada en otros modelos de caja.
