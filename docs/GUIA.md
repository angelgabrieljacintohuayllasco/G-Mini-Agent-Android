# Guia de uso

Esta guia cubre el dia a dia del cliente movil de G-Mini Agent. Todo ocurre
contra tu propia instancia de G-Mini (tu PC, tu servidor o tu Raspberry Pi).

## 1. Emparejar

1. En tu G-Mini de escritorio o servidor, abre **Ajustes > Dispositivos** y
   genera un codigo de emparejamiento. Veras un codigo de 6 digitos y un QR cuyo
   contenido es `gmini://pair?host=<ip>&port=<puerto>&code=<codigo>`.
2. En la app:
   - **Escanear QR:** pulsa *Escanear QR* y apunta al codigo. La primera vez se
     pide permiso de camara, que solo se usa en esta pantalla.
   - **Manual:** escribe host, puerto (por defecto 8765) y el codigo de 6
     digitos.
3. Opcional: ponle un nombre al servidor. Pulsa **Emparejar**.

El codigo vence a los 5 minutos y es de un solo uso. Si falla, genera uno nuevo.

### Servidor remoto

Por omision solo puedes conectarte a servidores de tu red local. Si el host no
es local (una IP de Tailscale, un dominio o una IP publica), la app muestra un
aviso y te pide confirmar que entiendes el riesgo antes de continuar. Para salir
de casa de forma segura usa una VPN o un proxy con TLS (activa entonces el
interruptor **Usar HTTPS/WSS**).

## 2. Chatear

- Escribe una orden y pulsa enviar. La respuesta llega en streaming: el texto
  aparece a medida que el agente lo produce.
- Debajo de cada respuesta veras el **avance de las acciones** (por ejemplo
  "Tarea creada") con su resultado, y arriba el **estado** del agente
  (pensando, ejecutando, hablando).
- Si una accion es sensible, aparece una tarjeta de **aprobacion**: pulsa
  *Aprobar* o *Rechazar*. La app responde con `POST /api/v1/approvals`.
- Transporte: si el servicio en segundo plano esta activo, el chat viaja por el
  WebSocket; si no, usa el streaming SSE. Lo veras indicado junto al estado.

### Comandos con barra

La fila de atajos ofrece `/nuevo`, `/ayuda`, `/plan`, `/modo` y `/detener`.
`/nuevo` empieza una conversacion desde cero; los demas se insertan en el cuadro
de texto para que completes el resto. Cualquier texto que empiece con `/` se
envia tal cual: lo interpreta tu agente.

### Voz (push-to-talk)

Manten pulsado el microfono para grabar y suelta para enviar. La app graba WAV a
16 kHz mono, lo manda a `POST /api/v1/voice/turn` y reproduce la respuesta. El
permiso de microfono se pide la primera vez.

## 3. Avisos del agente y nodo

Activa **Mantener conexion en segundo plano** en Ajustes para que un servicio en
primer plano conserve la conexion. Con el:

- Los frames `notify` del agente se convierten en notificaciones de Android (la
  primera vez se pide el permiso de notificaciones en Android 13 o superior).
- El telefono se registra como nodo y expone dos superficies seguras, que
  puedes apagar por separado:
  - **display.text:** el agente muestra un texto como notificacion.
  - **tts.speak:** el agente lee un texto en voz alta con la voz del telefono.

Nada de lo que envia el agente se ejecuta: solo se muestra o se pronuncia.

## 4. Tareas en segundo plano

En **Tareas** puedes crear trabajo para que tu agente lo haga sin ti:

- **Una vez:** corre de inmediato.
- **Intervalo:** cada N segundos (minimo 60).
- **Cron:** una expresion `min hora dia mes dia-semana` con su zona horaria.

Puedes indicar destinos de aviso (por ejemplo `telegram`) y cancelar cualquier
tarea en cola, programada o en curso.

## 5. Sesiones

En **Sesiones** ves tus conversaciones con su titulo, numero de mensajes y
cuando se actualizaron. Toca una para retomarla en el chat.

## 6. Ajustes

- **Servidores:** lista de perfiles; toca uno para activarlo, el icono de
  papelera lo desvincula (borra el token y, si puede, lo revoca en el servidor).
  *Agregar servidor* abre de nuevo el emparejamiento.
- **Conexion:** interruptor del servicio en segundo plano.
- **Superficies del nodo:** apaga o enciende `display.text` y `tts.speak`.
- **Tema:** sistema, claro u oscuro.

## Resolucion de problemas

| Sintoma | Que hacer |
|---|---|
| "El servidor rechazo el token" | El token vencio o se revoco. Desvincula y vuelve a emparejar con un codigo nuevo. |
| "El agente esta ocupado" | Esta atendiendo otra conversacion. Reintenta en un momento o crea una tarea. |
| No conecta en LAN | Verifica la IP y el puerto, que el telefono este en la misma red y que el servidor escuche fuera de `127.0.0.1` (modo servidor o `--host 0.0.0.0`). |
| No llegan avisos | Activa la conexion en segundo plano y concede el permiso de notificaciones. |
| El QR no se lee | Concede el permiso de camara o usa la entrada manual. |
