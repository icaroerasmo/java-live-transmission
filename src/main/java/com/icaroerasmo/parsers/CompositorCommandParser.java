package com.icaroerasmo.parsers;

import com.icaroerasmo.properties.CameraProperties;
import com.icaroerasmo.properties.LiveTransmissionProperties;
import com.icaroerasmo.storage.DetectionStateStorage;

import java.util.ArrayList;
import java.util.List;

public class CompositorCommandParser {

    public static List<String> build(LiveTransmissionProperties properties) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("info");
        cmd.add("-nostdin");
        cmd.add("-nostats");

        List<CameraProperties> cameras = properties.cameras();

        // Image2pipe inputs from frame workers (video)
        for (CameraProperties camera : cameras) {
            cmd.add("-thread_queue_size");
            cmd.add("8");
            cmd.add("-framerate");
            cmd.add(properties.output().fps());
            cmd.add("-f");
            cmd.add("image2pipe");
            cmd.add("-vcodec");
            cmd.add("mjpeg");
            cmd.add("-i");
            cmd.add(camera.pipePath());
        }

        // Border overlay inputs (raw RGBA, minimal buffering for low latency)
        for (CameraProperties camera : cameras) {
            cmd.add("-thread_queue_size");
            cmd.add("1");
            cmd.add("-framerate");
            cmd.add(properties.output().fps());
            cmd.add("-f");
            cmd.add("rawvideo");
            cmd.add("-pix_fmt");
            cmd.add("rgba");
            cmd.add("-video_size");
            cmd.add(properties.panel().width() + "x" + properties.panel().height());
            cmd.add("-i");
            cmd.add(camera.borderPipePath());
        }

        // Raw PCM inputs remain live because their feeders substitute silence
        // whenever a camera audio worker is unavailable.
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

        // Build filter complex
        int n = cameras.size();
        StringBuilder filter = new StringBuilder();

        // Video + border: setpts, then overlay the border onto each panel
        for (int i = 0; i < n; i++) {
            filter.append(String.format("[%d:v]setpts=PTS-STARTPTS[cam%d];", i, i));
            filter.append(String.format("[%d:v]setpts=PTS-STARTPTS[border%d];", n + i, i));
            filter.append(String.format("[cam%d][border%d]overlay=0:0:format=auto:shortest=0:eof_action=repeat:repeatlast=1[panel%d];", i, i, i));
        }

        // 2x2 grid layout
        if (n == 4) {
            filter.append("[panel0][panel1]hstack=inputs=2[top];");
            filter.append("[panel2][panel3]hstack=inputs=2[bottom];");
            filter.append("[top][bottom]vstack=inputs=2,");
        } else if (n == 2) {
            filter.append("[panel0][panel1]hstack=inputs=2,");
        } else if (n == 1) {
            filter.append("[panel0],");
        }

        // Bottom detection label (live-updatable via textfile reload)
        filter.append(String.format(
                "drawtext=textfile=%s:reload=1:fontfile=%s:fontcolor=white:fontsize=28:"
                        + "box=1:boxcolor=black@0.6:boxborderw=12:x=(w-text_w)/2:y=h-text_h-24,",
                DetectionStateStorage.LABEL_FILE, DetectionStateStorage.FONT_FILE));

        filter.append(String.format(
                "fps=%s,scale=in_range=pc:out_range=tv:out_color_matrix=bt709,"
                        + "format=yuv420p,setparams=range=tv:color_primaries=bt709:"
                        + "color_trc=bt709:colorspace=bt709[outv];",
                properties.output().fps()));

        // Audio: mix all camera audio tracks
        for (int i = 0; i < n; i++) {
            filter.append(String.format("[%d:a]aresample=%s:async=1:first_pts=0,volume=0.25[a%d];",
                    2 * n + i, properties.output().audioSampleRate(), i));
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

        // Video encoding - matches Perl script exactly (h264_nvenc defaults)
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

        // Output
        cmd.add("-flvflags");
        cmd.add("no_duration_filesize");
        cmd.add("-f");
        cmd.add("flv");
        String rtmpUrl = properties.rtmpUrl();
        if (rtmpUrl.endsWith("/")) {
            rtmpUrl = rtmpUrl.substring(0, rtmpUrl.length() - 1);
        }
        cmd.add(rtmpUrl + "/" + properties.streamKey());

        return cmd;
    }
}
