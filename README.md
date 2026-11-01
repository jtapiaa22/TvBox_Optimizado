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

## La optimización de una caja (esto es lo que se repite en cada caja)
Resumen de lo hecho en la MXQ Pro 5G (detalles y comandos en [`devices/mxq-pro-5g/README.md`](devices/mxq-pro-5g/README.md)):
1. **Respaldo y debloat**: apps de fábrica, de prueba, telemetría (Adups) y navegadores se desactivan y luego se quitan.
2. **Launcher propio** (`tv-launcher/`) en lugar del original, que no se vuelve a activar al reiniciar.
3. **Play Store y Play Services apagados** (`devices/<modelo>/play-services.ps1`): ~300 MB de RAM en una caja de 1 GB.
   Antes se instala **Downloader 1.5.1** ([`apps/downloader-1.5.1/`](apps/downloader-1.5.1/README.md)) para poder instalar apps.
4. **Pregunta final: ¿reemplazar YouTube por TizenTube?** Si el usuario dice que sí, se instala **TizenTube**
   ([`apps/tizentube-2.0.2/`](apps/tizentube-2.0.2/README.md)) y recién después se desinstala YouTube (con respaldo).

## Extras aparte (NO forman parte de la optimización)
Cosas que se hicieron en el camino y sirven en casos puntuales. No hace falta nada de esto para optimizar una caja, y la
caja funciona igual sin ellos:
- [`tools/ver-tvbox/`](tools/ver-tvbox/README.md): ver la pantalla de la caja en el PC, con sonido (experimental). Es una
  herramienta para quien trabaja con la caja por ADB: no instala nada permanente en ella.
- [`docs/PARCHEAR-JAVA-TIME.md`](docs/PARCHEAR-JAVA-TIME.md): arreglo para una app ajena que se cae en Android 7 por `java.time`.
- [`tools/correccion-calidad-h264/`](tools/correccion-calidad-h264/README.md): arreglo a nivel sistema para que las apps ofrezcan
  720p/1080p (H.264 High) en cajas con Android 7 (parche de 3 bytes en `libstagefright.so`, con diagnóstico y vuelta atrás).
- [`tools/bootanimation-messi/`](tools/bootanimation-messi/README.md): animación de arranque personalizada (solo estética).
- WebView 55 → 114: solo si una app necesita páginas modernas (ver "Extras" en las notas de la caja). No hacía falta.

## Qué es
- `tv-launcher/` — el launcher. Un archivo Java, UI por código, se compila con `build.ps1`
  (aapt2 + javac + d8 + apksigner) y se instala con `install.ps1 <ip>`.
  - Un único dock desplazable con todas las apps: las nuevas entran solas al principio y se pueden ocultar y
    recuperar (mantener OK sobre una app, o sobre "Ajustes" para ver las ocultas). Sin grilla ni recientes.
  - Vidrio de resorte entre tiles, brillo del color del ícono, navegación D-pad determinista.
  - Barra superior: Liberar RAM, Wi-Fi con velocidad, Ajustes de Android TV, línea de RAM.
- `docs/` — documentación técnica y el prompt reutilizable.
- `devices/<modelo>/` — lo hecho en cada caja concreta.

## Requisitos en el PC
Windows con PowerShell, Android SDK (platform-tools, build-tools y una plataforma de API 25 o más; basta con instalar
Android Studio) y un JDK 17 o más nuevo. `tv-launcher/build.ps1` detecta solo las versiones instaladas; no hace falta Python.
La caja y el PC, en la misma red, con la depuración ADB activada.

## Empezar desde otra computadora (con una caja nueva)
1. **Prepara el PC**: instala Android Studio (trae el SDK y un JDK) o el SDK y un JDK por separado, y `git`.
2. **Clona el repo** y abre ahí tu asistente (Claude Code u otro con acceso a la shell):
   ```
   git clone https://github.com/jtapiaa22/TvBox_Optimizado.git
   ```
3. **En la caja**: Ajustes → Información → toca 7 veces "Número de compilación" → Opciones de desarrollador → activa la
   depuración USB/ADB. Anota su IP (Ajustes → Red).
4. **Dile al asistente**: "Lee `README.md` y `docs/PROMPT-TVBOX-ANDROID-VIEJO.md`. Mi caja es `<modelo>`, IP `<ip>`."
   El prompt le indica el orden de trabajo y las reglas (chequear antes de hacer, respaldar, preguntarte antes de tocar).
5. **Lo que NO viene en el repo y se hace de nuevo para cada caja**: el respaldo de sus paquetes y de las apps de `/system`
   (se guarda en tu PC), la carpeta `devices/<modelo>/` con sus notas, IP y `restore.ps1`, y el dock del launcher: edita la
   constante `DOCK` de `Launcher.java` con las apps de esa caja (la de ejemplo trae `com.jgx.iptv`, que es una app privada, y
   Play Store, que se apaga en la optimización).

Los scripts de `devices/mxq-pro-5g/` traen la IP de esa caja por defecto: pásale la de la tuya con `-Ip`
(`play-services.ps1`, `restore.ps1`, `apps/downloader-1.5.1/instalar-downloader.ps1`, `apps/tizentube-2.0.2/instalar-tizentube.ps1`). `restore.ps1` también necesita
`-Respaldo <carpeta>` con el respaldo que hiciste de tu caja.

## Uso rápido
```powershell
adb connect 192.168.1.X:5555
.\tv-launcher\install.ps1 192.168.1.X
```
Antes de instalar, edita la constante `DOCK` de `tv-launcher/src/com/jgx/launcher/Launcher.java` con los paquetes
de tus apps. Para volver atrás en una caja: `devices/<modelo>/restore.ps1`.

`install.ps1` busca solo el launcher original de la caja (las apps que responden a HOME), te lo **muestra** y no desactiva
nada si no se lo pides: `.\tv-launcher\install.ps1 <ip> -LauncherOriginal <paquete>` (o `-DesactivarOriginal` si hay uno solo).
`-SoloDetectar` solo lista los candidatos, sin compilar ni instalar. Es reversible con `adb shell pm enable <paquete>`.

## Aviso
Modificar el firmware/paquetes de un dispositivo es bajo tu responsabilidad. Lo que se desactiva es reversible
(`pm enable`); lo que se borra de `/system` solo se recupera con el respaldo que hagas antes (el `restore.ps1` que lo
vuelve a copiar **no está probado**). Haz siempre el respaldo primero y no toques paquetes críticos del sistema.

`apps/downloader-1.5.1/` guarda una copia de una app de otra persona (AFTVnews): ver su README sobre la licencia.
`apps/tizentube-2.0.2/` no guarda la app: la baja de la página de su autor (reisxd, BSD-3-Clause) y verifica su hash.
