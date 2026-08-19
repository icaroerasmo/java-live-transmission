# java-live-transmission

Transmits RTSP cameras to Telegram live streams.

## Overview

`java-live-transmission` is a Spring Boot application that:

1. Reads one or more RTSP camera URLs from configuration.
2. Creates RTMP-based live streams (video chats) in Telegram groups/channels via the Bot API.
3. Uses **ffmpeg** to pull each RTSP feed and push it to the corresponding Telegram RTMP ingest URL.
4. Automatically restarts the stream if ffmpeg exits unexpectedly.

```
RTSP camera → ffmpeg → Telegram RTMP ingest → Telegram Live Stream
```

## Prerequisites

| Dependency | Version |
|------------|---------|
| Java       | 17+     |
| ffmpeg     | any recent (installed in PATH or at a custom path) |
| Telegram Bot | must be an **administrator** of the target group/channel with permission to manage video chats |

> **Note:** Live streaming (RTMP) in Telegram requires the target chat to be a **supergroup** or **channel**.

## Configuration

All settings are driven by environment variables (or `application.yml`).

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `TELEGRAM_BOT_TOKEN` | ✅ | – | Bot API token from [@BotFather](https://t.me/BotFather) |
| `TELEGRAM_BOT_USERNAME` | ✅ | – | Bot username (without `@`) |
| `CAMERA_1_NAME` | | `Camera 1` | Display name of the camera / live stream title |
| `CAMERA_1_RTSP_URL` | ✅ | – | Full RTSP URL, e.g. `******192.168.1.10:554/stream` |
| `CAMERA_1_CHAT_ID` | ✅ | – | Telegram chat ID of the target group/channel (negative number for supergroups) |
| `FFMPEG_PATH` | | `ffmpeg` | Path to the ffmpeg binary |
| `FFMPEG_LOG_LEVEL` | | `warning` | ffmpeg log level (e.g. `info`, `warning`, `error`) |
| `FFMPEG_RESTART_DELAY_SECONDS` | | `5` | How often (seconds) to check/restart streams |

For **multiple cameras**, mount a custom `application.yml`:

```yaml
transmission:
  bot-token: YOUR_TOKEN
  bot-username: YOUR_BOT_USERNAME
  cameras:
    - name: "Front Door"
      rtsp-url: "******192.168.1.10:554/stream"
      chat-id: "-1001234567890"
    - name: "Back Yard"
      rtsp-url: "******192.168.1.11:554/stream"
      chat-id: "-1009876543210"
```

## Running with Docker Compose

```bash
cp .env.example .env   # fill in your values
docker compose up -d
```

`.env.example`:
```
TELEGRAM_BOT_TOKEN=123456:ABC-DEF1234...
TELEGRAM_BOT_USERNAME=MyStreamBot
CAMERA_1_NAME=Front Door
CAMERA_1_RTSP_URL=******192.168.1.10:554/stream
CAMERA_1_CHAT_ID=-1001234567890
```

## Running with Maven

```bash
export TELEGRAM_BOT_TOKEN=123456:ABC-DEF1234...
export TELEGRAM_BOT_USERNAME=MyStreamBot
export CAMERA_1_RTSP_URL=******192.168.1.10:554/stream
export CAMERA_1_CHAT_ID=-1001234567890

mvn spring-boot:run
```

## Building

```bash
mvn package
java -jar target/java-live-transmission-0.0.1-SNAPSHOT.jar
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Spring Boot Application                                     │
│                                                             │
│  StreamScheduler                                            │
│    └─ (every N seconds) for each Camera:                    │
│         ├─ if ffmpeg not running:                           │
│         │   ├─ TelegramStreamService.getRtmpUrl()           │
│         │   │   └─ createVideoChat (if no live stream)      │
│         │   │   └─ getVideoChatRtmpUrl                      │
│         │   └─ FfmpegStreamService.startStream()            │
│         │       └─ ffmpeg -i <rtsp-url> -f flv <rtmp-url>   │
│         └─ if running: no-op                                │
└─────────────────────────────────────────────────────────────┘
```

## License

MIT
