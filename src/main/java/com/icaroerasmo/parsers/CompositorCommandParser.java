package com.icaroerasmo.parsers;

import com.icaroerasmo.properties.CameraProperties;
import com.icaroerasmo.properties.LiveTransmissionProperties;
import com.icaroerasmo.storage.DetectionStateStorage;
import com.icaroerasmo.util.GridLayout;

import java.util.ArrayList;
import java.util.List;

public class CompositorCommandParser {

    public static List<String> build(LiveTransmissionProperties properties) {
        boolean overlayEnabled = properties.detectionOverlay() == null || properties.detectionOverlay().enabled();

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("info");
        cmd.add("-nostdin");
        cmd.add("-nostats");

        List<CameraProperties> cameras = properties.cameras();
        int n = cameras.size();

        // Video inputs (frame workers) - image2pipe MJPEG
        for (CameraProperties camera : cameras) {
            cmd.add("-thread_queue_size");
            cmd.add("3");
            cmd.add("-framerate");
            cmd.add(properties.output().fps());
            cmd.add("-f");
            cmd.add("image2pipe");
            cmd.add("-vcodec");
            cmd.add("mjpeg");
            cmd.add("-i");
            cmd.add(camera.pipePath());
        }

        // Border mask inputs (raw gray), only when overlay enabled
        if (overlayEnabled) {
            for (CameraProperties camera : cameras) {
                cmd.add("-thread_queue_size");
                cmd.add("1");
                cmd.add("-framerate");
                cmd.add(properties.output().fps());
                cmd.add("-f");
                cmd.add("rawvideo");
                cmd.add("-pix_fmt");
                cmd.add("gray");
                cmd.add("-video_size");
                cmd.add(properties.panel().width() + "x" + properties.panel().height());
                cmd.add("-i");
                cmd.add(camera.borderPipePath());
            }
        }

        // Raw PCM audio inputs
        for (CameraProperties camera : cameras) {
            cmd.add("-thread_queue_size");
            cmd.add("8");
            cmd.add("-f");
            cmd.add("s16le");
            cmd.add("-ar");
            cmd.add(properties.output().audioSampleRate());
            cmd.add("-ac");
            cmd.add("1");
            cmd.add("-i");
            cmd.add(camera.audioPipePath());
        }

        int audioInputStart = overlayEnabled ? (2 * n) : n;

        StringBuilder filter = new StringBuilder();

        if (overlayEnabled) {
            String panelW = properties.panel().width();
            String panelH = properties.panel().height();
            String fps = properties.output().fps();

            for (int i = 0; i < n; i++) {
                filter.append(String.format("[%d:v]setpts=PTS-STARTPTS[cam%d];", i, i));
            }
            for (int i = 0; i < n; i++) {
                filter.append(String.format("[%d:v]setpts=PTS-STARTPTS[mask%d];", n + i, i));
            }
            for (int i = 0; i < n; i++) {
                filter.append(String.format(
                        "color=red:size=%sx%s:rate=%s,setpts=PTS-STARTPTS[red%d];"
                                + "[red%d][mask%d]alphamerge[rgba%d];"
                                + "[cam%d][rgba%d]overlay=0:0:format=auto[panel%d];",
                        panelW, panelH, fps, i, i, i, i, i, i, i));
            }

            appendGrid(filter, n, "panel", targetAspect(properties));

            filter.append(String.format(
                    "drawtext=textfile=%s:reload=1:fontfile=%s:fontcolor=white:fontsize=28:"
                            + "box=1:boxcolor=black@0.6:boxborderw=12:x=(w-text_w)/2:y=h-text_h-24,",
                    DetectionStateStorage.LABEL_FILE, DetectionStateStorage.FONT_FILE));
        } else {
            for (int i = 0; i < n; i++) {
                filter.append(String.format("[%d:v]setpts=PTS-STARTPTS[cam%d];", i, i));
            }

            appendGrid(filter, n, "cam", targetAspect(properties));
        }

        filter.append(String.format(
                "fps=%s,scale=in_range=pc:out_range=tv:out_color_matrix=bt709,"
                        + "format=yuv420p,setparams=range=tv:color_primaries=bt709:"
                        + "color_trc=bt709:colorspace=bt709[outv];",
                properties.output().fps()));

        // Audio: mix all camera audio tracks
        for (int i = 0; i < n; i++) {
            filter.append(String.format("[%d:a]aresample=%s:async=1:first_pts=0,volume=0.25[a%d];",
                    audioInputStart + i, properties.output().audioSampleRate(), i));
        }
        for (int i = 0; i < n; i++) {
            filter.append(String.format("[a%d]", i));
        }
        filter.append(String.format("amix=inputs=%d:duration=longest:dropout_transition=2[outa]", n));

        cmd.add("-filter_complex");
        cmd.add(filter.toString());

        cmd.add("-map");
        cmd.add("[outv]");
        cmd.add("-map");
        cmd.add("[outa]");

        // Video encoding
        cmd.add("-c:v");
        cmd.add(properties.output().videoCodec());
        cmd.add("-preset");
        cmd.add(properties.output().videoPreset());
        cmd.add("-tune");
        cmd.add(properties.output().outputTune());
        cmd.add("-b:v");
        cmd.add(properties.output().videoBitrate());
        cmd.add("-maxrate");
        cmd.add(properties.output().maxrate());
        cmd.add("-bufsize");
        cmd.add(properties.output().bufsize());
        cmd.add("-g");
        cmd.add(properties.output().gop());
        cmd.add("-keyint_min");
        cmd.add(properties.output().gop());
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-color_range");
        cmd.add("tv");

        // Audio encoding
        cmd.add("-c:a");
        cmd.add(properties.output().audioCodec());
        cmd.add("-b:a");
        cmd.add(properties.output().audioBitrate());
        cmd.add("-ar");
        cmd.add(properties.output().audioSampleRate());
        cmd.add("-ac");
        cmd.add(String.valueOf(properties.output().audioChannels()));

        // Output (RTSP push to go2rtc)
        cmd.add("-rtsp_transport");
        cmd.add("tcp");
        cmd.add("-f");
        cmd.add("rtsp");
        String rtspUrl = properties.rtspUrl();
        if (rtspUrl.endsWith("/")) {
            rtspUrl = rtspUrl.substring(0, rtspUrl.length() - 1);
        }
        cmd.add(rtspUrl);

        return cmd;
    }

    private static void appendGrid(StringBuilder filter, int count, String cellPrefix, double targetAspect) {
        GridLayout.Grid grid = GridLayout.compute(count, targetAspect);
        int cols = grid.columns();
        int rows = grid.rows();

        if (rows == 1) {
            for (int i = 0; i < count; i++) {
                filter.append(String.format("[%s%d]", cellPrefix, i));
            }
            filter.append(String.format("hstack=inputs=%d,", count));
            return;
        }

        List<String> rowLabels = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            int start = r * cols;
            int end = Math.min(count, start + cols);
            if (end - start == 1) {
                rowLabels.add(String.format("[%s%d]", cellPrefix, start));
                continue;
            }
            String rowLabel = "row" + r;
            for (int i = start; i < end; i++) {
                filter.append(String.format("[%s%d]", cellPrefix, i));
            }
            filter.append(String.format("hstack=inputs=%d[%s];", end - start, rowLabel));
            rowLabels.add(String.format("[%s]", rowLabel));
        }

        for (String rowLabel : rowLabels) {
            filter.append(rowLabel);
        }
        filter.append(String.format("vstack=inputs=%d,", rows));
    }

    private static double targetAspect(LiveTransmissionProperties properties) {
        double width = Double.parseDouble(properties.output().width());
        double height = Double.parseDouble(properties.output().height());
        return height > 0 ? width / height : 16.0 / 9.0;
    }
}
