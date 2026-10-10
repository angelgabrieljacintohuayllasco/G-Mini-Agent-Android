# Extensiones (puntos de extension)

Este documento describe **como se enganchan** modulos opcionales que acceden a
partes sensibles del telefono, bajo consentimiento explicito. Los modulos que ya
estan implementados se documentan en [MODULOS.md](MODULOS.md); aqui queda el
contrato de diseño y las capacidades que todavia no estan.

Ya implementado (ver [MODULOS.md](MODULOS.md)):

- **Pasarela SMS**: reenvio de SMS entrantes al agente y envio de SMS tras
  confirmacion por mensaje, solo en red local.

Capacidades que todavia quedan fuera del nucleo limpio:

- Leer el registro de llamadas.
- Leer notificaciones de **otras** apps (NotificationListenerService).
- Manejar el dispositivo por el servicio de accesibilidad (AccessibilityService):
  en preparacion, con el mismo modelo de consentimiento.

## Principios para cualquier modulo opcional

Un modulo de estos solo seria aceptable si cumple, todos a la vez, estos
requisitos:

1. **Opt-in por capacidad.** Cada capacidad se activa de forma individual en
   Ajustes, apagada por defecto. Activarla muestra en claro que leera o hara el
   modulo y por que. No hay un interruptor unico que lo encienda todo.
2. **Consentimiento explicito y permiso del sistema.** Ademas del interruptor en
   la app, el usuario concede el permiso o el acceso especial de Android que
   corresponda (por ejemplo, el acceso a notificaciones o a accesibilidad desde
   Ajustes del sistema). El modulo nunca intenta trabajar sin ese permiso.
3. **Notificacion persistente en primer plano.** Mientras una capacidad
   sensible este activa, hay una notificacion continua y no descartable que lo
   indica, con un acceso directo para apagarla. El usuario siempre sabe que esta
   encendido.
4. **Solo red local.** Estos modulos solo operan cuando el servidor activo es de
   la red local. Si el perfil activo es remoto, quedan inhabilitados.
5. **El contenido del agente sigue siendo dato.** Igual que en el nucleo, lo que
   llega del agente no se interpreta como instruccion para el telefono mas alla
   de la superficie concreta que el usuario habilito.
6. **Minimo y auditable.** Cada modulo declara solo los permisos que usa, en su
   propio punto de registro, y su codigo queda aislado para poder revisarlo.

## Forma del contrato

El nucleo ya expone superficies de nodo (`display.text`, `tts.speak`). Un modulo
opcional seria, conceptualmente, un proveedor de superficies adicionales que se
registra solo si el usuario lo habilito. La forma (ilustrativa, **no** presente
en el codigo) seria algo asi:

```
// Ilustrativo. No existe en el repositorio.
interface NodeExtension {
    val id: String                       // p. ej. "notif.read", "sms.send"
    val surfaces: List<String>           // superficies que anade al node.register
    fun isEnabled(context): Boolean      // refleja el interruptor por capacidad
    fun requiredPermissions(): List<String>
    suspend fun invoke(surface: String, params: JsonObject): NodeResult
}
```

El registro de nodos (`node.register`) sumaria las superficies de los modulos
**habilitados** a las del nucleo, y `node.invoke` se enrutaria a la extension
correspondiente solo si sigue habilitada y con permiso. Un modulo deshabilitado
no aparece en el registro y sus superficies responden con error.

## Notas por capacidad

- **Lectura de notificaciones de otras apps:** requeriria un
  `NotificationListenerService` y el acceso especial que el usuario concede en
  Ajustes del sistema. Expondria, por ejemplo, una superficie de solo lectura que
  entrega al agente resumenes que el usuario pidio, nunca de forma silenciosa.
- **SMS/MMS y registro de llamadas:** requeririan los permisos de SMS/llamadas y
  una pantalla que explique el alcance. Convendria limitarlo a acciones puntuales
  confirmadas, no a un volcado continuo.
- **Automatizacion por accesibilidad:** es la mas invasiva. Exigiria el
  `AccessibilityService` activado a mano por el usuario, la notificacion
  persistente del punto 3 y, idealmente, confirmacion por accion. Queda fuera del
  nucleo por diseño.

Mientras tanto, el cliente se mantiene como un control remoto y cliente de chat
de tu propio agente, con la superficie de permisos mas pequeña posible.
