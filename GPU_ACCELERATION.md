# GPU Acceleration

This document explains how to select the video encoder for the compositor
output, covering both **NVIDIA (NVENC)** and **AMD Radeon (CPU fallback)**. It
describes what must be installed on the host, how the GPU must be exposed to the
container, and the relevant environment variables.

## Image

```text
ghcr.io/icaroerasmo/java-live-transmission:latest
```

The image is based on `archlinux` and bundles `ffmpeg` with the `h264_nvenc`,
`h264_vaapi` and `libx264` encoders compiled in. Only the encoder is configurable;
the compositor itself (the camera grid) runs on the CPU.

## Encoder selection

The encoder is driven by environment variables mapped into the `live.output`
section of the configuration:

| Environment variable | Config key | Default | Meaning |
|---|---|---|---|
| `LIVE_VIDEO_CODEC` | `live.output.video-codec` | `h264_nvenc` | ffmpeg video encoder |
| `LIVE_VIDEO_PRESET` | `live.output.video-preset` | `p4` | encoder preset |
| `LIVE_OUTPUT_TUNE` | `live.output.output-tune` | `ll` | encoder tune |
| `LIVE_VIDEO_BITRATE` | `live.output.video-bitrate` | `1200k` | target bitrate |
| `LIVE_MAXRATE` | `live.output.maxrate` | `1500k` | max bitrate |
| `LIVE_BUFSIZE` | `live.output.bufsize` | `3000k` | VBV buffer size |

## NVIDIA (NVENC)

### Host requirements

1. Install the NVIDIA driver.
2. Install the container toolkit (`nvidia-container-toolkit`) so the GPU is
   exposed through the CDI `nvidia.com/gpu` device.

### Exposing the GPU to the container

```yaml
services:
  live:
    image: ghcr.io/icaroerasmo/java-live-transmission:latest
    devices:
      - nvidia.com/gpu=all
    environment:
      LIVE_VIDEO_CODEC: h264_nvenc
      LIVE_VIDEO_PRESET: p4
      LIVE_OUTPUT_TUNE: ll
```

`p4` and `ll` are NVENC-specific values.

## AMD Radeon (CPU fallback)

AMD hardware encoding (`h264_vaapi`) is **not** currently supported by the
compositor: the ffmpeg command uses NVENC/x264-style flags (`-preset`, `-tune`)
that the VA-API encoder does not accept, and the image does not ship the VA-API
driver (`libva-mesa-driver`). On AMD/Intel machines, use the software encoder:

```yaml
services:
  live:
    image: ghcr.io/icaroerasmo/java-live-transmission:latest
    environment:
      LIVE_VIDEO_CODEC: libx264
      LIVE_VIDEO_PRESET: veryfast
      LIVE_OUTPUT_TUNE: zerolatency
```

No GPU device is required. `libx264` is a software encoder, so it consumes CPU
instead of the GPU.

Enabling hardware encoding on AMD would require changing the compositor to emit
VA-API-specific flags, installing the VA-API driver in the image, and exposing
`/dev/dri/renderD128`.
