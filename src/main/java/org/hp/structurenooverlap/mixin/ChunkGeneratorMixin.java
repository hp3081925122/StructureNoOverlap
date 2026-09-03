package org.hp.structurenooverlap.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.structure.Structure;
import org.hp.structurenooverlap.api.StructureOverlapChecker;
import org.hp.structurenooverlap.data.CancelledStructuresData;
import org.hp.structurenooverlap.data.LocatedStructuresData;
import org.hp.structurenooverlap.world.StructureSectionClaim;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin implements StructureOverlapChecker {

    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    @Unique
    private final Map<Long, StructureSectionClaim> structurenooverlap$sectionClaims = new ConcurrentHashMap<>();

    @Unique
    private final Map<Long, Boolean> structurenooverlap$overlapChecks = new ConcurrentHashMap<>();

    @Unique
    private final Set<String> structurenooverlap$acceptedStructureStarts = ConcurrentHashMap.newKeySet();

    // 记录已经因重叠取消的结构起始位置，避免同一个结构跨区块或重新加载后重复触发取消日志。
    @Unique
    private final Set<String> structurenooverlap$cancelledStructureStarts = ConcurrentHashMap.newKeySet();

    @Unique
    private final ReentrantLock structurenooverlap$claimsLock = new ReentrantLock();

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
        ServerWorld level
    ) {
        if (!org.hp.structurenooverlap.Config.preventStructureOverlap) {
            return true;
        }

        if (org.hp.structurenooverlap.Config.isWhitelisted(structureId)) {
            LOGGER.debug("Structure {} is whitelisted, allowing generation", structureId);
            return true;
        }

        ChunkPos chunkPos = start.getPos();

        // 使用结构 ID 和起始区块组成稳定键，保证结构重新加载后仍能识别之前的取消状态。
        String cancellationKey = structureId + "|" + chunkPos.toLong();

        // 已经完成判定的结构不再访问定位数据或重新计算包围盒，避免同一结构跨区块放置时重复开销。
        if (structurenooverlap$acceptedStructureStarts.contains(cancellationKey)) {
            return true;
        }

        // 已经取消的结构不再重复执行冲突检测，并保持原有的取消结果。
        if (structurenooverlap$cancelledStructureStarts.contains(cancellationKey)) {
            return false;
        }

        List<StructurePlacement> placements = new ArrayList<>();
        streamStructureSets().forEach(structureSetEntry -> {
            StructureSet structureSet = structureSetEntry.value();
            if (structureSet.structures().stream().anyMatch(entry -> entry.structure().value() == start.getStructure())) {
                placements.add(structureSet.placement());
            }
        });
        boolean locatedTarget = LocatedStructuresData.get(level).isLocatedTarget(structureId, start, placements);

        // 被定位的结构允许越过已有占用，但仍登记空闲区域，保护后续生成的结构。
        if (locatedTarget) {
            long[] locatedSections = structurenooverlap$calculateSections(start);
            if (locatedSections.length == 0) {
                return true;
            }

            long locatedCenterPos = start.getBoundingBox().getCenter().asLong();
            StructureSectionClaim locatedClaim = new StructureSectionClaim(System.nanoTime(), structureId.toString(), locatedCenterPos);
            structurenooverlap$claimsLock.lock();
            try {
                if (structurenooverlap$acceptedStructureStarts.contains(cancellationKey)) {
                    return true;
                }
                for (long section : locatedSections) {
                    structurenooverlap$sectionClaims.putIfAbsent(section, locatedClaim);
                }
                structurenooverlap$acceptedStructureStarts.add(cancellationKey);
            } finally {
                structurenooverlap$claimsLock.unlock();
            }
            LOGGER.debug("Located structure {} at {} is exempt from overlap cancellation", structureId, chunkPos);
            return true;
        }

        long[] sections = structurenooverlap$calculateSections(start);
        if (sections.length == 0) {
            return true;
        }

        structurenooverlap$claimsLock.lock();
        try {
            if (structurenooverlap$acceptedStructureStarts.contains(cancellationKey)) {
                return true;
            }
            if (structurenooverlap$cancelledStructureStarts.contains(cancellationKey)) {
                return false;
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

                        // 只有首次发现该结构起始位置冲突时才记录和输出取消日志。
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

            structurenooverlap$acceptedStructureStarts.add(cancellationKey);
            return true;
        } finally {
            structurenooverlap$claimsLock.unlock();
        }
    }

    @Unique
    private long[] structurenooverlap$calculateSections(StructureStart start) {
        if (start.getChildren().isEmpty()) {
            return new long[0];
        }

        BlockBox bb = start.getBoundingBox();
        int minX = bb.getMinX() >> 4;
        int minY = bb.getMinY() >> 4;
        int minZ = bb.getMinZ() >> 4;
        int maxX = bb.getMaxX() >> 4;
        int maxY = bb.getMaxY() >> 4;
        int maxZ = bb.getMaxZ() >> 4;

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

    @Shadow
    public abstract Stream<RegistryEntry<StructureSet>> streamStructureSets();
}
