# G-Mini Agent for Android

Mobile client for [G-Mini Agent](https://github.com/angelgabrieljacintohuayllasco/G-Mini-Agent):
a remote control and chat client for **your own** G-Mini instance. It plays the
same role as the `gmini` CLI or the browser extension, but from your phone: you
talk to your agent, watch it work on your PC or server, approve sensitive
actions and receive its notifications.

It is not a tool to control other people's devices or administer someone else's
phone. It is the phone acting as a client of your own agent, like an SSH client
or the app of a self-hosted service.

[Version en español](README.md) · [User guide](docs/GUIA.md) · [Security](SECURITY.md) · [Extensions](docs/EXTENSIONES.md) · [Changelog](CHANGELOG.md)

## What it does

| Area | What it includes |
|---|---|
| Pair | Scan a `gmini://pair` QR or type host, port and 6-digit code. Multiple server profiles; switch the active one and unpair. The token lives encrypted in the Keystore. |
| Chat | Streaming chat over WebSocket (with SSE fallback): live text, action progress, agent state, slash commands and approvals. |
| Voice | Push-to-talk that records 16 kHz WAV and plays back the agent's reply (`/api/v1/voice/turn`), plus standalone TTS and STT. |
| Notifications | A foreground service keeps the connection alive and turns the agent's `notify` frames into Android notifications. The phone registers as a node with two safe surfaces: show text and speak text. |
| 24/7 work | Create, list and cancel background tasks (one-shot, interval or cron) with notify targets. |
| Resume | List conversations and continue any of them. |

## Requirements

- Android 8.0 (API 26) or newer.
- A reachable G-Mini Agent instance (on your PC, a server or a Raspberry Pi)
  with the v1 remote API enabled. The contract lives in the main repo:
  [`docs/protocol/remote-api-v1.md`](https://github.com/angelgabrieljacintohuayllasco/G-Mini-Agent/blob/main/docs/protocol/remote-api-v1.md).

## Build

Two modules:

- `:core` — pure Kotlin/JVM, no Android dependencies: pairing-URL parsing, the
  protocol model, WebSocket framing, SSE parsing, the chat-turn reducer and the
  OkHttp REST/WebSocket clients.
- `:app` — the Android app (Jetpack Compose, Material 3) that depends on `:core`.

The `:core` tests run with just the Gradle wrapper and a JDK 17, without the
Android SDK:

```sh
GMINI_SKIP_APP=true ./gradlew :core:test
```

Building the APK needs the Android SDK:

```sh
./gradlew :core:test assembleDebug
```

CI runs the `:core` tests and builds the debug APK on every push and PR. On a
`vX.Y.Z` tag the release workflow builds the APK and publishes it on a GitHub
Release. The published APK is signed with Android's debug/CI key so it installs;
it is **not** a production signature.

## Security

- **Local network only by default.** A remote server (a Tailscale IP, a domain
  or a public IP) must be added manually in Settings behind a clear warning.
- **Minimal permissions.** The manifest only grants INTERNET and the foreground
  service; microphone, notifications and camera are requested at runtime.
- **Token in the Keystore**, never in plain text or backups.
- **Agent content is data, not commands:** it is shown or spoken, never executed.

This build does **not** read SMS or call logs, does **not** read other apps'
notifications and does **not** use the accessibility service. Such opt-in modules
are only described as extension points in [docs/EXTENSIONES.md](docs/EXTENSIONES.md);
nothing of the sort is implemented here.

## License

[MIT](LICENSE).
