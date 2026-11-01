# Launcher estilo Apple TV para TV boxes con Android viejo (7.x)

Documentación técnica de `tv-launcher/`. Un solo archivo Java (`src/com/jgx/launcher/Launcher.java`), sin
AndroidX, sin Gradle, sin recursos XML: toda la UI se construye por código. Pensado para cajas con Android
7.1 (SDK 25), ~1-2 GB de RAM y control remoto con D-pad.

## Por qué existe
El launcher de referencia (otro repositorio) pide Android 8+ (minSdk 26). En Android 7.1 responde
`INSTALL_FAILED_OLDER_SDK`. Se escribió uno propio con `minSdk 25 / targetSdk 25`. Al apuntar a SDK 25 no hace
falta declarar `<queries>` (eso es de Android 11+); si algún día se sube el target a 30+, hay que declarar
`<queries>` para `LAUNCHER` y `LEANBACK_LAUNCHER` o el dock sale vacío.

## Compilación (sin Gradle)
`build.ps1`: `aapt2 link` (solo manifiesto) → `javac --release 8` → `d8 --min-api 25` → agrega `classes.dex`
al APK con .NET (`System.IO.Compression`; no hace falta Python) → `zipalign` → `apksigner` con un keystore de depuración
(`debug.keystore`, se crea solo). Requisitos en el PC: Android SDK con build-tools y una plataforma de API 25 o más, y un JDK.
El script **detecta solo** lo más nuevo que haya instalado (`ANDROID_HOME`/`ANDROID_SDK_ROOT`/ruta de Android Studio para el
SDK; `JAVA_HOME`, `~/.jdks`, el `PATH` o el JBR de Android Studio para el JDK) y lo imprime al empezar. Probado con
build-tools 35 y 36, plataformas android-35 y android-37.0 y un JDK 25. Los avisos de javac/keytool/apksigner salen por stderr y
PowerShell los muestra como error: por eso `$ErrorActionPreference = "Continue"` y se revisa `$LASTEXITCODE`.
`install.ps1 [ip]` compila, instala, fija Home y desactiva el launcher original.

## Pantalla
- Fondo negro con un brillo ambiental continuo (capa fija detrás del scroll) que se anima, en posición y color,
  hacia el tile enfocado. Arriba a la izquierda: saludo, hora grande (98.8), fecha. Arriba a la derecha: botones
  con ícono "Liberar RAM", "Wi-Fi · red · Mbps" (o "Cable" si la red activa es Ethernet) y "Ajustes", y debajo una
  línea "RAM libre N MB". Los íconos se dibujan por código (`PillIcon`), sin recursos.
- Dock: banda redondeada abajo con tiles de 240×135 dp; sobre el dock, el nombre de la app enfocada en grande
  y un brillo ambiental del color promedio del ícono (muestreado a 24×24 px).
- No hay grilla ni "Recientes": **todas las apps viven en el dock** (ver "Dock editable y desplazable").
- Un único "vidrio" (View translúcido) se desliza con resorte (`OvershootInterpolator`) entre tiles. El tile
  enfocado crece 1.1× y su nombre se muestra grande sobre el dock.
- El nombre grande de la app enfocada va en **negrita cursiva** (`Typeface.BOLD_ITALIC` de Roboto). Se probó convertir las
  letras a los caracteres Unicode "Mathematical Bold Italic" (𝑨𝑩𝑪…) y en la MXQ Pro 5G (Android 7.1) salen como cuadraditos
  porque la fuente del sistema no los trae: no usar caracteres matemáticos para dar estilo, usar el `Typeface`.
- Saludo, hora, fecha y la línea "RAM libre" van en cursiva "media" (`sans-serif-medium` + `ITALIC`): misma familia que el
  nombre de la app pero más liviana, y sin cambiar tamaños ni posiciones.
- Textos: Roboto del sistema con sombra suave (`setShadowLayer`) para legibilidad. No se descargó Outfit.
- Escala: el diseño es de 1280×720. `d` se calcula con el tamaño físico de la pantalla (`getRealMetrics`,
  `min(ancho/1280, alto/720)`), no con la densidad que declare el firmware; medidas y textos se multiplican por `d`.
  En la MXQ Pro 5G vale 1.0. Con 1920×1080 simulado (`wm size`) las proporciones se mantienen.
- Las apps sin banner usan un degradé oscuro con el tinte del color de su ícono.
- `HIDDEN` (constante al inicio del Java) impide que entren solos al dock el launcher original, este launcher y
  utilidades que nadie abre a propósito: `com.android.documentsui` ("Descargas") y `com.rockchip.wfd` (WifiDisplay).
  No es lo mismo que "Ocultar" del menú, que es una decisión de la persona y se puede deshacer desde la caja.

## Navegación determinista
- `ScrollView` externo sobrescribe `computeScrollDeltaToGetChildRectOnScreen` (devuelve 0) y `arrowScroll`:
  arriba/abajo mueven exactamente una fila con `FocusFinder` y nunca desplazan por su cuenta. El
  desplazamiento vertical lo hace el listener de foco (`smoothScrollTo`).
- Los extremos usan `nextFocus*Id` apuntando a sí mismos (los botones superiores y los bordes de cada fila),
  así el foco no se escapa. `FocusFinder` respeta esos ids.
- Arriba desde el dock va al botón Wi-Fi; abajo desde los botones va al dock.
- **Atrás** en el inicio (`onBackPressed`) lleva al primer ícono del dock y, si ya está ahí, no hace nada. Sin esto Android
  cerraba la actividad y, al ser el Home, la volvía a abrir (parpadeo gris y launcher reiniciado). En el modo Mover y en
  los diálogos Atrás conserva su función de cancelar.
- Home (`onNewIntent`) siempre devuelve el foco al primer tile del dock. El último tile enfocado se guarda en
  `SharedPreferences` y se restaura al crear la actividad.

## Resaltado de vidrio: casos que costaron
- La ventana útil puede ser más baja que la pantalla (aquí 672 de 720 px): la primera "pantalla" se ajusta a
  la altura real con un `OnLayoutChangeListener` en el scroll.
- Al volver de otra app el vidrio quedaba en la posición vieja: `onResume` y `onWindowFocusChanged` llaman a
  `placeHighlight()`, que lo coloca sin animar sobre el tile enfocado.
- Dock con scroll horizontal: `OnScrollChangeListener` del `HorizontalScrollView` re-coloca el vidrio para que
  acompañe al tile mientras se desplaza.
- Los botones superiores escalan al enfocarse: el contenedor lleva `clipChildren=false` o se recortan.

## Dock editable y desplazable
- Se guarda como lista de paquetes en `SharedPreferences` (`dock`). Si no existe se usa `DOCK` (constante al
  inicio del Java: paquete, etiqueta opcional, acción alternativa si el paquete no tiene entrada de launcher;
  así "Ajustes" abre `ACTION_SETTINGS`).
- Sin límite de apps: `HorizontalScrollView` dentro de la banda. La banda tiene margen derecho para limitar su
  ancho. El scroll deja 34 dp de aire a cada lado del tile enfocado (`computeScrollDeltaToGetChildRectOnScreen`
  con el rectángulo ampliado) para que el vidrio quede dentro de la banda.
- Menú al **mantener OK** sobre una app: "Mover", "Ocultar", "Personalizar launcher…",
  "Información de la app" y "Desinstalar". Las apps del sistema no se desinstalan (solo avisa).
- **Modo Mover** (solo con orden Manual): "Mover" agarra la app (el nombre grande pasa a "↔ nombre"); con ◀ ▶ se la lleva
  al lugar elegido, OK confirma y se guarda, Atrás o Inicio cancelan (se vuelve al orden guardado). Arriba/abajo se
  ignoran mientras tanto. `moveStep` no saca la vista enfocada para reubicarla (el foco se perdería y Android lo mandaría
  al primer tile): intercambia a su vecina del lado opuesto, con el mismo resultado; el vidrio la sigue con su resorte.
  `onWindowFocusChanged` limpia `longHandled`: el "soltar OK" de un mantener-OK se lo queda el diálogo y dejaba el
  indicador pegado, tragándose el siguiente clic.
- "Ocultar" saca la app del dock y la guarda en `hidden`; no la desinstala. Para recuperarla: "Personalizar
  launcher…" > "Apps ocultas…" (aparece solo si hay alguna), que devuelve una (o "Mostrar todas") al principio del dock.
- `build()` descarta del dock las apps que ya no existen o están desactivadas; sin esto quedaban anotadas en la
  lista guardada, ocupando una posición invisible (el primer tile ofrecía "Mover a la izquierda" sin tener nada a la izquierda).

## Personalizar launcher y ajustes exportables
- Tres formas de entrar: (1) **clic** en el botón "Ajustes" de arriba, que ofrece "Configuración de Android" o
  "Configuración del launcher" (`showSettingsChoice`); (2) **mantener OK** sobre ese mismo botón, que abre el menú del
  launcher directo; (3) mantener OK sobre cualquier app > "Personalizar launcher…". Es un menú (`showLauncherMenu`)
  al que se le van sumando opciones. Cada una guarda un índice o booleano en las preferencias y rearma la pantalla.
- **Fondos propios (imágenes y videos)**: todo lo que haya en `/sdcard/Android/data/com.jgx.launcher/files/backgrounds/`
  (`.jpg .jpeg .png` y `.mp4 .m4v .webm .mkv .3gp`) aparece en *Personalizar launcher > Fondo* (▶ marca los videos), con
  "Ninguno" para quitarlo; el elegido se guarda en `bg_file` (por nombre). Encima va una capa negra de oscurecido (`bg_dim`:
  Sin oscurecer / Suave 35 % / Medio 55 % / Fuerte 75 %) para que los textos se lean, y el brillo de color sobre eso.
  Se copian con `install.ps1 <ip> -Fondo <archivos o carpeta>` (deja puesto el primero) o con `adb push`; también se puede
  elegir por ADB: `am start -n com.jgx.launcher/.Launcher --es background nombre.jpg` (`""` = ninguno).
  - Imágenes: se decodifican al ancho de pantalla (`inSampleSize`) y en RGB_565, porque son cajas de 1 GB (una foto de
    4000×3000 dejó el launcher en ~35 MB de PSS). Conviene que sean **apaisadas**: una vertical se recorta por el medio, y
    si el archivo no trae EXIF y está de costado hay que girarlo antes (pasó con las de Messi).
  - Videos: `TextureView` + `MediaPlayer`, sin sonido y en bucle, recortados para llenar la pantalla (`fitVideo`). Se pausan
    en `onPause` (el panel de Ajustes es translúcido) y se **sueltan en `onStop`**: con otra app al frente el cliente
    desaparece de `dumpsys media.player`, así que el decodificador queda libre para esa app. Con un video de 720p el launcher
    quedó en ~37 MB de PSS. Hay que usar H.264 (lo que decodifique la caja); un archivo roto o no soportado se ignora y el
    fondo queda sin imagen, sin fallos.
  - Los archivos **no** viajan en el archivo de ajustes (solo `bg_file` y `bg_dim`).
- **Orden del dock** (`dock_order`): Manual (el orden que armó la persona, con el modo Mover) /
  Alfabético / Más usadas / Últimas usadas. Solo cambia lo que se *muestra* (`sortDock`); la lista manual guardada no
  se toca, así que volver a Manual recupera el orden propio. "Mover" solo aparece en Manual. El uso se registra al abrir
  una app desde el launcher (`recordUse`: `use_count` y `use_last`), es propio de cada caja y **no se exporta**; con
  orden por uso, `onResume` reordena al volver (no `onRestart`: el panel de Ajustes de Android TV es translúcido y el
  launcher nunca llega a detenerse) y descarta `last` para que el foco caiga en el primer tile del nuevo orden.
  Los empates conservan el orden manual (la ordenación es estable).
- **Tarjetas** (se calculan en `onCreate` antes de `build()`; los valores por defecto reproducen el diseño original):
  - Tamaño (`card_size`): Pequeño 0.85× / Normal / Grande 1.15× sobre 240×135 dp; la banda del dock crece con la
    tarjeta (`bandH = tileH + 61`).
  - Esquinas (`card_corners`): Rectas 4 dp / Suaves 16 dp / Muy redondeadas 28 dp; el vidrio de foco usa el mismo radio + 6.
  - Zoom al enfocar (`card_zoom`): Sin zoom / Suave 1.05× / Normal 1.1× / Grande 1.2×. El vidrio ensancha su margen
    (`hlPad`) para seguir abarcando la tarjeta ampliada.
  - Brillo de fondo (`glow`): sí/no; apagado deja el fondo negro puro.
  - Un valor guardado fuera de rango (archivo editado a mano) cae al valor por defecto (`pref()`).
- **Exportar ajustes** escribe `launcher-settings.json` en la carpeta externa propia de la app
  (`/sdcard/Android/data/com.jgx.launcher/files/`; no pide permisos y `adb pull/push` llega hasta ahí). Solo viajan
  las preferencias de `SETTING_KEYS` (dock y ocultas); `known` y `last` dependen de cada caja y no se exportan.
- **Importar ajustes** lee ese archivo, borra `known` (las apps de esta caja que el archivo no menciona se reubican
  solas al final del dock) y recrea la pantalla. Una clave de `SETTING_KEYS` que el archivo no trae **vuelve a su valor
  por defecto** (así un archivo viejo, anterior a una opción nueva, no deja a medias los ajustes actuales de la caja). Los paquetes del archivo que la caja no tenga se descartan solos.
- Para otra caja: `install.ps1 <ip> -Ajustes archivo.json` copia el archivo y lo importa con
  `am start -n com.jgx.launcher/.Launcher --ez import_settings true` (el launcher consume el extra una sola vez).
- Probado en la MXQ Pro 5G: exportar desde el menú, cambiar el orden del dock en el JSON, importarlo por intent (el
  orden cambió) y volver a importar el original (se restauró), y `install.ps1 -Ajustes` de punta a punta.
- El long-press nativo de Android no es fiable con el mando (y con `input keyevent` simulado se interpretaba
  como clic corto). Se detecta en `dispatchKeyEvent`: `DPAD_CENTER/ENTER` con `repeatCount > 0` ejecuta la
  acción larga registrada en `longActions` y se descarta el `ACTION_UP` siguiente.

## Apps nuevas
- `build()` guarda en `known` los paquetes lanzables que ya vio. Todo paquete lanzable que no esté en `known`, ni en el
  dock, ni en `hidden`, ni en `HIDDEN`, entra solo **al principio** del dock (ordenados por nombre si llegan varios).
- La primera vez que corre esta versión sobre un dock ya armado (`known` no existe), las apps que faltaban se agregan
  al **final** para no desordenar lo que la persona ya tenía.
- Un `BroadcastReceiver` de `PACKAGE_ADDED/REMOVED` recrea la pantalla; al desinstalar, `known` se poda solo en el
  siguiente `build()`. Probado instalando un APK de prueba sin código: apareció al frente y, al desinstalarlo, se fue.

## Ajustes de Android TV, no de teléfono
`Settings.ACTION_SETTINGS` y similares se resuelven en estas cajas a `com.android.settings` (aspecto de
celular). `start()` reintenta el intent con `setPackage("com.android.tv.settings")` si ese paquete lo resuelve
(`MainSettings` responde a `android.settings.SETTINGS`). Funciona también para Wi-Fi y detalles de app.

## Barra superior
- RAM: `MemFree + Inactive(file)` de `/proc/meminfo` (o `MemAvailable` si el kernel lo ofrece), sin mostrar total. Hay cajas baratas que falsean `MemTotal`, `Cached` y `ActivityManager` (la MXQ Pro 5G dice 4 GB y tiene 1 GB), asi que esos valores no se usan. Se refresca cada 5 s solo mientras el launcher está
  en primer plano. Sin red.
- Red: SSID y `getLinkSpeed()` de `WifiManager` (permiso `ACCESS_WIFI_STATE`). Si `getActiveNetworkInfo()` es
  Ethernet (permiso `ACCESS_NETWORK_STATE`) muestra "Cable" y, si existe `/sys/class/net/eth0/speed`, la velocidad.
  La rama de cable **no se ha probado en una caja real** (la MXQ Pro 5G usa Wi-Fi).
- Temperatura: se intenta leer `/sys/class/thermal/thermal_zone0/temp`; en la MXQ Pro 5G no es legible y no se
  muestra nada. En otras cajas puede aparecer.
- **Liberar RAM**: `killBackgroundProcesses` sobre todos los paquetes instalados excepto el propio (permiso
  `KILL_BACKGROUND_PROCESSES`, normal). Mide antes/después y avisa con un Toast. Solo toca procesos en segundo
  plano; en la prueba liberó ~170 MB medidos con la misma cifra (parte vuelve sola porque los servicios del sistema se reinician).

## Cómo cambiar cosas
- Dock por defecto, nombres, acciones: constante `DOCK`.
- Tamaño base de las tarjetas y márgenes: constantes `TILE_W`, `TILE_H`, `MARGIN`, etc. (la banda sale de
  `bandH = tileH + 61`, calculado en `onCreate`). Los tamaños, esquinas y zooms disponibles: `CARD_*` al inicio del Java.
- Tamaños de letra: llamadas a `text(...)` y `clock/date.setTextSize` en `build()`.
- Fondo: `scroll.setBackgroundColor(Color.BLACK)`.
- Restablecer preferencias del launcher (dock, ocultas, apps conocidas): `adb shell pm clear com.jgx.launcher`
  (el Home preferido se conserva).

## Limitaciones conocidas
- No hay botón "Inputs": estas cajas no tienen entradas HDMI ni TV en vivo.
- Las teclas de acceso directo del mando (apps) no se mapean; se usa Button Mapper si está instalado.
- No probado con el mando físico durante el desarrollo: todo se verificó inyectando teclas por ADB y con
  capturas de pantalla.

