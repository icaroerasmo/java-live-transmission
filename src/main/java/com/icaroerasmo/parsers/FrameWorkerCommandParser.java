package com.icaroerasmo.parsers;

import com.icaroerasmo.properties.CameraProperties;
import com.icaroerasmo.properties.LiveTransmissionProperties;

import java.util.ArrayList;
import java.util.List;

public class FrameWorkerCommandParser {

    public static List<String> build(CameraProperties camera, LiveTransmissionProperties properties) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("info");
        cmd.add("-nostdin");
        cmd.add("-nostats");
        cmd.add("-fflags");
        cmd.add("+genpts+discardcorrupt");
        cmd.add("-timeout");
        cmd.add(camera.rtspTimeoutUs());
        cmd.add("-thread_queue_size");
        cmd.add(camera.threadQueueSize());
        cmd.add("-probesize");
        cmd.add(camera.probesize());
        cmd.add("-analyzeduration");
        cmd.add(camera.analyzeduration());
        cmd.add("-rtsp_transport");
        cmd.add("tcp");
        cmd.add("-i");
        cmd.add(camera.rtspUrl());

        String panelWidth = properties.panel().width();
        String panelHeight = properties.panel().height();
        String fps = camera.filterFps();

        String filter = String.format(
                "fps=%s,showinfo@%s,"
                        + "setpts=PTS-STARTPTS,split=2[bg_src][fg_src];"
                        + "[bg_src]scale=%s:%s:force_original_aspect_ratio=increase,crop=%s:%s,boxblur=12:1,setsar=1[bg];"
                        + "[fg_src]scale=%s:%s:force_original_aspect_ratio=decrease,setsar=1[fg];"
                        + "[bg][fg]overlay=(W-w)/2:(H-h)/2,format=yuvj420p[out]",
                fps, camera.name(),
                panelWidth, panelHeight, panelWidth, panelHeight,
                panelWidth, panelHeight
        );

        cmd.add("-filter_complex");
        cmd.add(filter);
        cmd.add("-map");
        cmd.add("[out]");
        cmd.add("-an");
        cmd.add("-c:v");
        cmd.add("mjpeg");
        cmd.add("-q:v");
        cmd.add("4");
        cmd.add("-update");
        cmd.add("1");
        cmd.add("-atomic_writing");
        cmd.add("1");
        cmd.add("-f");
        cmd.add("image2");
        cmd.add("-y");
        cmd.add(camera.currentPath());

        return cmd;
    }
}
