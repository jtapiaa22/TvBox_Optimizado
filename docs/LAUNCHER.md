# Launcher estilo Apple TV para TV boxes con Android viejo (7.x)

Documentación técnica de `tv-launcher/`. Un solo archivo Java (`src/com/jgx/launcher/Launcher.java`), sin
AndroidX, sin Gradle, sin recursos XML: toda la UI se construye por código. Pensado para cajas con Android
7.1 (SDK 25), ~1-4 GB de RAM y control remoto con D-pad.

## Por qué existe
El launcher de referencia (otro repositorio) pide Android 8+ (minSdk 26). En Android 7.1 responde
`INSTALL_FAILED_OLDER_SDK`. Se escribió uno propio con `minSdk 25 / targetSdk 25`. Al apuntar a SDK 25 no hace
falta declarar `<queries>` (eso es de Android 11+); si algún día se sube el target a 30+, hay que declarar
`<queries>` para `LAUNCHER` y `LEANBACK_LAUNCHER` o la grilla sale vacía.

## Compilación (sin Gradle)
`build.ps1`: `aapt2 link` (solo manifiesto) → `javac --release 8` → `d8 --min-api 25` → agrega `classes.dex`
al APK con `python zipfile` → `zipalign` → `apksigner` con un keystore de depuración (`debug.keystore`, se crea
solo). Requisitos en el PC: Android SDK (build-tools 35 y platforms/android-35) y un JDK (se busca en
`~/.jdks`). Las rutas están al inicio del script. Los avisos de javac/keytool/apksigner salen por stderr y
PowerShell los muestra como error: por eso `$ErrorActionPreference = "Continue"` y se revisa `$LASTEXITCODE`.
`install.ps1 [ip]` compila, instala, fija Home y desactiva el launcher original.

## Pantalla
- Fondo negro. Arriba a la izquierda: saludo, hora grande (98.8 sp), fecha. Arriba a la derecha: botones
  "Liberar RAM", "Wi-Fi · red · Mbps" y "Ajustes", y debajo una línea "RAM x / y GB libre".
- Dock: banda redondeada abajo con tiles de 240×135 dp; sobre el dock, el nombre de la app enfocada en grande
  y un brillo ambiental del color promedio del ícono (muestreado a 24×24 px).
- Debajo (al bajar la pantalla se desplaza): fila "Recientes" (hasta 4) y "Todas las apps" en 4 columnas,
  ordenadas por nombre.
- Un único "vidrio" (View translúcido) se desliza con resorte (`OvershootInterpolator`) entre tiles. El tile
  enfocado crece 1.1× y, en la grilla, muestra su nombre debajo.
- Textos: Roboto del sistema con sombra suave (`setShadowLayer`) para legibilidad. No se descargó Outfit.
- Todo en dp. La caja renderiza a 1280×720 (densidad 160) y el TV escala.

## Navegación determinista
- `ScrollView` externo sobrescribe `computeScrollDeltaToGetChildRectOnScreen` (devuelve 0) y `arrowScroll`:
  arriba/abajo mueven exactamente una fila con `FocusFinder` y nunca desplazan por su cuenta. El
  desplazamiento vertical lo hace el listener de foco (`smoothScrollTo`).
- Los extremos usan `nextFocus*Id` apuntando a sí mismos (los botones superiores y los bordes de cada fila),
  así el foco no se escapa. `FocusFinder` respeta esos ids.
- Arriba desde el dock va al botón Wi-Fi; abajo desde los botones va al dock.
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
- Menú al **mantener OK**: en la grilla "Agregar al dock / Ocultar / Información / Desinstalar"; en el dock
  "Mover a la izquierda / derecha / Quitar del dock / Información / Desinstalar". Las apps del sistema no se
  desinstalan (solo avisa). Mantener OK sobre "Ajustes" (botón superior) restaura las apps ocultas.
- El long-press nativo de Android no es fiable con el mando (y con `input keyevent` simulado se interpretaba
  como clic corto). Se detecta en `dispatchKeyEvent`: `DPAD_CENTER/ENTER` con `repeatCount > 0` ejecuta la
  acción larga registrada en `longActions` y se descarta el `ACTION_UP` siguiente.

## Recientes y apps nuevas
- Al abrir una app se guarda en `recent` (máx. 8; se muestran 4 que no estén en el dock). Al volver
  (`onRestart`) el launcher se recrea si hubo cambios.
- Un `BroadcastReceiver` de `PACKAGE_ADDED/REMOVED` recrea la pantalla; si la app es nueva (no es una
  actualización) se coloca primero en Recientes.

## Ajustes de Android TV, no de teléfono
`Settings.ACTION_SETTINGS` y similares se resuelven en estas cajas a `com.android.settings` (aspecto de
celular). `start()` reintenta el intent con `setPackage("com.android.tv.settings")` si ese paquete lo resuelve
(`MainSettings` responde a `android.settings.SETTINGS`). Funciona también para Wi-Fi y detalles de app.

## Barra superior
- RAM: `ActivityManager.getMemoryInfo` (disponible/total). Se refresca cada 5 s solo mientras el launcher está
  en primer plano. Sin red.
- Wi-Fi: SSID y `getLinkSpeed()` de `WifiManager` (permiso `ACCESS_WIFI_STATE`).
- Temperatura: se intenta leer `/sys/class/thermal/thermal_zone0/temp`; en la MXQ Pro 5G no es legible y no se
  muestra nada. En otras cajas puede aparecer.
- **Liberar RAM**: `killBackgroundProcesses` sobre todos los paquetes instalados excepto el propio (permiso
  `KILL_BACKGROUND_PROCESSES`, normal). Mide antes/después y avisa con un Toast. Solo toca procesos en segundo
  plano; en la prueba liberó ~200 MB (parte vuelve sola porque los servicios del sistema se reinician).

## Cómo cambiar cosas
- Dock por defecto, nombres, acciones: constante `DOCK`.
- Columnas de la grilla: `COLS`. Tamaño de tiles/banda/márgenes: constantes `TILE_W`, `TILE_H`, `BAND_H`,
  `MARGIN`, etc. (si cambia el tamaño del tile, ajustar también `BAND_H = TILE_H + 2*BAND_PAD`).
- Tamaños de letra: llamadas a `text(...)` y `clock/date.setTextSize` en `build()`.
- Fondo: `scroll.setBackgroundColor(Color.BLACK)`.
- Restablecer preferencias del launcher (dock, ocultas, recientes): `adb shell pm clear com.jgx.launcher`
  (el Home preferido se conserva).

## Limitaciones conocidas
- No hay botón "Inputs": estas cajas no tienen entradas HDMI ni TV en vivo.
- Las teclas de acceso directo del mando (apps) no se mapean; se usa Button Mapper si está instalado.
- No probado con el mando físico durante el desarrollo: todo se verificó inyectando teclas por ADB y con
  capturas de pantalla.
