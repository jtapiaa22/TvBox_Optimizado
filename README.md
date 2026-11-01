# tvbox-launcher

Limpiar un TV box Android viejo (7.x) y reemplazar su launcher por uno propio, limpio y estilo Apple TV.
Todo se hace por ADB desde un PC con Windows y se verifica con capturas de pantalla de la caja.

![](https://img.shields.io/badge/Android-7.1%2B-green) minSdk 25 · Java sin dependencias · sin Gradle

## Si eres un asistente de IA (Claude Code u otro con shell)
Lee en este orden y luego sigue el prompt:
1. [`docs/PROMPT-TVBOX-ANDROID-VIEJO.md`](docs/PROMPT-TVBOX-ANDROID-VIEJO.md) — procedimiento completo, reglas
   de seguridad del debloat y trampas ya conocidas.
2. [`docs/LAUNCHER.md`](docs/LAUNCHER.md) — cómo está hecho el launcher y qué constante cambiar para cada cosa.
3. [`devices/`](devices/) — notas, lista de paquetes desactivados y script de restauración de cada caja ya hecha.

Reglas rápidas: trabaja sobre el dispositivo real, **primero chequea y después haz**, respalda antes de tocar,
desactiva con `pm disable-user` y verifica que todo funcione. Quitar apps de sistema del todo (`pm uninstall --user 0` y
borrarlas de `/system`) solo con respaldo previo en el PC y confirmación del usuario; no desactives paquetes críticos.
No abras apps de fábrica/test, verifica con capturas, no pruebes abriendo apps con sesiones de personas, y cierra el
ADB al terminar.

## Qué incluye la optimización de una caja
Resumen de lo hecho en la MXQ Pro 5G (detalles y comandos en [`devices/mxq-pro-5g/README.md`](devices/mxq-pro-5g/README.md)):
1. **Respaldo y debloat**: apps de fábrica, de prueba, telemetría (Adups) y navegadores se desactivan y luego se quitan.
2. **Launcher propio** (`tv-launcher/`) en lugar del original, que no se vuelve a activar al reiniciar.
3. **Play Store y Play Services apagados** (`devices/<modelo>/play-services.ps1`): ~300 MB de RAM en una caja de 1 GB.
   Antes se instala **Downloader 1.5.1** ([`apps/downloader-1.5.1/`](apps/downloader-1.5.1/README.md)) para poder instalar apps.
4. **WebView actualizado** (55 → 114) cuando una app necesita páginas modernas.
5. **Apps que se caen en Android 7 por `java.time`**: [`docs/PARCHEAR-JAVA-TIME.md`](docs/PARCHEAR-JAVA-TIME.md).
6. **Ver la caja desde el PC con imagen y sonido** (experimental): [`tools/ver-tvbox/`](tools/ver-tvbox/README.md).

## Qué es
- `tv-launcher/` — el launcher. Un archivo Java, UI por código, se compila con `build.ps1`
  (aapt2 + javac + d8 + apksigner) y se instala con `install.ps1 <ip>`.
  - Dock desplazable y editable (mantener OK sobre una app), fila de recientes, grilla de 4 columnas.
  - Vidrio de resorte entre tiles, brillo del color del ícono, navegación D-pad determinista.
  - Barra superior: Liberar RAM, Wi-Fi con velocidad, Ajustes de Android TV, línea de RAM.
- `docs/` — documentación técnica y el prompt reutilizable.
- `devices/<modelo>/` — lo hecho en cada caja concreta.

## Requisitos en el PC
Android SDK (platform-tools, build-tools 35, platforms/android-35), un JDK y Python 3 en el PATH. Las rutas
están al inicio de `tv-launcher/build.ps1`. La caja y el PC, en la misma red, con la depuración ADB activada.

## Uso rápido
```powershell
adb connect 192.168.1.X:5555
.\tv-launcher\install.ps1 192.168.1.X
```
Antes de instalar, edita la constante `DOCK` de `tv-launcher/src/com/jgx/launcher/Launcher.java` con los paquetes
de tus apps. Para volver atrás en una caja: `devices/<modelo>/restore.ps1`.

## Aviso
Modificar el firmware/paquetes de un dispositivo es bajo tu responsabilidad. Lo que se desactiva es reversible
(`pm enable`); lo que se borra de `/system` solo se recupera con el respaldo que hagas antes (el `restore.ps1` que lo
vuelve a copiar **no está probado**). Haz siempre el respaldo primero y no toques paquetes críticos del sistema.

`apps/downloader-1.5.1/` guarda una copia de una app de otra persona (AFTVnews): ver su README sobre la licencia.
