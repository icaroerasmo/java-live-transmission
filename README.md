# java-live-transmission

Streams a four-camera RTSP grid with mixed audio to Telegram Live via RTMPS.
Camera audio is isolated behind paced PCM feeders, allowing an unavailable
camera to switch to SMPTE bars and silence without stalling the shared stream.

The container image is published as:

```text
ghcr.io/icaroerasmo/java-live-transmission:latest
```

Configuration is supplied through environment variables and an optional
`/app/config/config.yaml` bind mount.