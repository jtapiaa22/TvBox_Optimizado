# Limpiar las apps desactivadas (borrarlas de /system)

Última parte del debloat: cuando una app ya está **desactivada** (`pm disable-user --user 0`) y el usuario confirmó que no la
quiere, este script la borra de verdad de `/system`, con respaldo en el PC. Así deja de salir en Ajustes → Aplicaciones (con 0 B)
y nada del sistema puede volver a activarla.

> Toca la partición del sistema. Pídele confirmación al usuario antes, y corre **siempre primero el modo prueba**.

## Uso
```powershell
.\limpiar-apps-desactivadas.ps1 -Ip 192.168.1.4                 # MODO PRUEBA: muestra el plan, no cambia nada
.\limpiar-apps-desactivadas.ps1 -Ip 192.168.1.4 -Ejecutar       # respalda, borra y reinicia (hay que escribir BORRAR)
```
Otras opciones: `-Respaldo <carpeta>` (por defecto `respaldo-system\` junto al script, ignorada por git),
`-Conservar <paquetes>` (por defecto `com.google.android.webview`: ya venía desactivado de fábrica y Chrome hace de WebView).

El modo prueba lee la caja de verdad (`pm list packages -d`, `dumpsys package`, `du`) y arma una tabla con paquete, carpeta, si tiene
actualización en `/data/app` y tamaño. Los paquetes básicos del sistema (`android`, SystemUI, Ajustes, `com.android.providers.*`,
instalador de paquetes, teclado, Chrome, nuestro launcher...) se saltan aunque estén desactivados.

## Qué hace con `-Ejecutar` (y se detiene antes de borrar ante cualquier problema)
1. Respalda cada carpeta `/system/app/<X>` o `/system/priv-app/<X>` en `<respaldo>\app` y `<respaldo>\priv-app`, y **comprueba que se
   copiaron todos los archivos**. Si falta alguno, no borra nada.
2. `adb root` y `adb remount`. Hace falta una compilación depurable (`userdebug`); si no hay root, no borra nada.
3. Las apps de sistema con actualización en `/data/app` (Play Store, Play Services...) se desinstalan con `pm uninstall` sin `--user`.
4. `rm -rf` de cada carpeta, solo si la ruta es `/system/app/<nombre>` o `/system/priv-app/<nombre>`.
5. `adb unroot` y reinicio. Después comprueba con `adb shell pm list packages -d` que las borradas ya no figuran.

## Volver atrás
El respaldo tiene el mismo formato que espera `devices/mxq-pro-5g/restore.ps1 -Respaldo <carpeta> -Ip <ip>`. Ese restore **no está
probado**: revísalo antes de fiarte, y guarda el respaldo en un lugar seguro, porque es la única copia de esas apps.

## Qué se probó y qué no
Probado con un `adb` simulado que devuelve salidas reales de una caja (sin tocar ninguna): el modo prueba no deja rastro; con
`-Ejecutar` el orden de acciones es respaldo → root → desinstalar actualizaciones → borrar → unroot → reinicio; con un respaldo
incompleto corta sin borrar nada; y escribir otra cosa que `BORRAR` cancela. Eso detectó un error real (una ruta con un solo
resultado se leía como texto suelto) que ya está corregido.
**No probado contra una caja real**: el comportamiento de `adb root`/`remount` en cada firmware, y que `du` y `find` existan en
todos los Android 7. Por eso el modo prueba va primero: si algo no se parsea bien en esa caja, se ve ahí.
