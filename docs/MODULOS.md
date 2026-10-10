# Modulos opcionales

Ademas del cliente base (chat, voz, tareas, avisos), la app tiene modulos
**opcionales** que acceden a partes mas sensibles del telefono. Todos comparten
las mismas reglas:

- **Apagados por defecto.** Se encienden uno por uno en Ajustes, con una
  pantalla que explica que hacen.
- **Permiso del sistema aparte.** Encender el interruptor no basta: Android pide
  el permiso correspondiente, y algunos (como la accesibilidad) hay que
  activarlos a mano en Ajustes del sistema.
- **Solo red local.** Funcionan solo cuando el servidor activo es de tu red
  local. Con un servidor remoto quedan inhabilitados.
- **Visibles.** Mientras un modulo esta activo hay una notificacion permanente.
- **El contenido del agente es dato.** Nunca se interpreta como una orden mas
  alla de la superficie concreta que habilitaste.

Son para tu propio telefono y tu propio agente. Puedes apagarlos cuando quieras.

## Pasarela SMS

Permite que tu agente use los SMS de tu telefono. Pensada para cosas como
recibir un codigo de verificacion o mandar un aviso.

Que hace con la pasarela encendida:

- **Entrantes.** El texto de los SMS que te llegan se reenvia a tu agente como
  un evento de nodo `sms.received` (`{from, text, received_at}`), para que lo use
  en la conversacion. Requiere los permisos `RECEIVE_SMS` y `READ_SMS`.
- **Salientes.** El agente puede pedir el envio de un SMS con la superficie de
  nodo `sms.send` (`{to, text}`). **Cada envio te pide confirmacion**: aparece una
  notificacion con el destinatario y el texto, y el mensaje no sale hasta que
  pulsas *Permitir*. Si no respondes en dos minutos, se cancela y el agente
  recibe un error. Requiere el permiso `SEND_SMS`.

Salvaguardas:

- Apagada por defecto; se enciende en **Ajustes > Pasarela SMS** tras aceptar la
  pantalla de consentimiento.
- Solo opera con un servidor de **red local**; si el perfil activo es remoto, la
  pasarela queda inhabilitada y `sms.send` responde con error.
- Notificacion permanente mientras esta activa.
- El numero y el texto de cada envio se validan antes de pedir la confirmacion.

El receptor de SMS entrantes se registra en tiempo de ejecucion solo mientras la
pasarela esta encendida; no hay ningun receptor declarado en el manifiesto, asi
que la app no escucha SMS con la pasarela apagada.

### Lado del servidor

Esta parte vive en el telefono. Para que el agente **use** la pasarela, el nucleo
de G-Mini Agent (escritorio o servidor) tiene que saber invocar la superficie
`sms.send` sobre el nodo y, si quieres, consumir los eventos `sms.received`. Eso
se configura en el repo principal de G-Mini Agent.

## Control del telefono (android-use)

En preparacion. Permitiria que el agente maneje el telefono (como el *computer
use* del escritorio) a traves del servicio de accesibilidad de Android, que el
usuario activa a mano, con notificacion permanente, solo en red local y con
confirmacion por accion. Hasta que este, su contrato queda descrito en
[EXTENSIONES.md](EXTENSIONES.md).
