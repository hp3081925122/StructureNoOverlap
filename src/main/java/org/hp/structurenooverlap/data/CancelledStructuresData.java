package org.hp.structurenooverlap.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import org.hp.structurenooverlap.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class CancelledStructuresData extends PersistentState {
    private static final Logger LOGGER = LoggerFactory.getLogger("structurenooverlap");
    private static final String STATE_ID = "cancelled_structures";

    public static final PersistentState.Type<CancelledStructuresData> TYPE = new PersistentState.Type<>(
        CancelledStructuresData::new,
        CancelledStructuresData::load,
        null
    );

    private final Map<Identifier, Set<ChunkPos>> cancelledPositions = new HashMap<>();

    public synchronized void recordCancelled(Identifier structureId, ChunkPos pos) {
        Set<ChunkPos> positions = cancelledPositions.computeIfAbsent(
            structureId,
            key -> new LinkedHashSet<>()
        );

        if (!positions.add(pos)) {
            return;
        }

        int maxEntries = Config.maxCancelledRecords;
        if (positions.size() > maxEntries) {
            Iterator<ChunkPos> iterator = positions.iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }

        markDirty();
        LOGGER.debug("Recorded cancelled structure {} at {}", structureId, pos);
    }

    public synchronized boolean isCancelled(Identifier structureId, ChunkPos pos) {
        Set<ChunkPos> positions = cancelledPositions.get(structureId);
        return positions != null && positions.contains(pos);
    }

    public synchronized int getCancelledCount(Identifier structureId) {
        Set<ChunkPos> positions = cancelledPositions.get(structureId);
        return positions == null ? 0 : positions.size();
    }

    public synchronized void cleanup(ServerWorld world) {
        ChunkPos spawnChunk = new ChunkPos(world.getSpawnPos());
        double maxDistance = 10000.0;
        double maxDistanceSquared = maxDistance * maxDistance;

        for (Set<ChunkPos> positions : cancelledPositions.values()) {
            positions.removeIf(pos -> {
                double dx = (pos.x - spawnChunk.x) * 16.0;
                double dz = (pos.z - spawnChunk.z) * 16.0;
                return dx * dx + dz * dz > maxDistanceSquared;
            });
        }
        markDirty();
    }

    @Override
    public synchronized NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Map.Entry<Identifier, Set<ChunkPos>> entry : cancelledPositions.entrySet()) {
            NbtCompound structureNbt = new NbtCompound();
            structureNbt.putString("structure", entry.getKey().toString());
            structureNbt.putLongArray(
                "positions",
                entry.getValue().stream().mapToLong(ChunkPos::toLong).toArray()
            );
            list.add(structureNbt);
        }
        nbt.put("cancelled", list);
        return nbt;
    }

    private static CancelledStructuresData load(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        CancelledStructuresData data = new CancelledStructuresData();
        NbtList list = nbt.getList("cancelled", 10);

        for (int i = 0; i < list.size(); i++) {
            NbtCompound structureNbt = list.getCompound(i);
            Identifier structureId = Identifier.tryParse(structureNbt.getString("structure"));
            if (structureId == null) {
                continue;
            }

            Set<ChunkPos> positions = Arrays.stream(structureNbt.getLongArray("positions"))
                .mapToObj(ChunkPos::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            data.cancelledPositions.put(structureId, positions);
        }

        LOGGER.info("Loaded {} cancelled structure records", data.cancelledPositions.size());
        return data;
    }

    public static CancelledStructuresData get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, STATE_ID);
    }
}
