package org.hp.structurenooverlap.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
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

    @Unique
    private boolean structurenooverlap$checked;

    @Unique
    private boolean structurenooverlap$cancelled;

    @Shadow @Final private Structure structure;

    @Inject(
        method = "placeInChunk",
        at = @At("HEAD"),
        cancellable = true
    )
    private void beforePlaceInChunk(
        net.minecraft.world.level.WorldGenLevel level,
        net.minecraft.world.level.StructureManager structureManager,
        ChunkGenerator chunkGenerator,
        net.minecraft.util.RandomSource random,
        net.minecraft.world.level.levelgen.structure.BoundingBox boundingBox,
        net.minecraft.world.level.ChunkPos chunkPos,
        CallbackInfo ci
    ) {
        if (structurenooverlap$checked) {
            return;
        }
        if (structurenooverlap$cancelled) {
            ci.cancel();
            return;
        }

        // 结构生成阶段使用 WorldGenRegion，这里需要取回它对应的服务端世界。
        ServerLevel serverLevel;
        if (level instanceof ServerLevel directServerLevel) {
            serverLevel = directServerLevel;
        } else if (level instanceof WorldGenRegion worldGenRegion) {
            serverLevel = worldGenRegion.getLevel();
        } else {
            return;
        }

        if (chunkGenerator instanceof org.hp.structurenooverlap.api.StructureOverlapChecker checker) {
            StructureStart self = (StructureStart) (Object) this;

            // 从当前结构注册表直接取得 ID，避免依赖直接 Holder 是否带有注册键。
            Registry<Structure> registry = serverLevel.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);
            ResourceLocation structureId = registry.getKey(structure);
            if (structureId == null) {
                LOGGER.debug("Skipping structure overlap check because the structure has no registry id at {}", chunkPos);
                return;
            }

            LOGGER.debug("Structure overlap check reached for {} at {} via {}", structureId, chunkPos, level.getClass().getSimpleName());

            if (!checker.tryClaimStructure(self, structureId, serverLevel)) {
                structurenooverlap$cancelled = true;
                ci.cancel();
            } else {
                structurenooverlap$checked = true;
            }
        }
    }
}
