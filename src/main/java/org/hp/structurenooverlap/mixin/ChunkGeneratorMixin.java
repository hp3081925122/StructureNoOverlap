package org.hp.structurenooverlap.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.StructureHolder;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import org.hp.structurenooverlap.Config;
import org.hp.structurenooverlap.api.StructureOverlapChecker;
import org.hp.structurenooverlap.compat.ExplorerCompassSearchContext;
import org.hp.structurenooverlap.data.AcceptedStructuresData;
import org.hp.structurenooverlap.data.CancelledStructuresData;
import org.hp.structurenooverlap.data.LocatedStructuresData;
import org.hp.structurenooverlap.world.StructureSectionClaim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin implements StructureOverlapChecker {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("structurenooverlap");

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

    @Unique
    private volatile ServerWorld structurenooverlap$claimsLevel;

    @Unique
    private volatile boolean structurenooverlap$claimsLoaded;

    // 在结构起点写入区块前完成一次检测，避免等到结构逐区块放置时重复扫描完整包围盒。
    @org.spongepowered.asm.mixin.injection.Redirect(
        method = "trySetStructureStart",
        at = @org.spongepowered.asm.mixin.injection.At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/gen/StructureAccessor;setStructureStart(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/world/gen/structure/Structure;Lnet/minecraft/structure/StructureStart;Lnet/minecraft/world/StructureHolder;)V"
        )
    )
    private void structurenooverlap$checkBeforeStart(
        StructureAccessor structureAccessor,
        ChunkSectionPos sectionPos,
        Structure structure,
        StructureStart start,
        StructureHolder structureHolder
    ) {
        net.minecraft.world.WorldAccess worldAccess = ((StructureAccessorAccessor) (Object) structureAccessor).structurenooverlap$getWorld();
        ServerWorld serverWorld;
        if (worldAccess instanceof ServerWorld directServerWorld) {
            serverWorld = directServerWorld;
        } else if (worldAccess instanceof ChunkRegion chunkRegion) {
            serverWorld = chunkRegion.toServerWorld();
        } else {
            structureAccessor.setStructureStart(sectionPos, structure, start, structureHolder);
            return;
        }

        Registry<Structure> registry = serverWorld.getRegistryManager().get(RegistryKeys.STRUCTURE);
        Identifier structureId = registry.getId(structure);
        if (structureId == null) {
            structureAccessor.setStructureStart(sectionPos, structure, start, structureHolder);
            return;
        }
        if (!tryClaimStructure(start, structureId, serverWorld)) {
            return;
        }

        structureAccessor.setStructureStart(sectionPos, structure, start, structureHolder);
    }

    @Override
    public Map<Long, StructureSectionClaim> getStructureSectionClaims() {
        return structurenooverlap$sectionClaims;
    }

    @Override
    public Map<Long, Boolean> getOverlapChecks() {
        return structurenooverlap$overlapChecks;
    }

    @Override
    public boolean tryClaimStructure(StructureStart start, Identifier structureId, ServerWorld world) {
        if (!Config.preventStructureOverlap) {
            return true;
        }

        structurenooverlap$ensureClaimsLoaded(world);

        if (Config.isWhitelisted(structureId)) {
            LOGGER.debug("Structure {} is whitelisted, allowing generation", structureId);
            return true;
        }

        ChunkPos chunkPos = start.getPos();
        String cancellationKey = structureId + "|" + chunkPos.toLong();

        // 已经完成判定的结构不再访问定位数据或重新计算包围盒，避免同一结构跨区块放置时重复开销。
        if (structurenooverlap$acceptedStructureStarts.contains(cancellationKey)) {
            return true;
        }

        // 已经取消的结构不再重复执行冲突检测，并保持原有的取消结果。
        if (structurenooverlap$cancelledStructureStarts.contains(cancellationKey)) {
            return false;
        }

        boolean compassTarget = ExplorerCompassSearchContext.allows(world, chunkPos, start.getStructure());
        boolean locatedTarget = compassTarget
            || LocatedStructuresData.get(world).isLocatedTarget(structureId, start, world);

        // 被定位的结构允许越过已有占用，但仍登记空闲区域，保护后续生成的结构。
        if (locatedTarget) {
            long[] locatedSections = structurenooverlap$calculateSections(start);
            if (locatedSections.length == 0) {
                return true;
            }

            long locatedCenterPos = start.getBoundingBox().getCenter().asLong();
            StructureSectionClaim locatedClaim = new StructureSectionClaim(
                System.nanoTime(),
                structureId.toString(),
                locatedCenterPos
            );
            structurenooverlap$claimsLock.lock();
            try {
                if (structurenooverlap$acceptedStructureStarts.contains(cancellationKey)) {
                    return true;
                }
                for (long section : locatedSections) {
                    structurenooverlap$sectionClaims.putIfAbsent(section, locatedClaim);
                }
                structurenooverlap$acceptedStructureStarts.add(cancellationKey);
                AcceptedStructuresData.get(world).recordAccepted(
                    structureId,
                    chunkPos,
                    start.getBoundingBox()
                );
            } finally {
                structurenooverlap$claimsLock.unlock();
            }
            LOGGER.debug("Structure {} at {} is exempt from overlap cancellation by {}",
                structureId,
                chunkPos,
                compassTarget ? "Explorer's Compass" : "locate");
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
            StructureSectionClaim claim = new StructureSectionClaim(
                System.nanoTime(),
                structureId.toString(),
                centerPos
            );

            for (int i = 0; i < sections.length; i++) {
                StructureSectionClaim existing = structurenooverlap$sectionClaims.putIfAbsent(
                    sections[i],
                    claim
                );

                if (existing != null
                    && (!existing.structureId().equals(claim.structureId())
                    || existing.structureCenter() != claim.structureCenter())) {
                    for (int j = 0; j < i; j++) {
                        structurenooverlap$sectionClaims.remove(sections[j], claim);
                    }

                    // 只有首次发现该结构起始位置冲突时才记录和输出取消日志。
                    if (structurenooverlap$cancelledStructureStarts.add(cancellationKey)) {
                        LOGGER.debug(
                            "Structure {} at {} cancelled due to overlap with {}",
                            structureId,
                            chunkPos,
                            existing.structureId()
                        );

                        CancelledStructuresData.get(world).recordCancelled(structureId, chunkPos);
                        if (Config.logCancelledStructures) {
                            LOGGER.info(
                                "Structure generation cancelled: {} at {} (conflicts with {})",
                                structureId,
                                chunkPos,
                                existing.structureId()
                            );
                        }
                    }
                    return false;
                }
            }

            structurenooverlap$acceptedStructureStarts.add(cancellationKey);
            AcceptedStructuresData.get(world).recordAccepted(
                structureId,
                chunkPos,
                start.getBoundingBox()
            );
            return true;
        } finally {
            structurenooverlap$claimsLock.unlock();
        }
    }

    @Unique
    private void structurenooverlap$ensureClaimsLoaded(ServerWorld world) {
        if (structurenooverlap$claimsLoaded && structurenooverlap$claimsLevel == world) {
            return;
        }

        structurenooverlap$claimsLock.lock();
        try {
            if (structurenooverlap$claimsLoaded && structurenooverlap$claimsLevel == world) {
                return;
            }

            structurenooverlap$sectionClaims.clear();
            structurenooverlap$acceptedStructureStarts.clear();
            structurenooverlap$cancelledStructureStarts.clear();

            List<AcceptedStructuresData.AcceptedStructure> acceptedStructures =
                AcceptedStructuresData.get(world).getAcceptedStructures();
            for (AcceptedStructuresData.AcceptedStructure accepted : acceptedStructures) {
                StructureSectionClaim claim = new StructureSectionClaim(
                    System.nanoTime(),
                    accepted.structureId().toString(),
                    accepted.center()
                );
                long[] sections = structurenooverlap$calculateSections(
                    accepted.minX(), accepted.minY(), accepted.minZ(),
                    accepted.maxX(), accepted.maxY(), accepted.maxZ()
                );
                for (long section : sections) {
                    structurenooverlap$sectionClaims.putIfAbsent(section, claim);
                }
                structurenooverlap$acceptedStructureStarts.add(
                    accepted.structureId() + "|" + accepted.chunkPos().toLong()
                );
            }
            structurenooverlap$claimsLevel = world;
            structurenooverlap$claimsLoaded = true;
            LOGGER.debug("Restored {} accepted structure records", acceptedStructures.size());
        } finally {
            structurenooverlap$claimsLock.unlock();
        }
    }

    @Unique
    private long[] structurenooverlap$calculateSections(StructureStart start) {
        if (start.getChildren().isEmpty()) {
            return new long[0];
        }

        BlockBox boundingBox = start.getBoundingBox();
        int minX = boundingBox.getMinX() >> 4;
        int minY = boundingBox.getMinY() >> 4;
        int minZ = boundingBox.getMinZ() >> 4;
        int maxX = boundingBox.getMaxX() >> 4;
        int maxY = boundingBox.getMaxY() >> 4;
        int maxZ = boundingBox.getMaxZ() >> 4;

        return structurenooverlap$calculateSections(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Unique
    private long[] structurenooverlap$calculateSections(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
    ) {

        int count = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        long[] sections = new long[count];
        int index = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    sections[index++] = (long) x & 4194303L
                        | ((long) y & 1048575L) << 42
                        | (long) z << 20;
                }
            }
        }
        return sections;
    }
}
