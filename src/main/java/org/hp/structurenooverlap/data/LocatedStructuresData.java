package org.hp.structurenooverlap.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.structure.Structure;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class LocatedStructuresData extends PersistentState {
    private static final String STATE_ID = "located_structures";

    public static final PersistentState.Type<LocatedStructuresData> TYPE = new PersistentState.Type<>(
        LocatedStructuresData::new,
        LocatedStructuresData::load,
        null
    );

    private final Map<Identifier, Set<Long>> locatedPositions = new HashMap<>();

    public synchronized void recordLocated(Identifier structureId, BlockPos locatePos) {
        Set<Long> positions = locatedPositions.computeIfAbsent(
            structureId,
            key -> new LinkedHashSet<>()
        );
        if (positions.add(locatePos.asLong())) {
            markDirty();
        }
    }

    public synchronized boolean isLocatedTarget(Identifier structureId, StructureStart start, ServerWorld world) {
        Set<Long> positions = locatedPositions.get(structureId);
        if (positions == null || positions.isEmpty()) {
            return false;
        }

        DynamicRegistryManager registryManager = world.getRegistryManager();
        Registry<Structure> structureRegistry = registryManager.get(RegistryKeys.STRUCTURE);
        RegistryEntry.Reference<Structure> structureEntry = structureRegistry.getEntry(structureId).orElse(null);
        if (structureEntry == null) {
            return false;
        }

        StructurePlacementCalculator placementCalculator = world.getChunkManager()
            .getStructurePlacementCalculator();
        ChunkPos startPos = start.getPos();
        for (StructurePlacement placement : placementCalculator.getPlacements(structureEntry)) {
            if (positions.contains(placement.getLocatePos(startPos).asLong())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Map.Entry<Identifier, Set<Long>> entry : locatedPositions.entrySet()) {
            NbtCompound structureNbt = new NbtCompound();
            structureNbt.putString("structure", entry.getKey().toString());
            structureNbt.putLongArray(
                "positions",
                entry.getValue().stream().mapToLong(Long::longValue).toArray()
            );
            list.add(structureNbt);
        }
        nbt.put("located", list);
        return nbt;
    }

    private static LocatedStructuresData load(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        LocatedStructuresData data = new LocatedStructuresData();
        NbtList list = nbt.getList("located", 10);

        for (int i = 0; i < list.size(); i++) {
            NbtCompound structureNbt = list.getCompound(i);
            Identifier structureId = Identifier.tryParse(structureNbt.getString("structure"));
            if (structureId == null) {
                continue;
            }

            Set<Long> positions = Arrays.stream(structureNbt.getLongArray("positions"))
                .boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));
            data.locatedPositions.put(structureId, positions);
        }

        return data;
    }

    public static LocatedStructuresData get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, STATE_ID);
    }
}
