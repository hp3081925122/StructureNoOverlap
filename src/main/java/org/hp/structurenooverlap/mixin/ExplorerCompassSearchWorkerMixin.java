package org.hp.structurenooverlap.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.hp.structurenooverlap.compat.ExplorerCompassSearchContext;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Pseudo
@Mixin(targets = "com.chaosthedude.explorerscompass.worker.StructureSearchWorker", remap = false)
public abstract class ExplorerCompassSearchWorkerMixin {

    @Unique
    private static final Logger structurenooverlap$LOGGER = LogUtils.getLogger();

    @Unique
    private boolean structurenooverlap$loggedCompatibility;

    @Shadow(remap = false)
    protected List<Structure> structureSet;

    @Redirect(
        method = "getStructureGeneratingAt",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            remap = false
        ),
        remap = false
    )
    private ChunkAccess structurenooverlap$loadSearchChunk(
        ServerLevel level,
        int chunkX,
        int chunkZ,
        ChunkStatus status
    ) {
        return structurenooverlap$loadChunkWithSearchContext(level, chunkX, chunkZ, status);
    }

    @Redirect(
        method = "confirmStructureAt",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private ChunkAccess structurenooverlap$loadConfirmedChunk(
        ServerLevel level,
        int chunkX,
        int chunkZ,
        ChunkStatus status
    ) {
        return structurenooverlap$loadChunkWithSearchContext(level, chunkX, chunkZ, status);
    }

    @Unique
    private ChunkAccess structurenooverlap$loadChunkWithSearchContext(
        ServerLevel level,
        int chunkX,
        int chunkZ,
        ChunkStatus status
    ) {
        if (!structurenooverlap$loggedCompatibility) {
            structurenooverlap$loggedCompatibility = true;
            structurenooverlap$LOGGER.debug(
                "Explorer's Compass overlap compatibility active for {} in {} with {} target structures",
                getClass().getName(), level.dimension().location(), structureSet.size()
            );
        }
        ExplorerCompassSearchContext.begin(
            this, level, new net.minecraft.world.level.ChunkPos(chunkX, chunkZ), structureSet
        );
        try {
            return level.getChunk(chunkX, chunkZ, status);
        } finally {
            ExplorerCompassSearchContext.end(this);
        }
    }
}
