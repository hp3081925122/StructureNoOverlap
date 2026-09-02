package org.hp.structurenooverlap.data;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class CancelledStructuresData extends PersistentState {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<Identifier, Set<ChunkPos>> cancelledPositions = new HashMap<>();

    public CancelledStructuresData() {
    }

    // 世界生成由多个线程并行执行，串行保护取消位置集合，确保相同位置只被记录一次。
    public synchronized void recordCancelled(Identifier structureId, ChunkPos pos) {
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

        markDirty();

        LOGGER.debug("Recorded cancelled structure {} at {}", structureId, pos);
    }

    public boolean isCancelled(Identifier structureId, ChunkPos pos) {
        Set<ChunkPos> positions = cancelledPositions.get(structureId);
        return positions != null && positions.contains(pos);
    }

    public int getCancelledCount(Identifier structureId) {
        Set<ChunkPos> positions = cancelledPositions.get(structureId);
        return positions != null ? positions.size() : 0;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        NbtList list = new NbtList();
        for (Map.Entry<Identifier, Set<ChunkPos>> entry : cancelledPositions.entrySet()) {
            NbtCompound structureTag = new NbtCompound();
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

    public static CancelledStructuresData load(NbtCompound tag) {
        CancelledStructuresData data = new CancelledStructuresData();
        NbtList list = tag.getList("cancelled", NbtElement.COMPOUND_TYPE);

        for (int i = 0; i < list.size(); i++) {
            NbtCompound structureTag = list.getCompound(i);
            Identifier structureId = Identifier.tryParse(structureTag.getString("structure"));
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

    public static CancelledStructuresData get(ServerWorld level) {
        return level.getPersistentStateManager().getOrCreate(
            CancelledStructuresData::load,
            CancelledStructuresData::new,
            "cancelled_structures"
        );
    }
}
