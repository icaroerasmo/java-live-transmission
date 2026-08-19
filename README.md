# java-live-transmission

Streams a four-camera RTSP grid with mixed audio to Telegram Live via RTMPS.

The container image is published as:

```text
ghcr.io/icaroerasmo/java-live-transmission:latest
```

Configuration is supplied through environment variables and an optional
`/app/config/config.yaml` bind mount.