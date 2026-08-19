package com.icaroerasmo.services;

import com.icaroerasmo.properties.CameraProperties;
import com.icaroerasmo.properties.LiveTransmissionProperties;
import com.icaroerasmo.storage.CameraStateStorage;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Log4j2
@Service
public class FrameMonitor {

    @Autowired
    private LiveTransmissionProperties properties;

    public void checkFrameFreshness(CameraStateStorage.CameraState state) {
        CameraProperties camera = state.camera();
        File currentFile = new File(camera.currentPath());

        if (!currentFile.exists()) {
            return;
        }

        long now = System.currentTimeMillis() / 1000;
        long fileModified = currentFile.lastModified() / 1000;

        if (fileModified < state.workerStartedAt()) {
            return;
        }

        long fileAge = now - fileModified;

        if (fileAge <= properties.watchdog().frameTimeoutSeconds()) {
            state.setLastFrameAt(now);
            state.setSeenFrame(true);

            String checksum = computeChecksum(currentFile);
            if (checksum != null) {
                if (checksum.equals(state.lastChecksum())) {
                    if (state.sameFrameSince() == null) {
                        state.setSameFrameSince(now);
                    }
                } else {
                    state.setLastChecksum(checksum);
                    state.setSameFrameSince(null);
                }
            }
        }
    }

    public boolean isFrameStale(CameraStateStorage.CameraState state) {
        long now = System.currentTimeMillis() / 1000;

        if (!state.seenFrame()) {
            return (now - state.workerStartedAt()) > properties.watchdog().startupFrameTimeoutSeconds();
        }

        if ((now - state.lastFrameAt()) > properties.watchdog().frameTimeoutSeconds()) {
            return true;
        }

        if (state.sameFrameSince() != null
                && (now - state.sameFrameSince()) >= properties.watchdog().staticFrameSeconds()) {
            return true;
        }

        return false;
    }

    private String computeChecksum(File file) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(file.getAbsolutePath()));
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }
}
