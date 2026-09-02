package org.hp.structurenooverlap.data;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class CancelledStructuresData extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<ResourceLocation, Set<ChunkPos>> cancelledPositions = new HashMap<>();

    public CancelledStructuresData() {
    }

    // 世界生成由多个线程并行执行，串行保护取消位置集合，确保相同位置只被记录一次。
    public synchronized void recordCancelled(ResourceLocation structureId, ChunkPos pos) {
        Set<ChunkPos> positions = cancelledPositions.computeIfAbsent(structureId, k -> new LinkedHashSet<>());

        // 相同结构位置已经记录过时不再重复标记存档或输出调试日志。
        if (!positions.add(pos)) {
            return;
        }

        int maxEntries = org.hp.structurenooverlap.Config.maxCancelledRecords;
        if (positions.size() > maxEntries) {
            Iterator<ChunkPos> iter = positions.iterator();
            if (iter.hasNext()) {
                iter.next();
                iter.remove();
            }
        }

        setDirty();

        LOGGER.debug("Recorded cancelled structure {} at {}", structureId, pos);
    }

    public boolean isCancelled(ResourceLocation structureId, ChunkPos pos) {
        Set<ChunkPos> positions = cancelledPositions.get(structureId);
        return positions != null && positions.contains(pos);
    }

    public int getCancelledCount(ResourceLocation structureId) {
        Set<ChunkPos> positions = cancelledPositions.get(structureId);
        return positions != null ? positions.size() : 0;
    }

    public void cleanup(ServerLevel level) {
        Vec3 spawnPos = Vec3.atCenterOf(level.getSharedSpawnPos());
        double maxDistance = 10000.0;

        for (Set<ChunkPos> positions : cancelledPositions.values()) {
            positions.removeIf(pos -> {
                double dist = Math.sqrt(pos.x * pos.x + pos.z * pos.z) * 16;
                return dist > maxDistance;
            });
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceLocation, Set<ChunkPos>> entry : cancelledPositions.entrySet()) {
            CompoundTag structureTag = new CompoundTag();
            structureTag.putString("structure", entry.getKey().toString());

            long[] positions = entry.getValue().stream()
                .mapToLong(ChunkPos::toLong)
                .toArray();
            structureTag.putLongArray("positions", positions);

            list.add(structureTag);
        }
        tag.put("cancelled", list);
        return tag;
    }

    public static CancelledStructuresData load(CompoundTag tag, HolderLookup.Provider registries) {
        CancelledStructuresData data = new CancelledStructuresData();
        ListTag list = tag.getList("cancelled", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag structureTag = list.getCompound(i);
            ResourceLocation structureId = ResourceLocation.tryParse(structureTag.getString("structure"));
            if (structureId == null) continue;
            long[] positions = structureTag.getLongArray("positions");

            Set<ChunkPos> posSet = Arrays.stream(positions)
                .mapToObj(ChunkPos::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));

            data.cancelledPositions.put(structureId, posSet);
        }

        LOGGER.info("Loaded {} cancelled structure records", data.cancelledPositions.size());
        return data;
    }

    public static CancelledStructuresData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(CancelledStructuresData::new, CancelledStructuresData::load),
            "cancelled_structures"
        );
    }
}
