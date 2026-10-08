# Contribuir

Gracias por el interes. Esta guia resume como trabajar en el cliente movil.

## Requisitos

- JDK 17 (Temurin recomendado).
- Para compilar el APK: el SDK de Android (Android Studio Ladybug o mas nuevo, o
  las command-line tools con una plataforma reciente).
- El wrapper de Gradle ya esta en el repo; no instales Gradle a mano.

## Estructura

```
core/   modulo Kotlin/JVM puro, sin Android
  pairing/   parseo de gmini://pair
  model/     modelo del protocolo remoto v1
  protocol/  framing del WebSocket, eventos de chat y SSE
  chat/      reductor puro del turno de chat
  net/       clientes REST y WebSocket (OkHttp) y clasificacion de red
  store/     perfiles e interfaces de almacenamiento
app/    modulo de Android (Jetpack Compose, Material 3) que depende de :core
  data/      almacen seguro, ajustes, repositorio y conexion viva
  service/   servicio en primer plano, notificaciones y nodo
  voice/     grabacion WAV y reproduccion
  qr/        lector de QR con CameraX + ML Kit
  ui/        tema, navegacion, componentes y pantallas (chat, tareas, etc.)
```

## Probar

La logica delicada vive en `:core` y se prueba en la JVM, sin el SDK de Android:

```sh
GMINI_SKIP_APP=true ./gradlew :core:test
```

La variable `GMINI_SKIP_APP=true` excluye el modulo `:app` del build para no
necesitar el SDK. Con el SDK instalado puedes correr todo:

```sh
./gradlew :core:test assembleDebug
```

## Estilo

- Kotlin oficial; identificadores en ingles, textos de interfaz y documentacion
  en español.
- Sin emojis en el codigo, la interfaz ni la documentacion.
- Iconos vectoriales de Material, nunca emojis como iconos.
- Commits en español con estilo conventional-commits
  (`feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`).

## Antes de un PR

1. `GMINI_SKIP_APP=true ./gradlew :core:test` en verde.
2. Si tocaste `:app`, compila el APK de depuracion al menos una vez.
3. Actualiza `CHANGELOG.md` si el cambio es visible para el usuario.
