# Seguridad

## Versiones con soporte

| Version | Soporte |
|---|---|
| 0.1.x | Si |

## Reportar una vulnerabilidad

No abras un issue publico. Usa el reporte privado de GitHub en la pestaña
**Security** del repositorio ("Report a vulnerability"). Incluye la version de la
app, el modelo y la version de Android y los pasos para reproducirlo. La primera
respuesta llega en un plazo de 7 dias.

## Modelo de amenazas

La app es un cliente de tu propia instancia de G-Mini. Recibe la direccion y un
token del servidor, le manda tus mensajes y voz, y muestra o pronuncia lo que el
agente responde. Por eso:

1. **Con quien habla.** Solo con el servidor que emparejaste. Por omision se
   aceptan unicamente direcciones de red local (`127.0.0.0/8`, `10/8`,
   `172.16/12`, `192.168/16`, enlaces locales y `.local`). Un servidor remoto
   (incluida una IP de Tailscale, que cae en el rango CGNAT) exige agregarlo a
   mano en Ajustes y confirmar un aviso.
2. **Como guarda el token.** En EncryptedSharedPreferences respaldado por el
   Keystore de Android (clave AES-256-GCM, claves de preferencia AES-256-SIV). El
   archivo queda excluido de los respaldos y de la transferencia entre
   dispositivos. Al desvincular se borra y, si se puede, se revoca en el servidor
   con `DELETE /api/v1/devices/{id}`.
3. **Que permisos pide.** El manifiesto solo declara como concedidos INTERNET,
   el estado de red y el servicio en primer plano (con su tipo `dataSync`). El
   microfono (RECORD_AUDIO), las notificaciones (POST_NOTIFICATIONS) y la camara
   (CAMERA, solo para leer el QR) se piden en tiempo de ejecucion la primera vez
   que se usan. No se declara ningun otro permiso sensible.
4. **El contenido del agente es dato, no ordenes.** Los frames `notify` y las
   invocaciones `node.invoke` solo muestran un texto como notificacion o lo leen
   en voz alta. Nunca se interpreta como instruccion para el telefono, ni se
   ejecuta codigo, ni se abre nada por su cuenta.
5. **Superficies de nodo acotadas.** El telefono se registra con exactamente dos
   superficies: `display.text` y `tts.speak`. Cada una se puede apagar desde
   Ajustes, y una invocacion a una superficie desactivada o desconocida responde
   con error.
6. **Trafico.** La red local usa HTTP sin cifrar; para salir de casa se
   recomienda una VPN o un proxy con TLS. El token viaja en la cabecera
   `Authorization: Bearer` y OkHttp fija la cabecera `Host` a partir de la URL,
   como pide el contrato.

## Fuera del alcance

- Lo que el nucleo de G-Mini hace con lo que recibe (por ejemplo, enviarlo a un
  proveedor de modelos). Eso se configura en G-Mini Agent.
- Un atacante en la misma red local que levante un servidor falso en la
  direccion y puerto esperados antes de que emparejes. Empareja en una red de
  confianza y revisa el nombre del agente que devuelve el servidor.
- Lectura de SMS, registros de llamadas o notificaciones de otras apps y la
  automatizacion por accesibilidad: no existen en esta app (ver
  [docs/EXTENSIONES.md](docs/EXTENSIONES.md)).
