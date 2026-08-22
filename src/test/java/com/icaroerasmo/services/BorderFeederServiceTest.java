package com.icaroerasmo.services;

import com.icaroerasmo.properties.LiveTransmissionProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@link BorderFeederService#generateBorderImages()} produces a border
 * mask of the expected size. No threads, no processes, no network: the properties
 * are injected reflectively and the generated mask is read back reflectively
 * because the class keeps both in private fields without accessors.
 */
class BorderFeederServiceTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int BORDER_THICKNESS = 4;

    @Test
    void shouldGenerateBorderMaskOfExpectedSize() throws Exception {
        BorderFeederService service = new BorderFeederService();

        LiveTransmissionProperties properties = new LiveTransmissionProperties(
                null, null, null,
                new LiveTransmissionProperties.PanelProperties(String.valueOf(WIDTH), String.valueOf(HEIGHT)),
                null, null, null, null
        );
        setField(service, "properties", properties);

        service.generateBorderImages();

        byte[] borderMask = (byte[]) getField(service, "borderMaskBytes");
        byte[] clearMask = (byte[]) getField(service, "clearMaskBytes");

        assertEquals(WIDTH * HEIGHT, borderMask.length, "border mask size");
        assertEquals(WIDTH * HEIGHT, clearMask.length, "clear mask size");

        // border pixels at the four corners and at the center of each edge
        assertEquals(255, pixel(borderMask, 0, 0));
        assertEquals(255, pixel(borderMask, WIDTH - 1, 0));
        assertEquals(255, pixel(borderMask, 0, HEIGHT - 1));
        assertEquals(255, pixel(borderMask, WIDTH - 1, HEIGHT - 1));

        assertEquals(255, pixel(borderMask, WIDTH / 2, 0));
        assertEquals(255, pixel(borderMask, WIDTH / 2, HEIGHT - 1));
        assertEquals(255, pixel(borderMask, 0, HEIGHT / 2));
        assertEquals(255, pixel(borderMask, WIDTH - 1, HEIGHT / 2));

        // interior pixel must be zero
        assertEquals(0, pixel(borderMask, WIDTH / 2, HEIGHT / 2));

        // clear mask must be all zeros
        int nonZero = 0;
        for (byte value : clearMask) {
            if (value != 0) {
                nonZero++;
            }
        }
        assertEquals(0, nonZero, "clear mask must be all zeros");
    }

    private static int pixel(byte[] mask, int x, int y) {
        return Byte.toUnsignedInt(mask[y * WIDTH + x]);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = BorderFeederService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = BorderFeederService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
