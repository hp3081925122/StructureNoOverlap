package org.hp.structurenooverlap.mixin;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import org.hp.structurenooverlap.api.StructureOverlapChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureStart.class)
public class StructureStartMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("structurenooverlap");

    @Unique
    private boolean structurenooverlap$checked;

    @Unique
    private boolean structurenooverlap$cancelled;

    @Shadow
    @Final
    private Structure structure;

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void beforePlace(
        StructureWorldAccess world,
        StructureAccessor structureAccessor,
        ChunkGenerator chunkGenerator,
        Random random,
        BlockBox chunkBox,
        ChunkPos chunkPos,
        CallbackInfo callbackInfo
    ) {
        if (structurenooverlap$checked) {
            return;
        }
        if (structurenooverlap$cancelled) {
            callbackInfo.cancel();
            return;
        }

        if (!(chunkGenerator instanceof StructureOverlapChecker checker)) {
            return;
        }

        ServerWorld serverWorld = world.toServerWorld();
        StructureStart self = (StructureStart) (Object) this;

        Registry<Structure> registry = serverWorld.getRegistryManager().get(RegistryKeys.STRUCTURE);
        Identifier structureId = registry.getId(structure);
        if (structureId == null) {
            LOGGER.debug("Skipping structure overlap check because the structure has no registry id at {}", chunkPos);
            return;
        }

        LOGGER.debug(
            "Structure overlap check reached for {} at {} via {}",
            structureId,
            chunkPos,
            world.getClass().getSimpleName()
        );

        if (!checker.tryClaimStructure(self, structureId, serverWorld)) {
            structurenooverlap$cancelled = true;
            callbackInfo.cancel();
        } else {
            structurenooverlap$checked = true;
        }
    }
}
