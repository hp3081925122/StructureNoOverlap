package org.hp.structurenooverlap.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import org.slf4j.Logger;
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
    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow @Final private Structure structure;

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void beforePlace(
        StructureWorldAccess world,
        StructureAccessor structureAccessor,
        ChunkGenerator chunkGenerator,
        Random random,
        BlockBox boundingBox,
        ChunkPos chunkPos,
        CallbackInfo ci
    ) {
        ServerWorld serverWorld = world.toServerWorld();

        if (chunkGenerator instanceof org.hp.structurenooverlap.api.StructureOverlapChecker checker) {
            StructureStart self = (StructureStart) (Object) this;

            // 从当前结构注册表直接取得 ID，避免依赖直接 Holder 是否带有注册键。
            Registry<Structure> registry = serverWorld.getRegistryManager().get(Registry.STRUCTURE_KEY);
            Identifier structureId = registry.getKey(structure).map(key -> key.getValue()).orElse(null);
            if (structureId == null) {
                LOGGER.debug("Skipping structure overlap check because the structure has no registry id at {}", chunkPos);
                return;
            }

            LOGGER.debug("Structure overlap check reached for {} at {} via {}", structureId, chunkPos, world.getClass().getSimpleName());

            if (!checker.tryClaimStructure(self, structureId, serverWorld)) {
                ci.cancel();
            }
        }
    }
}
