package org.hp.structurenooverlap.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.hp.structurenooverlap.api.StructureOverlapChecker;
import org.hp.structurenooverlap.data.CancelledStructuresData;
import org.hp.structurenooverlap.data.LocatedStructuresData;
import org.hp.structurenooverlap.world.StructureSectionClaim;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin implements StructureOverlapChecker {

    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    @Unique
    private final Map<Long, StructureSectionClaim> structurenooverlap$sectionClaims = new ConcurrentHashMap<>();

    @Unique
    private final Map<Long, Boolean> structurenooverlap$overlapChecks = new ConcurrentHashMap<>();

    // 记录已经因重叠取消的结构起始位置，避免同一个结构跨区块或重新加载后重复触发取消日志。
    @Unique
    private final Set<String> structurenooverlap$cancelledStructureStarts = ConcurrentHashMap.newKeySet();

    @Override
    public Map<Long, StructureSectionClaim> getStructureSectionClaims() {
        return structurenooverlap$sectionClaims;
    }

    @Override
    public Map<Long, Boolean> getOverlapChecks() {
        return structurenooverlap$overlapChecks;
    }

    @Override
    public boolean tryClaimStructure(
        StructureStart start,
        Identifier structureId,
        ServerLevel level
    ) {
        if (!org.hp.structurenooverlap.Config.preventStructureOverlap) {
            return true;
        }

        if (org.hp.structurenooverlap.Config.isWhitelisted(structureId)) {
            LOGGER.debug("Structure {} is whitelisted, allowing generation", structureId);
            return true;
        }

        ChunkPos chunkPos = start.getChunkPos();

        // 使用结构 ID 和起始区块组成稳定键，保证结构重新加载后仍能识别之前的取消状态。
        String cancellationKey = structureId + "|" + chunkPos.pack();

        boolean locatedTarget = LocatedStructuresData.get(level).isLocatedTarget(structureId, start, level);

        // 被定位的结构允许越过已有占用，但仍登记空闲区域，保护后续生成的结构。
        if (locatedTarget) {
            long[] locatedSections = structurenooverlap$calculateSections(start);
            if (locatedSections.length == 0) {
                return true;
            }

            long locatedCenterPos = start.getBoundingBox().getCenter().asLong();
            StructureSectionClaim locatedClaim = new StructureSectionClaim(System.nanoTime(), structureId.toString(), locatedCenterPos);
            for (long section : locatedSections) {
                structurenooverlap$sectionClaims.putIfAbsent(section, locatedClaim);
            }
            LOGGER.debug("Located structure {} at {} is exempt from overlap cancellation", structureId, chunkPos);
            return true;
        }

        // 已经取消的结构后续仍然返回 false，但不再重复执行检测和输出取消日志。
        if (structurenooverlap$cancelledStructureStarts.contains(cancellationKey)) {
            return false;
        }

        long[] sections = structurenooverlap$calculateSections(start);
        if (sections.length == 0) {
            return true;
        }

        long centerPos = start.getBoundingBox().getCenter().asLong();
        long token = System.nanoTime();

        StructureSectionClaim claim = new StructureSectionClaim(token, structureId.toString(), centerPos);

        for (int i = 0; i < sections.length; i++) {
            StructureSectionClaim existing = structurenooverlap$sectionClaims.putIfAbsent(sections[i], claim);

            if (existing != null) {
                if (!existing.structureId().equals(claim.structureId()) || existing.structureCenter() != claim.structureCenter()) {
                    for (int j = 0; j < i; j++) {
                        structurenooverlap$sectionClaims.remove(sections[j], claim);
                    }

                    // 只有首次发现该结构起始位置冲突时才记录和输出日志，后续调用直接保持取消状态。
                    if (structurenooverlap$cancelledStructureStarts.add(cancellationKey)) {
                        LOGGER.debug("Structure {} at {} cancelled due to overlap with {}",
                            structureId, chunkPos, existing.structureId());

                        CancelledStructuresData.get(level).recordCancelled(structureId, chunkPos);

                        if (org.hp.structurenooverlap.Config.logCancelledStructures) {
                            LOGGER.info("Structure generation cancelled: {} at {} (conflicts with {})",
                                structureId, chunkPos, existing.structureId());
                        }
                    }

                    return false;
                }
            }
        }

        return true;
    }

    @Unique
    private long[] structurenooverlap$calculateSections(StructureStart start) {
        if (start.getPieces().isEmpty()) {
            return new long[0];
        }

        var bb = start.getBoundingBox();
        int minX = bb.minX() >> 4;
        int minY = bb.minY() >> 4;
        int minZ = bb.minZ() >> 4;
        int maxX = bb.maxX() >> 4;
        int maxY = bb.maxY() >> 4;
        int maxZ = bb.maxZ() >> 4;

        int count = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        long[] sections = new long[count];
        int index = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    sections[index++] = (long) x & 4194303L | ((long) y & 1048575L) << 42 | (long) z << 20;
                }
            }
        }

        return sections;
    }
}
