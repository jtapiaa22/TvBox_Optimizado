# Parchear una app que se cae en Android 7 por `java.time` (NoClassDefFoundError)

Síntoma: la app abre, pero al entrar a una pantalla se cierra. En `adb logcat`:

```
java.lang.NoClassDefFoundError: Failed resolution of: Ljava/time/LocalDate;
```

Causa: `java.time` (LocalDate, LocalDateTime, DateTimeFormatter...) existe desde Android 8 (API 26). Hay apps que
declaran `minSdk 24` pero usan esas clases sin activar *core library desugaring* al compilar. En Android 7.x se
caen. La solución correcta es que el autor active el desugaring; si no se puede, se puede parchear el APK.
Probado con una app Kotlin/Compose de 24 MB en una MXQ Pro 5G (Android 7.1.2). Funcionó, pero **no hay garantía**:
es un reempaquetado de una app ajena, la firma cambia y puede haber otros fallos.

## Antes de empezar
1. Confirma la causa en el registro (`NoClassDefFoundError ... java/time`). Si es otra cosa, esto no sirve.
2. Guarda el APK original (`adb shell pm path <paquete>` y `adb pull` con la ruta **completa y entre comillas**;
   si el comando devuelve varias líneas, no uses `$paths[0]` en PowerShell: con un solo APK eso toma la primera
   *letra* y `adb pull /` intenta copiar toda la caja).
3. Avisa que hay que **desinstalar la original** (la firma cambia) y se pierden sus datos.

## Qué hace falta
- Android SDK build-tools (d8, zipalign, apksigner) y un JDK. El `d8.jar` ya incluye **L8**.
- `desugar_jdk_libs-2.1.5.jar` y `desugar_jdk_libs_configuration-2.1.5.jar` (Google Maven:
  `dl.google.com/android/maven2/com/android/tools/…`, 6,1 MB y 56 KB). De la segunda jar sale
  `META-INF/desugar/d8/desugar.json`.
- [`google/enjarify`](https://github.com/google/enjarify) (Apache-2.0, archivado desde 2019, Python puro).

## Pasos
1. **dex → jar** (D8 no reescribe `java.time` si recibe `.dex`; hay que darle clases Java):
   `python -O -m enjarify.main app.apk -o app.jar --fast` (desde la carpeta de enjarify). Tardó unos minutos y
   tradujo 44 875 clases sin errores.
2. **jar → dex con desugaring**:
   `d8 -J-Xmx6g --release --min-api 24 --desugared-lib desugar.json --lib android.jar --output dexnew app.jar`
3. **La librería `j$` se compila aparte con L8.** No la metas como entrada de D8: D8 le reescribe sus propias
   llamadas y `j$.util.DesugarTimeZone.getTimeZone` se llama a sí misma hasta `StackOverflowError`.
   `java -cp d8.jar com.android.tools.r8.L8 --release --min-api 24 --lib android.jar --desugared-lib desugar.json --output desugar_lib.zip desugar_jdk_libs-2.1.5.jar`
   y agrega su `classes.dex` como un `classesN.dex` más.
4. **Reempaquetar**: copia el APK original sin `classes*.dex` ni `META-INF/MANIFEST.MF|*.SF|*.RSA`, agrega los dex
   nuevos, `zipalign -p -f 4` y `apksigner sign --min-sdk-version 24` con una clave de depuración propia.
   Si la firma es la misma, las siguientes versiones se instalan con `adb install -r` sin desinstalar.
5. **Instalar y probar**: desinstala la original, instala la parcheada, abre la app, entra a la pantalla que fallaba
   y mira `adb logcat`.

## Importante: parchea solo las clases afectadas (versión híbrida)
Convertir **toda** la app con enjarify rompió la reproducción de video: el lector de manifiestos DASH de Media3
fallaba con `IllegalArgumentException: Illegal Capacity: -2147483648` (una traducción incorrecta de enjarify,
que es un convertidor "mejor esfuerzo"). La app original reproducía bien, así que el error lo introdujo el parche.
Lo que funcionó:
1. Del jar de enjarify, selecciona solo las clases cuyo bytecode contiene `java/time/` (en la app de ejemplo, 56
   de 44 875; unas 120 KB) y guárdalas en un `subset.jar`.
2. `d8 ... --desugared-lib desugar.json --classpath <jar-completo> --output dexsub subset.jar`.
3. Arma el APK con este orden de dex: `classes.dex` = el subconjunto parcheado, `classes2..N.dex` = los dex
   **originales** sin tocar, y el dex de L8 como último. Android carga la primera definición de cada clase, así
   que las 56 parcheadas ganan y el resto del código es el original. (Las copias viejas quedan sombreadas.)
4. Prueba **las dos cosas**: la pantalla que se caía y la reproducción de un canal.
Los pasos 1 a 3 de arriba siguen valiendo para generar el jar y la librería `j$`, pero en vez de usar el dex de
toda la app, usa el híbrido.

## Comprobaciones que ayudan
- Contar en los dex el texto `Ljava/time/LocalDate;` (debe pasar a `Lj$/time/LocalDate;`).
- Intentos en orden: (1) D8 directo sobre el dex: no cambió nada. (2) enjarify + D8 con la librería `j$` como
  entrada: recursión infinita en `DesugarTimeZone`. (3) enjarify + D8 + L8 aparte: la pantalla de fechas
  funcionó, pero el video no. (4) Híbrido (solo las 56 clases parcheadas + dex originales + L8): todo funciona.

## Límites
- La app parcheada no recibe actualizaciones del autor (la firma es otra). Cada versión nueva hay que volver
  a parchearla, o pedirle al autor que active *core library desugaring*.
- Si la app trae una protección de integridad o carga código cifrado (como las versiones recientes de PlayTV),
  esto no sirve.
