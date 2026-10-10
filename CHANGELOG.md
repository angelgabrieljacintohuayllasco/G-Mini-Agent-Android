# Cambios

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el
proyecto usa [versionado semantico](https://semver.org/lang/es/).

## [0.2.0] - 2026-10-10

### Anadido

- **Pasarela SMS** (modulo opcional, apagado por defecto). Con el encendido, el
  telefono reenvia los SMS entrantes al agente (evento de nodo `sms.received`) y
  expone la superficie `sms.send`. Cada envio pide confirmacion con el
  destinatario y el texto a la vista, y no sale hasta que el usuario pulsa
  Permitir; si no responde en dos minutos, se cancela. Solo opera con un servidor
  de red local y muestra una notificacion permanente mientras esta activa. Los
  permisos de SMS se piden en tiempo de ejecucion tras una pantalla de
  consentimiento. Detalle en [docs/MODULOS.md](docs/MODULOS.md).
- **Control del telefono (android-use)** (modulo opcional, apagado por defecto).
  Permite que el agente toque, deslice, escriba y capture la pantalla
  (`device.tap`, `device.swipe`, `device.text`, `device.key`,
  `device.screenshot`), como el computer use del escritorio. Ademas del
  interruptor, el usuario activa a mano el servicio de accesibilidad en Ajustes
  de Android; funciona solo en red local y muestra una notificacion permanente
  con un boton para detenerlo. Detalle en [docs/MODULOS.md](docs/MODULOS.md).

[0.2.0]: https://github.com/angelgabrieljacintohuayllasco/G-Mini-Agent-Android/releases/tag/v0.2.0

## [0.1.0] - 2026-10-08

Primera version del cliente movil de G-Mini Agent: un control remoto y cliente
de chat para tu propia instancia de G-Mini, igual que la CLI `gmini` o la
extension del navegador, pero desde el telefono.

### Anadido

- Emparejamiento con tu servidor leyendo un QR (`gmini://pair`) o escribiendo
  host, puerto y codigo de 6 digitos a mano, contra `POST /api/v1/pairing/claim`.
- Perfiles de servidor multiples: agregar, cambiar el activo y desvincular
  (`DELETE /api/v1/devices/{id}`). El token se guarda en EncryptedSharedPreferences
  respaldado por el Keystore de Android.
- Chat en streaming sobre el WebSocket `/api/v1/ws`, con SSE (`POST /api/v1/chat`)
  como alternativa. Muestra el texto a medida que llega, el avance de las
  acciones, el estado del agente y resuelve aprobaciones con
  `POST /api/v1/approvals`.
- Comandos con barra (`/nuevo`, `/ayuda`, `/plan`, `/modo`, `/detener`) y
  conversacion nueva desde la propia pantalla.
- Voz push-to-talk: graba 16 kHz mono PCM16 en WAV, lo envia a
  `POST /api/v1/voice/turn` y reproduce la respuesta. El microfono se pide solo
  al usarlo por primera vez.
- Servicio en primer plano que mantiene viva la conexion, convierte los frames
  `notify` en notificaciones y registra el telefono como nodo
  (`node.register`) exponiendo solo dos superficies seguras: `display.text` y
  `tts.speak`.
- Tareas en segundo plano: listar, crear (una vez, por intervalo o cron) y
  cancelar (`/api/v1/tasks`).
- Sesiones: listar conversaciones (`GET /api/v1/sessions`) y retomar una
  (`GET /api/v1/sessions/{id}/messages`).
- Ajustes: perfiles, conexion en segundo plano, interruptor por cada superficie
  de nodo, tema (sistema, claro, oscuro) y los valores de seguridad.
- Modulo `:core` de Kotlin puro (sin dependencias de Android) con el parseo de
  la URL de emparejamiento, el modelo del protocolo, el framing del WebSocket, el
  analisis de SSE, el reductor del turno de chat y los clientes REST/WebSocket,
  cubierto por pruebas unitarias en la JVM.

### Seguridad

- Por omision el cliente solo se conecta a direcciones de red local. Un servidor
  remoto (una IP de Tailscale, un dominio o una IP publica) exige agregarlo a
  mano y confirmar un aviso.
- Permisos por omision minimos: el manifiesto solo concede INTERNET y el
  servicio en primer plano; el microfono, las notificaciones y la camara se
  piden en tiempo de ejecucion.
- Todo lo que llega del agente se trata como dato: se muestra o se pronuncia,
  nunca se ejecuta. No se lee SMS, registros de llamadas ni notificaciones de
  otras apps, y no se usa el servicio de accesibilidad.

[0.1.0]: https://github.com/angelgabrieljacintohuayllasco/G-Mini-Agent-Android/releases/tag/v0.1.0
