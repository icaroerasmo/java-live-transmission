package com.icaroerasmo.runners;

import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Log4j2
public class FfmpegRunner {

    private Process process;
    private final String name;

    public FfmpegRunner(String name) {
        this.name = name;
    }

    public Process start(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            process = pb.start();

            startLogReader(process.getInputStream(), line -> log.debug("[{}] {}", name, line));
            startLogReader(process.getErrorStream(), line -> {
                if (line.contains("error") || line.contains("Error") || line.contains("Missing")) {
                    log.warn("[{}] {}", name, line);
                } else {
                    log.debug("[{}] {}", name, line);
                }
            });

            log.info("[{}] Started with pid {}", name, process.pid());
            return process;
        } catch (Exception e) {
            log.error("[{}] Failed to start process", name, e);
            return null;
        }
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    public void destroy() {
        if (process != null && process.isAlive()) {
            log.info("[{}] Destroying process", name);
            process.destroyForcibly();
            try {
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        process = null;
    }

    public int exitCode() {
        if (process == null) return -1;
        return process.exitValue();
    }

    public Process process() {
        return process;
    }

    private void startLogReader(java.io.InputStream is, Consumer<String> handler) {
        Thread.startVirtualThread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handler.accept(line);
                }
            } catch (Exception e) {
                if (process != null && process.isAlive()) {
                    log.error("[{}] Log reader error", name, e);
                }
            }
        });
    }
}
