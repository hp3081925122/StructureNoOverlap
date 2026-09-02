package org.hp.structurenooverlap.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LocatedStructuresData extends SavedData {

    private static final Codec<Set<Long>> LONG_SET_CODEC = Codec.LONG.listOf().xmap(
        values -> values.stream().collect(Collectors.toCollection(LinkedHashSet::new)),
        positions -> positions.stream().toList()
    );

    private static final Codec<Map<Identifier, Set<Long>>> LOCATED_POSITIONS_CODEC =
        Codec.unboundedMap(Identifier.CODEC, LONG_SET_CODEC);

    public static final SavedDataType<LocatedStructuresData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("structurenooverlap", "located_structures"),
        LocatedStructuresData::new,
        RecordCodecBuilder.create(instance -> instance.group(
            LOCATED_POSITIONS_CODEC.fieldOf("located").forGetter(data -> data.locatedPositions)
        ).apply(instance, LocatedStructuresData::new))
    );

    private final Map<Identifier, Set<Long>> locatedPositions = new HashMap<>();

    public LocatedStructuresData() {
    }

    private LocatedStructuresData(Map<Identifier, Set<Long>> loadedPositions) {
        loadedPositions.forEach((structureId, positions) ->
            locatedPositions.put(structureId, new LinkedHashSet<>(positions))
        );
    }

    // 记录 locate 返回的实际结构位置，位置以结构的 locate 坐标保存而不是简单的区块坐标。
    public void recordLocated(Identifier structureId, BlockPos locatePos) {
        Set<Long> positions = locatedPositions.computeIfAbsent(structureId, key -> new LinkedHashSet<>());
        if (positions.add(locatePos.asLong())) {
            setDirty();
        }
    }

    // 根据结构的实际生成区块和所有结构放置规则，判断它是否就是 locate 返回的目标。
    public boolean isLocatedTarget(Identifier structureId, StructureStart start, ServerLevel level) {
        Set<Long> positions = locatedPositions.get(structureId);
        if (positions == null || positions.isEmpty()) {
            return false;
        }

        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Holder.Reference<Structure> holder = registry.get(structureId).orElse(null);
        if (holder == null) {
            return false;
        }

        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        ChunkPos startPos = start.getChunkPos();
        for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
            if (positions.contains(placement.getLocatePos(startPos).asLong())) {
                return true;
            }
        }

        return false;
    }

    // 获取当前维度的定位目标数据，使目标在区块卸载后仍然有效。
    public static LocatedStructuresData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
