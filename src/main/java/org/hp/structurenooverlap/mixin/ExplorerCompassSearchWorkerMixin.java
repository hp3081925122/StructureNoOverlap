package org.hp.structurenooverlap.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.hp.structurenooverlap.compat.ExplorerCompassSearchContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "com.chaosthedude.explorerscompass.workers.StructureSearchWorker")
public abstract class ExplorerCompassSearchWorkerMixin {

    @Shadow(remap = false)
    protected ServerLevel level;

    @Shadow(remap = false)
    protected List<Structure> structureSet;

    @Inject(method = "getStructureGeneratingAt", at = @At("HEAD"), remap = false)
    private void structurenooverlap$beginCompassSearch(
        ChunkPos chunkPos,
        CallbackInfoReturnable<?> callbackInfo
    ) {
        ExplorerCompassSearchContext.begin(this, level, chunkPos, structureSet);
    }

    @Inject(method = "getStructureGeneratingAt", at = @At("RETURN"), remap = false)
    private void structurenooverlap$endCompassSearch(
        ChunkPos chunkPos,
        CallbackInfoReturnable<?> callbackInfo
    ) {
        ExplorerCompassSearchContext.end(this);
    }
}
