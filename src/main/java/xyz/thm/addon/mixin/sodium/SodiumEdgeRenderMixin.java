/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.sodium;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders loaded edge chunks without requiring their missing neighbors. */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker", remap = false)
public abstract class SodiumEdgeRenderMixin {
    @Shadow
    private Long2IntOpenHashMap chunkStatus;
    @Shadow
    private LongOpenHashSet chunkReady;
    @Shadow
    private LongSet unloadQueue;
    @Shadow
    private LongSet loadQueue;

    @Inject(method = "updateMerged(II)V", at = @At("TAIL"))
    private void thm$forceReady(int x, int z, CallbackInfo ci) {
        long key = ChunkPos.pack(x, z);
        // Keep Sodium's unload handling; Voxy also consumes its ready-chunk set.
        if (this.chunkStatus.get(key) != 3) return;
        if (this.chunkReady.add(key) && !this.unloadQueue.remove(key)) {
            this.loadQueue.add(key);
        }
    }
}
