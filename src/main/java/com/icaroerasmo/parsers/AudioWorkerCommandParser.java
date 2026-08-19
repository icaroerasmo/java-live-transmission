package com.icaroerasmo.parsers;

import com.icaroerasmo.properties.CameraProperties;
import com.icaroerasmo.properties.LiveTransmissionProperties;

import java.util.ArrayList;
import java.util.List;

public class AudioWorkerCommandParser {

    public static List<String> build(CameraProperties camera, LiveTransmissionProperties properties) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("warning");
        cmd.add("-nostdin");
        cmd.add("-fflags");
        cmd.add("+genpts+discardcorrupt");
        cmd.add("-thread_queue_size");
        cmd.add(camera.threadQueueSize());
        cmd.add("-rtsp_transport");
        cmd.add("tcp");
        cmd.add("-timeout");
        cmd.add(camera.rtspTimeoutUs());
        cmd.add("-i");
        cmd.add(camera.rtspUrl());
        cmd.add("-map");
        cmd.add("0:a:0");
        cmd.add("-vn");
        cmd.add("-c:a");
        cmd.add("pcm_s16le");
        cmd.add("-ar");
        cmd.add(properties.output().audioSampleRate());
        cmd.add("-ac");
        cmd.add("1");
        cmd.add("-f");
        cmd.add("s16le");
        cmd.add("pipe:1");
        return cmd;
    }
}
