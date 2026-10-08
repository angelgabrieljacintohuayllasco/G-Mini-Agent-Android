# G-Mini Agent para Android

Cliente movil de [G-Mini Agent](https://github.com/angelgabrieljacintohuayllasco/G-Mini-Agent):
un control remoto y cliente de chat para tu **propia** instancia de G-Mini.
Hace el mismo papel que la CLI `gmini` o la extension del navegador, pero desde
el telefono: le hablas a tu agente, ves como trabaja en tu PC o servidor,
apruebas lo sensible y recibes sus avisos.

No es una app para controlar dispositivos de otras personas ni para administrar
teléfonos ajenos: es el telefono actuando como cliente de tu agente, igual que
un cliente SSH o la app de un servicio propio autoalojado.

[English version](README.en.md) · [Guia de uso](docs/GUIA.md) · [Seguridad](SECURITY.md) · [Extensiones](docs/EXTENSIONES.md) · [Cambios](CHANGELOG.md)

## Que hace

| Area | Que incluye |
|---|---|
| Emparejar | Lee un QR `gmini://pair` o escribe host, puerto y codigo de 6 digitos. Varios perfiles de servidor; cambiar el activo y desvincular. El token vive cifrado en el Keystore. |
| Conversar | Chat en streaming por WebSocket (con SSE de respaldo): texto en vivo, avance de acciones, estado del agente, comandos con barra y aprobaciones. |
| Hablar | Push-to-talk que graba WAV a 16 kHz y reproduce la respuesta del agente (`/api/v1/voice/turn`), ademas de TTS y STT sueltos. |
| Recibir avisos | Un servicio en primer plano mantiene la conexion y convierte los `notify` del agente en notificaciones. El telefono se registra como nodo con dos superficies seguras: mostrar un texto y leerlo en voz alta. |
| Trabajar 24/7 | Crear, listar y cancelar tareas en segundo plano (una vez, por intervalo o cron) con destinos de aviso. |
| Retomar | Lista de conversaciones y continuar cualquiera donde la dejaste. |

## Requisitos

- Android 8.0 (API 26) o superior.
- Una instancia de G-Mini Agent accesible desde el telefono (en tu PC, un
  servidor o una Raspberry Pi), con la API remota v1 activa. El contrato esta en
  el repo principal: [`docs/protocol/remote-api-v1.md`](https://github.com/angelgabrieljacintohuayllasco/G-Mini-Agent/blob/main/docs/protocol/remote-api-v1.md).

## Emparejar en un minuto

1. En tu G-Mini de escritorio o servidor: Ajustes > Dispositivos > genera un
   codigo. Aparece un QR con un payload `gmini://pair?host=...&port=...&code=...`.
2. En la app, pulsa **Escanear QR** (o entra los datos a mano) y **Emparejar**.
3. Listo: el telefono guarda un token de dispositivo y queda conectado.

Mas detalle en la [guia de uso](docs/GUIA.md).

## Compilar

El proyecto tiene dos modulos:

- `:core` — Kotlin/JVM puro, sin dependencias de Android. Contiene el parseo de
  la URL de emparejamiento, el modelo del protocolo, el framing del WebSocket, el
  analisis de SSE, el reductor del turno de chat y los clientes REST/WebSocket
  sobre OkHttp.
- `:app` — la app de Android (Jetpack Compose, Material 3) que depende de `:core`.

Las pruebas de `:core` corren solo con el wrapper de Gradle y un JDK 17, sin el
SDK de Android:

```sh
GMINI_SKIP_APP=true ./gradlew :core:test
```

Para compilar el APK hace falta el SDK de Android (Android Studio o
`android-actions/setup-android` en CI):

```sh
./gradlew :core:test assembleDebug
```

La integracion continua (`.github/workflows/ci.yml`) corre las pruebas de
`:core` y compila el APK de depuracion en cada push y PR. Al subir una etiqueta
`vX.Y.Z`, `release.yml` compila el APK y lo publica en una Release con las notas
del `CHANGELOG.md`. El APK publicado se firma con la clave de depuracion/CI de
Android para que se pueda instalar; **no es una firma de produccion**.

## Seguridad

- **Solo red local por omision.** La app se conecta a una direccion LAN (por
  ejemplo `http://192.168.x.x:8765`). Un servidor remoto (una IP de Tailscale, un
  dominio o una IP publica) se puede usar, pero exige agregarlo a mano en Ajustes
  y confirmar un aviso, igual que la extension del navegador trata los servidores
  remotos.
- **Permisos minimos.** El manifiesto solo concede INTERNET y el servicio en
  primer plano. El microfono, las notificaciones y la camara (para el QR) se
  piden en tiempo de ejecucion, la primera vez que usas cada funcion.
- **Token en el Keystore.** Se guarda en EncryptedSharedPreferences; nunca en
  texto plano ni en respaldos.
- **El contenido del agente es dato, no ordenes.** Lo que llega del servidor se
  muestra o se pronuncia; nunca se ejecuta en el telefono.

Esta version **no** lee SMS ni registros de llamadas, **no** lee notificaciones
de otras apps y **no** usa el servicio de accesibilidad. Ese tipo de modulos se
describe, como puntos de extension opcionales y con consentimiento explicito, en
[docs/EXTENSIONES.md](docs/EXTENSIONES.md), pero aqui no hay nada de eso
implementado ni se pide ningun permiso para ello.

Modelo de amenazas completo y como reportar fallos: [SECURITY.md](SECURITY.md).

## Proyecto principal y afines

| Repositorio | Que es |
|---|---|
| [G-Mini-Agent](https://github.com/angelgabrieljacintohuayllasco/G-Mini-Agent) | El agente de escritorio y servidor. |
| [G-Mini-Agent-Server](https://github.com/angelgabrieljacintohuayllasco/G-Mini-Agent-Server) | La CLI `gmini` y la imagen Docker del nucleo en modo servidor. |
| [G-Mini-Agent-Extension](https://github.com/angelgabrieljacintohuayllasco/G-Mini-Agent-Extension) | La extension de navegador. |
| [G-Mini-Agent-Home](https://github.com/angelgabrieljacintohuayllasco/G-Mini-Agent-Home) | El compañero fisico con ESP32, Arduino o Raspberry Pi. |

## Licencia

[MIT](LICENSE).
