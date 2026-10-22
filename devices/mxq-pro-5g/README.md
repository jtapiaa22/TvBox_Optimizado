# MXQ Pro 5G (Rockchip rk322x, Android 7.1.2) - notas del dispositivo

> Nota: las rutas `tv-backup/` y `tv-launcher/` de este texto corresponden al proyecto original. En este repo el
> script de restauración está en esta carpeta y el launcher en `../../tv-launcher`.

TV box: 192.168.1.4, ADB puerto 5555 (`adb connect 192.168.1.4:5555`).

## Documentación
- `docs/LAUNCHER.md`: cómo está hecho el launcher (diseño, navegación, trampas, cómo cambiarlo).
- `docs/PROMPT-TVBOX-ANDROID-VIEJO.md`: prompt reutilizable para repetir todo en otra caja.

## Archivos
- `tv-backup/` — copia previa: `packages.txt`, `packages-disabled.txt`, `getprop.txt`, `settings-*.txt`,
  capturas y `restore.ps1`.
- `tv-launcher/` — launcher propio estilo Apple TV (Java sin dependencias, minSdk 25).
  - `build.ps1` compila a `build/launcher.apk` (aapt2 + javac + d8 + apksigner).
  - `install.ps1` compila, instala, lo deja como Home y desactiva el launcher original.

## Qué se desactivó (`pm disable-user`, reversible)
`com.adups.fota` (telemetría/OTA Adups), `com.rockchip.devicetest`, `com.example.txczdevicetest`,
`com.DeviceTest`, `com.cghs.stresstest` (pruebas de fábrica), `com.android.music`,
`com.android.calculator2`, `com.android.htmlviewer`, `com.android.printspooler`,
`com.android.wallpaper.livepicker`, `com.droidlogic.mboxlauncher` (launcher original).
No se tocaron: actualizaciones Rockchip, Miracast, navegadores, explorador, LED.
Nota: en un reinicio el firmware reactivó el launcher original una vez; el Home preferido se mantuvo.
Si pasa, repite `pm disable-user --user 0 com.droidlogic.mboxlauncher`.

## Restaurar
```powershell
.\tv-backup\restore.ps1
```
Re-activa todo y vuelve a poner el launcher original como Home. Para un solo paquete:
`adb shell pm enable <paquete>`.

## Cambiar el dock
Edita `DOCK` al inicio de `tv-launcher/src/com/jgx/launcher/Launcher.java` (paquete, etiqueta, acción
alternativa), cambia `COLS` para las columnas de la grilla y ejecuta `.\tv-launcher\install.ps1`.
El dock usa 4 apps: JGX IPTV, Downloader, Play Store, Ajustes. El resto de apps salen en la grilla.

## Uso del launcher
- Mantener OK sobre una app: menú Ocultar / Información / Desinstalar (las del sistema no se desinstalan).
- Dock editable y sin límite: en una app de la grilla, "Agregar al dock"; en una del dock, Mover
  izquierda/derecha o Quitar. Con muchas apps el dock se desplaza de lado. Se guarda en el launcher.
- Botón "Liberar RAM" (barra superior): cierra procesos en segundo plano de las demás apps y avisa cuántos
  MB se liberaron. No toca la app que tienes abierta.
- Apps recién instaladas aparecen arriba en "Recientes".
- Barra superior: Wi-Fi con nombre y velocidad, y RAM libre (se actualiza cada 5 s, sin red).
- Mantener OK sobre el botón Ajustes: restaura las apps ocultas.
- Fila "Recientes" encima de "Todas las apps" (últimas 4 abiertas fuera del dock).
- Home siempre vuelve al primer tile del dock.
- Animaciones del sistema a 0.5 (`window_animation_scale`, `transition_animation_scale`,
  `animator_duration_scale`); `restore.ps1` las devuelve a 1.0.

## Fondo
Negro puro por diseño. Para otro fondo, cambia `scroll.setBackgroundColor(Color.BLACK)` en `build()`.

## Notas
- No hay botón Inputs: la caja no tiene entradas HDMI ni TV en vivo. La tipografía es la del sistema
  (Roboto fina/ligera), no se descargó Outfit.
- Teclas físicas de apps del control remoto: no se mapearon (Button Mapper ya está instalado).

## Cerrar ADB al terminar
ADB está abierto y sin autenticación (`ro.adb.secure=0`). En la caja: Ajustes > Opciones de desarrollador >
desactiva Depuración USB, y reinicia. Comprueba que el puerto 5555 rechaza la conexión.

