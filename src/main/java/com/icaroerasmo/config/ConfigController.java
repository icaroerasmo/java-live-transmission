package com.icaroerasmo.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public Map<String, Object> getConfig() {
        return configService.maskSecrets(configService.readConfig());
    }

    @PutMapping
    public ResponseEntity<Void> updateConfig(@RequestBody Map<String, Object> config) {
        Map<String, Object> current = configService.readConfig();
        Map<String, Object> merged = configService.restoreSecrets(config, current);
        configService.writeConfig(merged);

        Thread shutdownThread = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        });
        shutdownThread.setDaemon(true);
        shutdownThread.start();

        return ResponseEntity.ok().build();
    }
}
