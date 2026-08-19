package com.icaroerasmo.services;

import com.icaroerasmo.enums.MessagesEnum;
import com.icaroerasmo.parsers.FrameWorkerCommandParser;
import com.icaroerasmo.properties.CameraProperties;
import com.icaroerasmo.properties.LiveTransmissionProperties;
import com.icaroerasmo.runners.FfmpegRunner;
import com.icaroerasmo.storage.CameraStateStorage;
import com.icaroerasmo.util.TelegramUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Log4j2
@Service
public class FrameWorkerService {

    @Autowired
    private LiveTransmissionProperties properties;

    @Autowired
    private TelegramUtil telegramUtil;

    @Autowired
    private ExecutorService executorService;

    @Autowired
    private CameraStateStorage cameraStateStorage;

    private final Map<String, FfmpegRunner> runners = new ConcurrentHashMap<>();

    public void startWorker(CameraProperties camera) {
        stopWorker(camera.name());

        FfmpegRunner runner = new FfmpegRunner("frame-worker-" + camera.name());
        List<String> command = FrameWorkerCommandParser.build(camera, properties);

        log.info("[FrameWorker] Starting frame worker for {}", camera.name());
        Process process = runner.start(command);

        if (process != null) {
            runners.put(camera.name(), runner);
            CameraStateStorage.CameraState state = cameraStateStorage.getOrCreate(camera);
            state.setWorkerProcess(process);
            state.setWorkerStartedAt(System.currentTimeMillis() / 1000);
            state.setSeenFrame(false);
            state.setLastFrameAt(0);
            state.setLastChecksum(null);
            state.setSameFrameSince(null);

            telegramUtil.sendMessage(MessagesEnum.CAMERA_STARTED, camera.label());
            log.info("[FrameWorker] Frame worker started for {}", camera.name());
        } else {
            log.error("[FrameWorker] Failed to start frame worker for {}", camera.name());
        }
    }

    public void stopWorker(String cameraName) {
        FfmpegRunner runner = runners.remove(cameraName);
        if (runner != null) {
            runner.destroy();
            log.info("[FrameWorker] Stopped frame worker for {}", cameraName);
        }

        CameraStateStorage.CameraState state = cameraStateStorage.get(cameraName);
        if (state != null) {
            state.setWorkerProcess(null);
        }
    }

    public boolean isWorkerAlive(String cameraName) {
        FfmpegRunner runner = runners.get(cameraName);
        return runner != null && runner.isAlive();
    }

    public void stopAll() {
        for (String name : runners.keySet()) {
            stopWorker(name);
        }
    }
}
