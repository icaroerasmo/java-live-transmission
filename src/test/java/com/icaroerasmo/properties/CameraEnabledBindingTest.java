package com.icaroerasmo.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraEnabledBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConfigYaml.class, PropertiesConfiguration.class);

    @Test
    void cameraComEnabledLiteralTrue_noConfigYaml_deveFicarHabilitada() {
        runner.run(context -> {
            LiveTransmissionProperties properties = context.getBean(LiveTransmissionProperties.class);
            assertTrue(properties.cameras().get(0).isEnabled(),
                    "enabled: true do config.yaml deveria virar booleano true");
        });
    }

    @Test
    void cameraComEnabledLiteralFalse_deveFicarDesabilitada() {
        runner.withPropertyValues("live.cameras[0].enabled=false").run(context -> {
            LiveTransmissionProperties properties = context.getBean(LiveTransmissionProperties.class);
            assertFalse(properties.cameras().get(0).isEnabled());
        });
    }

    @Test
    void cameraSemEnabled_deveFicarHabilitada() {
        CameraProperties camera = new CameraProperties(
                "camera1", "CAMERA 1", "rtsp://cam-1", "256", "5000000", "5000000", "5000000", "10", null);

        assertTrue(camera.isEnabled(), "chave ausente no yaml = null = habilitada");
    }

    @EnableConfigurationProperties(LiveTransmissionProperties.class)
    static class PropertiesConfiguration {}
}
