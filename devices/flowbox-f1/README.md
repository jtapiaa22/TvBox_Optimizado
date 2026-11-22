# Flow Box F1 (Skyworth HP40A, Android TV 12): optimización hecha el 2026-11-22

Deco de Flow (Personal/Telecom Argentina) fabricado por Skyworth. **Lo principal en este aparato es que no se actualice**: las
actualizaciones de firmware de Flow bloquean instalar apps por fuera de Play Store.

## Datos
| | |
|---|---|
| Modelo | `FlowBox-F1`, fabricante `SkyworthDigital`, placa `HP40A` (Amlogic) |
| Android | 12 (SDK 31), `user/release-keys`, parche de seguridad 2026-07-05, firmware `C7.5.1_20260825` |
| RAM / pantalla | 2 GB (`/proc/zoneinfo` = 2048 MB) / 1920x1080 a 320 dpi |
| ADB | `ro.adb.secure=1` (pide autorización); actualizaciones A/B con `update_engine` |

## La protección del firmware ("whitelist")
`pm disable-user`, `pm uninstall --user 0` y desactivar componentes (`pm disable-user <pkg>/<clase>`) se rechazan con
`Info: <pkg> is in whitelist, there is no permission to excute.` para: `com.sdt.ota` (actualizador), `com.skyworth.android.tr069` y
`com.sdt.android.tr369` (gestión remota), `ar.com.flow.androidtv_stb` (app Flow) y `com.sdt.producttest` (prueba de fábrica).
`pm hide` pide `MANAGE_USERS` (no lo tiene el shell).

**Lo que sí funciona** (y es reversible):
```
adb shell pm suspend <pkg>                                   # no puede abrir pantallas ni avisos
adb shell cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore  # Android le corta los servicios
```
Verificado tras reiniciar: el actualizador arranca por su `BootReceiver`, pero `CoreService` recibe
`Service.startForeground() not allowed due to bg restriction` y ~1 minuto después `Stopping service due to app idle`. En ese minuto
**llega a consultar el servidor** (se vio la conexión). `tr069` y la app Flow ya no corren; `tr369` y `producttest` arrancan igual (uid
`system` / sin efecto de la restricción).

## Bloqueo total: en el router
Todo sale de `tms-api.app.flow.com.ar` (dato del propio deco, línea `ConfigProperties` del logcat): OTA puerto **30906** (el actualizador
se conectó justo ahí), TR-069 8086, TR-369 18369, certificados 18443/17443. El 2026-11-22 resolvía a `3.213.32.71` y `34.239.55.77` (balanceador
de Amazon): **bloquear el dominio**, no las IP. Ya venían desactivados de antes (con IceBox): `com.skyworth.update`,
`com.skyworth.xmppservice`, `ar.com.flow.provisioncheck`, diagnóstico y registros de Skyworth.

**No hacer restablecimiento de fábrica**: vuelve todo y el deco se actualiza solo.

## Qué más se hizo
- `com.omni.sci` (medición de Telecom) desactivado; Netflix, Disney+, Paramount+ y Max quitados (`pm uninstall --user 0`).
- Limpieza moderada (`pm disable-user`): Play Juegos, Comentarios, recomendaciones del inicio de Google, TalkBack, sincronización de
  calendario, `partnersetup`, `onetimeinitializer`, cola de impresión, visor HTML, salvapantallas básico, respaldo de fondos y
  `com.android.dynsystem`. Se dejaron Play Store/Services, voz, Chromecast, YouTube, el salvapantallas de Google, teclado y control.
- Projectivy desinstalado, inicio de Android TV desactivado y **launcher propio (variante moderna)** como Home.
- Animaciones a 0.5.
- `Used RAM`: ~1300 MB → ~1155 MB (`dumpsys meminfo`, con el launcher abierto).

## Volver atrás
`pm unsuspend <pkg>`, `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND allow`, `pm enable <pkg>`,
`cmd package install-existing <pkg>` para lo quitado.
