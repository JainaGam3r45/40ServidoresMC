package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.util.PendingAckStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPendingAckStore {

    @Test
    void takeRemovesStoredIds() {
        PendingAckStore store = new PendingAckStore();
        store.add("Cadiducho", Arrays.asList(1L, 2L));

        assertEquals(Arrays.asList(1L, 2L), store.peek("Cadiducho"));
        assertEquals(Arrays.asList(1L, 2L), store.take("Cadiducho"));
        assertTrue(store.peek("Cadiducho").isEmpty());
        assertTrue(store.take("Cadiducho").isEmpty());
    }

    @Test
    void ignoresEmptyAdds() {
        PendingAckStore store = new PendingAckStore(Duration.ofHours(1));
        store.add("Cadiducho", Collections.<Long>emptyList());
        store.add(null, Collections.singletonList(1L));
        assertTrue(store.isEmpty());
    }
}
