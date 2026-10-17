/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.sodium;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class SodiumEdgeRenderMixinTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void missingOrIncompleteChunksNeverBecomeReady(int status) throws Exception {
        var tracker = new Tracker();
        tracker.update(4, -7, status);
        assertTrue(tracker.ready.isEmpty());
        assertTrue(tracker.loads.isEmpty());
        assertTrue(tracker.unloads.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void loadedEdgeChunkRendersThenUnloadsWhenBlockOrLightDataIsLost(int status) throws Exception {
        var tracker = new Tracker();
        long key = ChunkPos.pack(4, -7);
        tracker.update(4, -7, 3);
        assertTrue(tracker.ready.contains(key));
        assertTrue(tracker.loads.contains(key));
        tracker.loads.clear();

        tracker.update(4, -7, status);
        assertFalse(tracker.ready.contains(key));
        assertTrue(tracker.loads.isEmpty());
        assertTrue(tracker.unloads.contains(key));
    }

    @Test
    void repeatedNeighborUpdatesDoNotDuplicateLoadEvents() throws Exception {
        var tracker = new Tracker();
        tracker.update(4, -7, 3);
        tracker.loads.clear();
        tracker.update(4, -7, 3);
        assertEquals(1, tracker.ready.size());
        assertTrue(tracker.loads.isEmpty());
        assertTrue(tracker.unloads.isEmpty());
    }

    @Test
    void pendingLoadAndUnloadEventsStillCoalesce() throws Exception {
        var tracker = new Tracker();
        tracker.update(4, -7, 3);
        tracker.update(4, -7, 0);
        assertTrue(tracker.ready.isEmpty());
        assertTrue(tracker.loads.isEmpty());
        assertTrue(tracker.unloads.isEmpty());

        tracker.update(4, -7, 3);
        tracker.loads.clear();
        tracker.update(4, -7, 0);
        tracker.update(4, -7, 3);
        assertEquals(1, tracker.ready.size());
        assertTrue(tracker.loads.isEmpty());
        assertTrue(tracker.unloads.isEmpty());
    }

    @Test
    void travellingDoesNotRetainPreviouslyUnloadedChunks() throws Exception {
        var tracker = new Tracker();
        for (int x = 0; x < 500; x++) {
            tracker.update(x, 0, 3);
            tracker.loads.clear();
            tracker.update(x, 0, 0);
            tracker.unloads.clear();
        }
        assertTrue(tracker.ready.isEmpty());
        assertTrue(tracker.statuses.isEmpty());
    }

    // Run Sodium's real readiness logic, followed by THM's injected tail, without a game.
    private static final class Tracker {
        private final Object sodium;
        private final SodiumEdgeRenderMixin mixin = new SodiumEdgeRenderMixin() {};
        private final Method update, tail;
        private final Long2IntOpenHashMap statuses;
        private final LongSet ready, loads, unloads;

        private Tracker() throws Exception {
            Class<?> type = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker");
            sodium = type.getConstructor().newInstance();
            for (var field : SodiumEdgeRenderMixin.class.getDeclaredFields()) {
                var source = type.getDeclaredField(field.getName());
                source.setAccessible(true);
                field.setAccessible(true);
                field.set(mixin, source.get(sodium));
            }
            statuses = (Long2IntOpenHashMap) field(type, "chunkStatus");
            ready = (LongSet) field(type, "chunkReady");
            loads = (LongSet) field(type, "loadQueue");
            unloads = (LongSet) field(type, "unloadQueue");
            update = type.getDeclaredMethod("updateMerged", int.class, int.class);
            update.setAccessible(true);
            tail = SodiumEdgeRenderMixin.class.getDeclaredMethod("thm$forceReady", int.class, int.class, CallbackInfo.class);
            tail.setAccessible(true);
        }

        private Object field(Class<?> type, String name) throws Exception {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(sodium);
        }

        private void update(int x, int z, int status) throws Exception {
            if (status == 0) statuses.remove(ChunkPos.pack(x, z));
            else statuses.put(ChunkPos.pack(x, z), status);
            update.invoke(sodium, x, z);
            tail.invoke(mixin, x, z, new CallbackInfo("updateMerged", false));
        }
    }
}
