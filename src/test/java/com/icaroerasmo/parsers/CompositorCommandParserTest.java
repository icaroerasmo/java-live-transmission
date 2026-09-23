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

        assertTrue(filter.contains("[panel0][panel1]hstack=inputs=2,"),
                "linha unica deveria conectar hstack direto a cadeia");
        assertFalse(filter.contains("[row0],"));
        assertFalse(filter.contains("vstack"));
    }

    @Test
    void deveMontarGrid3x1_paraTresCameras() {
        String filter = filterFor(3, true);

        assertTrue(filter.contains("[panel0][panel1][panel2]hstack=inputs=3,"),
                "linha unica deveria conectar hstack direto a cadeia");
        assertFalse(filter.contains("[row0],"));
        assertFalse(filter.contains("vstack"));
    }

    @Test
    void deveMontarGrid1x1_paraUmaCamera() {
        String filter = filterFor(1, true);

        assertTrue(filter.contains("[panel0]hstack=inputs=1,"),
                "camera unica deveria usar hstack=inputs=1 para nao deixar label orfao");
        assertFalse(filter.contains("[panel0],"));
        assertFalse(filter.contains("vstack"));
    }

    @Test
    void gridDeLinhaUnica_deveSerValidoParaOFilterGraphFfmpeg() {
        String filter = filterFor(2, true);

        assertFalse(filter.contains("[row0],"),
                "label seguido de virgula sem filtro gera 'No such filter: \\'\\'' no ffmpeg");
        assertTrue(filter.contains("hstack=inputs=2,drawtext="),
                "hstack deveria continuar a cadeia direto para o drawtext");
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

    @Test
    void outputRtsp_deveUsarMuxerRtspETransporteTcp() {
        List<String> cmd = CompositorCommandParser.build(properties(2, true, "rtsp://go2rtc:8554/panel"));

        assertTrue(cmd.contains("-rtsp_transport"), "RTSP deveria forcar transporte tcp");
        assertTrue(cmd.contains("rtsp"), "RTSP deveria usar muxer rtsp");
        assertTrue(cmd.contains("rtsp://go2rtc:8554/panel"));
        assertFalse(cmd.contains("flv"), "RTSP nao deveria usar flv");
    }

    @Test
    void outputRtmp_deveUsarMuxerFlv() {
        List<String> cmd = CompositorCommandParser.build(properties(2, true, "rtmp://ingest.example.com/live/key"));

        assertTrue(cmd.contains("-f"));
        assertTrue(cmd.contains("flv"), "RTMP deveria usar muxer flv");
        assertFalse(cmd.contains("-rtsp_transport"), "RTMP nao deveria forcar transporte rtsp");
        assertTrue(cmd.contains("rtmp://ingest.example.com/live/key"));
    }

    @Test
    void outputRtmps_deveUsarMuxerFlv() {
        List<String> cmd = CompositorCommandParser.build(properties(2, true, "rtmps://dc1-1.rtmp.t.me/s/KEY"));

        assertTrue(cmd.contains("flv"), "RTMPS deveria usar muxer flv");
        assertFalse(cmd.contains("-rtsp_transport"), "RTMPS nao deveria forcar transporte rtsp");
        assertTrue(cmd.contains("rtmps://dc1-1.rtmp.t.me/s/KEY"));
    }

    private static String filterFor(int cameraCount, boolean overlayEnabled) {
        List<String> cmd = CompositorCommandParser.build(properties(cameraCount, overlayEnabled));
        int index = cmd.indexOf("-filter_complex");
        return cmd.get(index + 1);
    }

    private static LiveTransmissionProperties properties(int cameraCount, boolean overlayEnabled) {
        return properties(cameraCount, overlayEnabled, "rtsp://go2rtc:8554/panel");
    }

    private static LiveTransmissionProperties properties(int cameraCount, boolean overlayEnabled, String outputUrl) {
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
                outputUrl,
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