package org.hp.structurenooverlap.data;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CancelledStructuresData extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Codec<Set<ChunkPos>> CHUNK_POS_SET_CODEC = Codec.LONG.listOf().xmap(
        values -> values.stream()
            .map(ChunkPos::unpack)
            .collect(Collectors.toCollection(LinkedHashSet::new)),
        positions -> positions.stream()
            .map(ChunkPos::pack)
            .toList()
    );

    private static final Codec<Map<Identifier, Set<ChunkPos>>> CANCELLED_POSITIONS_CODEC =
        Codec.unboundedMap(Identifier.CODEC, CHUNK_POS_SET_CODEC);

    public static final SavedDataType<CancelledStructuresData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("structurenooverlap", "cancelled_structures"),
        CancelledStructuresData::new,
        RecordCodecBuilder.create(instance -> instance.group(
            CANCELLED_POSITIONS_CODEC.fieldOf("cancelled").forGetter(data -> data.cancelledPositions)
        ).apply(instance, CancelledStructuresData::new))
    );

    private final Map<Identifier, Set<ChunkPos>> cancelledPositions = new HashMap<>();

    public CancelledStructuresData() {
    }

    private CancelledStructuresData(Map<Identifier, Set<ChunkPos>> loadedPositions) {
        loadedPositions.forEach((structureId, positions) ->
            cancelledPositions.put(structureId, new LinkedHashSet<>(positions))
        );
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

        setDirty();

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

    public void cleanup(ServerLevel level) {
        double maxDistance = 10000.0;

        for (Set<ChunkPos> positions : cancelledPositions.values()) {
            positions.removeIf(pos -> {
                double dist = Math.sqrt(pos.x() * pos.x() + pos.z() * pos.z()) * 16;
                return dist > maxDistance;
            });
        }
        setDirty();
    }

    public static CancelledStructuresData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
