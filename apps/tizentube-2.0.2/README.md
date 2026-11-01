# TizenTube Cobalt 2.0.2 (reemplazo de YouTube)

Parte de la optimización: **al final**, el asistente le pregunta al usuario *"¿Desea reemplazar YouTube por TizenTube?"* y, si
dice que sí, instala TizenTube y desinstala el YouTube de la caja. En la MXQ Pro 5G ya estaba instalado y no había YouTube
(se sacó en la limpieza), así que ahí no hubo nada que reemplazar.

- App: **TizenTube Cobalt**, de **reisxd** — <https://github.com/reisxd/TizenTubeCobalt> (licencia **BSD-3-Clause**). Es un
  YouTube basado en Cobalt que bloquea anuncios, trae SponsorBlock y control de velocidad. Este repositorio no tiene relación
  con su autor.
- Paquete `io.gh.reisxd.tizentube.cobalt` · versión `2.0.2` · código `202` · pide Android 7.0 o más · un solo APK (sin partes).
- Versión elegida: la que estaba probada en la MXQ Pro 5G. Existe una más nueva (2.0.3) que **no se probó**.

## Por qué el APK no está en el repositorio
Pesa **~93 MB** (trae el motor completo) y GitHub avisa con archivos de más de 50 MB; además quedaría en el historial para
siempre. El instalador lo **baja de la página oficial de versiones** y comprueba su hash antes de instalar:

| | |
|---|---|
| Archivo oficial | `cobalt-arm.apk` de la versión `v2.0.2` (`armeabi-v7a`) |
| URL | `https://github.com/reisxd/TizenTubeCobalt/releases/download/v2.0.2/cobalt-arm.apk` |
| Tamaño | 97.195.140 bytes |
| SHA-256 | `FF90C6E85C37246B809CCA80282910509782C1384ECCFBB6066DA3B497DD9FD2` |
| SHA-256 del certificado de firma | `378ea3935af1b95c32f6ce193f4ff7f420cdc2d7160c8c866d28d6a91042ec9a` |

Se comprobó que el APK instalado en la MXQ Pro 5G es **idéntico, byte por byte,** al archivo oficial (mismo SHA-256). Si el autor
retira esa versión, hay que cambiar la URL y el hash en `instalar-tizentube.ps1` (y probar la versión nueva).
Solo se probó en `armeabi-v7a`. Para una caja arm64 el script se detiene: el autor publica también `cobalt-arm64.apk`, pero no
se verificó.

## Uso
```powershell
.\instalar-tizentube.ps1 -Ip 192.168.1.4 -SoloDetectar          # ve qué hay (TizenTube, YouTube); no cambia nada
.\instalar-tizentube.ps1 -Ip 192.168.1.4                        # instala TizenTube; NO toca YouTube
.\instalar-tizentube.ps1 -Ip 192.168.1.4 -ReemplazarYouTube     # instala TizenTube y después saca YouTube
```
Otras opciones: `-Reinstalar` (instala encima aunque ya esté; `install -r` conserva los datos), `-Apk <archivo>` (usa un APK ya
bajado, igual se verifica el hash), `-Descargas <carpeta>`, `-Respaldo <carpeta>`.

### Lo que hace `-ReemplazarYouTube`, en este orden
1. Instala TizenTube (baja el APK, verifica el SHA-256, `adb install -r`) y **comprueba que quedó instalado**. Si algo falla, se
   detiene y **YouTube no se toca**.
2. Por cada YouTube encontrado (`com.google.android.youtube.tv` y `com.google.android.youtube`; **no** YouTube Music, Kids ni
   YouTube TV de pago), guarda su APK en la carpeta de respaldo (`respaldo-youtube\<paquete>\`) y comprueba que se copió.
3. Lo desinstala: app de **sistema** → `pm uninstall --user 0` (reversible: `adb shell cmd package install-existing <paquete>`);
   app de **usuario** → `pm uninstall` (se restaura con `adb install-multiple` de los APK del respaldo).

## Qué se probó y qué no (en la MXQ Pro 5G, 2026-11-01)
Probado: detectar sin cambiar nada; no reinstalar cuando ya está; **rechazar un archivo corrupto** (no instala); bajar el APK
oficial, verificar el hash e instalarlo encima conservando la app; y el camino de **respaldar y desinstalar una app de usuario**
(con una app de prueba, porque la caja no tiene YouTube).
**No probado:** quitar un YouTube *real* (ni el de sistema con `--user 0`, ni sus variantes) porque en esta caja no existe;
las órdenes son las mismas que se usaron en la limpieza de otras apps de sistema, pero este camino exacto no se corrió con YouTube.
