package com.icaroerasmo.parsers;

import com.icaroerasmo.properties.CameraProperties;
import com.icaroerasmo.properties.LiveTransmissionProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositorCommandParserTest {

    @Test
    void deveMontarGrid2x2_paraQuatroCameras() {
        String filter = filterFor(4, true);

        assertTrue(filter.contains("[panel0][panel1]hstack=inputs=2[row0];"),
                "linha do topo deveria hstack 2 paineis");
        assertTrue(filter.contains("[panel2][panel3]hstack=inputs=2[row1];"),
                "linha de baixo deveria hstack 2 paineis");
        assertTrue(filter.contains("[row0][row1]vstack=inputs=2,"));
    }

    @Test
    void deveMontarGrid3x2_paraSeisCameras() {
        String filter = filterFor(6, true);

        assertTrue(filter.contains("[panel0][panel1][panel2]hstack=inputs=3[row0];"));
        assertTrue(filter.contains("[panel3][panel4][panel5]hstack=inputs=3[row1];"));
        assertTrue(filter.contains("[row0][row1]vstack=inputs=2,"));
    }

    @Test
    void deveMontarGrid4x2_paraOitoCameras() {
        String filter = filterFor(8, true);

        assertTrue(filter.contains("[panel0][panel1][panel2][panel3]hstack=inputs=4[row0];"));
        assertTrue(filter.contains("[panel4][panel5][panel6][panel7]hstack=inputs=4[row1];"));
        assertTrue(filter.contains("[row0][row1]vstack=inputs=2,"));
    }

    @Test
    void deveMontarGrid2x1_paraDuasCameras() {
        String filter = filterFor(2, true);

        assertTrue(filter.contains("[panel0][panel1]hstack=inputs=2[row0];"));
        assertTrue(filter.contains("[row0],"));
        assertFalse(filter.contains("vstack"));
    }

    @Test
    void deveMontarGrid3x1_paraTresCameras() {
        String filter = filterFor(3, true);

        assertTrue(filter.contains("[panel0][panel1][panel2]hstack=inputs=3[row0];"));
        assertTrue(filter.contains("[row0],"));
        assertFalse(filter.contains("vstack"));
    }

    @Test
    void deveMontarGrid1x1_paraUmaCamera() {
        String filter = filterFor(1, true);

        assertTrue(filter.contains("[panel0],"));
        assertFalse(filter.contains("hstack"));
        assertFalse(filter.contains("vstack"));
    }

    @Test
    void deveManterDesenhoDeTextoNoOverlay() {
        String filter = filterFor(4, true);

        assertTrue(filter.contains("drawtext=textfile="));
    }

    @Test
    void naoDeveUsarMaskSemOverlay() {
        String filter = filterFor(4, false);

        assertFalse(filter.contains("alphamerge"));
        assertTrue(filter.contains("[cam0][cam1]hstack=inputs=2[row0];"));
    }

    @Test
    void deveExecutarAudioMixingParaTodasAsCameras() {
        String filter = filterFor(6, true);

        assertTrue(filter.contains("amix=inputs=6"));
    }

    private static String filterFor(int cameraCount, boolean overlayEnabled) {
        List<String> cmd = CompositorCommandParser.build(properties(cameraCount, overlayEnabled));
        int index = cmd.indexOf("-filter_complex");
        return cmd.get(index + 1);
    }

    private static LiveTransmissionProperties properties(int cameraCount, boolean overlayEnabled) {
        List<CameraProperties> cameras = new ArrayList<>();
        for (int i = 1; i <= cameraCount; i++) {
            String name = "camera" + i;
            cameras.add(new CameraProperties(
                    name,
                    "CAMERA " + i,
                    "rtsp://cam-" + i,
                    "256",
                    "5000000",
                    "5000000",
                    "5000000",
                    "10"));
        }

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
                new LiveTransmissionProperties.DetectionOverlayProperties(overlayEnabled),
                cameras);
    }
}