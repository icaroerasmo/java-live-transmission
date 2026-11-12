package com.icaroerasmo.services;

import com.icaroerasmo.properties.CameraProperties;
import com.icaroerasmo.properties.LiveTransmissionProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Guards the startup path when every camera is disabled: the compositor must not
 * be started (an ffmpeg graph with zero inputs is invalid) and the app must keep
 * booting with a no-op watchdog.
 */
class WatchdogScheduledTaskTest {

    @Test
    void startAll_comTodasAsCamerasDesabilitadas_naoDeveIniciarCompositor() throws Exception {
        WatchdogScheduledTask task = new WatchdogScheduledTask();
        CompositorService compositorService = mock(CompositorService.class);
        setField(task, "properties", properties(camera(1, false), camera(2, false)));
        setField(task, "compositorService", compositorService);

        assertDoesNotThrow(task::startAll);

        verifyNoInteractions(compositorService);
    }

    @Test
    void startAll_semCamerasNaLista_naoDeveIniciarCompositor() throws Exception {
        WatchdogScheduledTask task = new WatchdogScheduledTask();
        CompositorService compositorService = mock(CompositorService.class);
        setField(task, "properties", properties());
        setField(task, "compositorService", compositorService);

        assertDoesNotThrow(task::startAll);

        verifyNoInteractions(compositorService);
    }

    @Test
    void monitor_semCamerasHabilitadas_naoDeveFazerNada() throws Exception {
        WatchdogScheduledTask task = new WatchdogScheduledTask();
        CompositorService compositorService = mock(CompositorService.class);
        setField(task, "properties", properties(camera(1, false)));
        setField(task, "compositorService", compositorService);

        task.startAll();

        assertDoesNotThrow(task::monitor, "monitor() nao deve tentar reerguer o compositor sem cameras");

        verifyNoInteractions(compositorService);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = WatchdogScheduledTask.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static CameraProperties camera(int index, Boolean enabled) {
        return new CameraProperties(
                "camera" + index,
                "CAMERA " + index,
                "rtsp://cam-" + index,
                "256",
                "5000000",
                "5000000",
                "5000000",
                "10",
                enabled);
    }

    private static LiveTransmissionProperties properties(CameraProperties... cameras) {
        return new LiveTransmissionProperties(
                "rtsp://go2rtc:8554/panel",
                new LiveTransmissionProperties.OutputProperties(
                        "1200k", "1500k", "3000k", "30", "60",
                        "1280", "720",
                        "h264_nvenc", "p4", "ll",
                        "aac", "128k", "44100", 2),
                new LiveTransmissionProperties.PanelProperties("640", "360"),
                new LiveTransmissionProperties.InputProperties("256", "5000000", "5000000", "5000000"),
                new LiveTransmissionProperties.WatchdogProperties(20, 30, 3, 10, 8, 5),
                new LiveTransmissionProperties.DetectionOverlayProperties(true),
                new ArrayList<>(Arrays.asList(cameras)));
    }
}
