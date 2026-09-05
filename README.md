# CercaChat 1.0.0

Aplicación Android de Antonio Avezon (2026) para conversar entre dos teléfonos cercanos **sin internet**, **sin datos móviles**, **sin router** y **sin cuentas**.

Application ID: `cl.antonioavezon.cercachat`  
Repositorio: [github.com/antonioavezon/cercachat](https://github.com/antonioavezon/cercachat)

## Descargar el APK de la versión 1.0.0

APK debug instalable de esta primera versión:

**[CercaChat-1.0.0-debug.apk](https://github.com/antonioavezon/cercachat/releases/download/v1.0.0/CercaChat-1.0.0-debug.apk)**

También está en la [release v1.0.0](https://github.com/antonioavezon/cercachat/releases/tag/v1.0.0).

| | |
| --- | --- |
| Archivo | `CercaChat-1.0.0-debug.apk` |
| Tamaño | 71 298 064 bytes (68,0 MiB) |
| SHA-256 | `0da645d304f58897e0e15f1796940cc3dbe1763312af00aa8f6bdbc7d33b23ec` |

En el teléfono, permite instalar apps de orígenes desconocidos. Desde un ordenador con `adb`:

```bash
adb install -r CercaChat-1.0.0-debug.apk
```

Es un APK **debug firmado** por el almacén de depuración de Android. Sirve para pruebas; no es una publicación en Play. El binario no vive en el historial Git: se publica como adjunto de la release.

## Qué hace

Cualquiera de los dos teléfonos puede **mostrar un QR** (anfitrión) o **escanearlo** (invitado). El anfitrión crea un *LocalOnlyHotspot* (no es tethering de datos). El invitado se une con `WifiNetworkSpecifier` y `ConnectivityManager`. El chat usa sockets TLS locales, autenticados con un token de un solo uso y la huella del certificado temporal incluida en el QR.

Ambos lados tienen las mismas funciones: texto, archivos, notas de voz y cierre.

## Permisos

Se piden **cuando hacen falta**, no todos al abrir la app:

| Permiso | Cuándo | Para qué |
| --- | --- | --- |
| Dispositivos cercanos (Android 13+) o ubicación precisa (Android 10–12) | Al mostrar o escanear QR | `LocalOnlyHotspot` / `WifiNetworkSpecifier` |
| Cámara | Al escanear | Lectura del QR con CameraX + ZXing (incluido en el APK) |
| Micrófono | Al grabar una nota de voz | AAC en M4A |
| Notificaciones | Al emparejar | Servicio en primer plano y avisos en segundo plano |
| Internet | Declarado | Sockets locales. **No se usa para servidores externos** |

En Android 10–12 hay que tener **la ubicación del sistema activada** para que el Wi-Fi local funcione. CercaChat lo avisa si está apagada.

## Cómo se obtiene la IP del anfitrión

No se asume `192.168.43.1` (tethering) ni ningún otro valor fijo.

1. Tras arrancar el *LocalOnlyHotspot*, se recorren las interfaces de red y se prefiere una con nombre de AP (`ap0`, `swlan0`, `softap`, `wlan1`…).
2. Si no aparece, se mira una red Wi-Fi **sin** capacidad `INTERNET` en `ConnectivityManager`.
3. Esa IPv4 real se escribe en el QR, junto con el puerto del servidor TLS.
4. El invitado, al obtener la `Network` local, **vincula el socket a esa red** (`network.socketFactory`) y conecta a la IP del QR. Si hiciera falta, puede usar la pasarela de `LinkProperties` como apoyo de descubrimiento, no como adivinanza ciega.

Si el fabricante no permite el hotspot solo local o no se descubre la IP, se muestra un **error claro** y **Reintentar**. Nunca se finge una conexión correcta.

## Uso

1. Arranque en frío: pantalla de autoría y versión (sale de `BuildConfig`). *Aceptar* una vez por proceso.
2. Alias local opcional (sin registro).
3. Un teléfono: *Mostrar mi QR*. El QR solo aparece cuando la red y el servidor TLS están listos.
4. El otro: *Escanear QR* y aceptar el diálogo del sistema para unirse a la red.
5. Chat: texto multilínea, pulsación larga para copiar (útil para IPs), archivos con *+*, notas de voz con el micrófono.
6. *Cerrar conversación* en cualquiera de los dos. El QR y el token quedan inválidos.
7. Si hay copias internas, cada teléfono decide por separado *Conservar* o *Borrar*. No se tocan originales del selector ni exportaciones.
8. *Archivos guardados*: abrir (FileProvider), exportar o eliminar.

## Limitaciones

- Un solo compañero por sesión.
- El QR caduca (unos 3 minutos) y no se reutiliza tras emparejar.
- Algunos fabricantes desactivan `LocalOnlyHotspot` o `WifiNetworkSpecifier`.
- Android puede matar el proceso pese al servicio en primer plano; la app muestra el estado real y no promete continuidad.
- El permiso `INTERNET` no implica salida a la red pública; el chat no comprueba “hay internet” para validar la sesión.
- Límite de archivo configurable (por defecto 100 MB). Si se supera, se rechaza; **no se trunca**.
- No hay cuentas, Firebase, publicidad, analítica ni modelos descargados.

## Compilación

Entorno de referencia: **Fedora Linux 43**, JDK **21** (`/usr/lib/jvm/java-21-openjdk`), Android SDK en `/home/antonio/Android/Sdk`.

Objetivo del comando: generar el APK debug.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew copyDistApk
```

El APK queda en `dist/CercaChat-1.0.0-debug.apk`.

Versiones elegidas (estables y alineadas con la documentación de AGP para `compileSdk` 36):

- minSdk 29 (Android 10), targetSdk 35, compileSdk 36
- Android Gradle Plugin 8.9.1, Gradle 8.11.1, Kotlin 2.0.21
- Jetpack Compose BOM 2024.12.01, Material 3, CameraX 1.4.1, ZXing 3.5.3
- BouncyCastle solo para emitir el certificado temporal del anfitrión (TLS 1.2/1.3, huella SHA-256 en el QR; no se acepta cualquier certificado)

## Guía de prueba en dos teléfonos físicos

Datos móviles **apagados**, sin router, Wi-Fi encendido. Repetir al final intercambiando quién muestra el QR.

1. A muestra QR y B escanea.
2. Ambos envían texto y una dirección IP (copiar con pulsación larga).
3. Ambos envían un archivo y una nota de voz (escuchar antes de enviar).
4. Comprobar sonido y, con la app en segundo plano, la notificación. Respetar silencio / No molestar.
5. Subir a mensajes antiguos, abrir el teclado y comprobar que no tapa *Enviar*; debe aparecer *Nuevos mensajes* si llega algo.
6. Bloquear pantalla, volver a la app; el estado debe ser el real.
7. Alejar o apagar Wi-Fi: error o desconexión visibles, no un chat “falso”.
8. Cerrar; el QR anterior no debe servir.
9. Conservar en un teléfono y borrar en el otro. Los originales del almacenamiento del usuario no deben desaparecer.
10. Repetir con los roles al revés.

Estas pruebas de radio **no quedan validadas** solo porque el emulador compile.

## Seguridad (resumen)

- TLS con certificado temporal del anfitrión.
- El cliente ancla la huella SHA-256 recibida en el QR.
- El token de un solo uso viaja **dentro** del canal TLS y se invalida al emparejar.
- Framing con tamaño máximo, confirmaciones de texto y de archivo, SHA-256 por archivo, timeouts y PING/PONG.
- Los textos no esperan a que termine un archivo grande: los marcos de control tienen prioridad sobre los bloques.
- No se escriben contraseñas, tokens ni contenido del chat en el registro.
