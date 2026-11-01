# Downloader 1.5.1-ForGoogleAndroidDevices

Versión de **Downloader by AFTVnews** elegida para las cajas con Android viejo: es liviana y rápida. Se usa para
instalar apps cuando se apagan Play Store y Play Services (ver `devices/mxq-pro-5g/README.md`).

- Paquete: `com.esaba.downloader` · versión `1.5.1-ForGoogleAndroidDevices` · código `62` · pide Android 5.1 o más.
- Autor y sitio oficial: AFTVnews (aftvnews.com). **Esta carpeta solo guarda una copia de la versión elegida**; la app es de
  su autor y este repositorio no tiene relación con él. No sé qué dice su licencia sobre redistribuirla: si el autor lo pide,
  hay que sacarla de aquí.

## Por qué hay varios archivos y no uno solo
El APK principal declara `isSplitRequired=true` y `requiredSplitTypes="base__density"`: Android **exige** instalarlo junto
con una pieza de densidad de pantalla. Por eso no se puede instalar solo, y un único archivo obligaría a modificar el APK
(y a firmarlo de nuevo). Aquí están las piezas originales, sin tocar, que vienen del paquete `.xapk` de la versión:

| Archivo | Qué es |
|---|---|
| `com.esaba.downloader.apk` | App principal (obligatoria) |
| `config.ldpi/mdpi/tvdpi/hdpi/xhdpi/xxhdpi/xxxhdpi.apk` | Una de estas, según los dpi de la caja (120/160/213/240/320/480/640) |
| `config.es.apk`, `config.en.apk` | Idiomas |

## Instalar
```
.\instalar-downloader.ps1 -Ip 192.168.1.4
```
El script lee los dpi de la caja y usa solo las piezas que corresponden. A mano (caja a 160 dpi):
```
adb install-multiple -r com.esaba.downloader.apk config.mdpi.apk config.es.apk config.en.apk
```
Si la caja ya trae un Downloader **más nuevo** que da errores: `.\instalar-downloader.ps1 -Ip ... -DesinstalarSiHaceFalta`
(Android no deja bajar de versión con `install -r`).

## Verificación del origen
Todas las piezas están firmadas con el mismo certificado, que **coincide con el del Downloader 2.0.4-ForGoogleAndroidDevices
publicado en APKMirror**, y la firma verifica (el archivo no se modificó después de firmarse):

- SHA-1 del certificado: `63d2b74eb719e4b5e38a57169549e6963b61b4b1`
- SHA-256 del certificado: `bf2d302923d70290de057a1df648af8d25b52f40f017290f1f1d4555f1c2d43a`

Para comprobarlo: `apksigner verify --print-certs com.esaba.downloader.apk`.
Esto prueba que no fue alterado y que lo firmó la misma clave; no prueba que AFTVnews autorice esta copia.
