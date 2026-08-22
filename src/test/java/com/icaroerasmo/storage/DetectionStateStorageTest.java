package com.icaroerasmo.storage;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectionStateStorageTest {

    @Test
    void shouldMarkCameraAsActiveAfterUpdate() {
        DetectionStateStorage storage = new DetectionStateStorage();

        storage.update("garagem1", "Pessoa detectada");

        assertTrue(storage.isActive("garagem1"));
        assertFalse(storage.isActive("unknown-camera"));
    }

    @Test
    void shouldReturnLatestLabelAsPrimaryLabel() throws InterruptedException {
        DetectionStateStorage storage = new DetectionStateStorage();

        storage.update("garagem1", "Pessoa detectada");
        Thread.sleep(10); // ensure strictly increasing timestamps
        storage.update("garagem2", "Movimento detectado");

        assertEquals("Movimento detectado", storage.primaryLabel());
    }

    @Test
    void shouldReturnEmptyPrimaryLabelWhenNoState() {
        DetectionStateStorage storage = new DetectionStateStorage();

        assertEquals("", storage.primaryLabel());
    }

    @Test
    void shouldReportChangedCamerasAndExpireEntries() {
        DetectionStateStorage storage = new DetectionStateStorage();

        storage.update("garagem1", "Pessoa detectada");

        // first call: camera became active
        assertEquals(Set.of("garagem1"), storage.detectChanges(60_000));

        // no changes on second call
        assertTrue(storage.detectChanges(60_000).isEmpty());

        // a new camera becomes active
        storage.update("garagem2", "Movimento detectado");
        assertEquals(Set.of("garagem2"), storage.detectChanges(60_000));

        // negative ttl expires every entry: both become inactive -> both reported as changed
        assertEquals(Set.of("garagem1", "garagem2"), storage.detectChanges(-1));

        assertFalse(storage.isActive("garagem1"));
        assertFalse(storage.isActive("garagem2"));
    }

    @Test
    void shouldReturnAllActiveCamerasAsCopy() {
        DetectionStateStorage storage = new DetectionStateStorage();

        storage.update("garagem1", "Pessoa detectada");
        storage.update("garagem2", "Movimento detectado");

        Set<String> active = storage.activeCameras();
        assertEquals(Set.of("garagem1", "garagem2"), active);

        // returned set is a defensive copy
        active.clear();
        assertEquals(Set.of("garagem1", "garagem2"), storage.activeCameras());
    }
}
