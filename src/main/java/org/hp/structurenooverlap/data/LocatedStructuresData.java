package org.hp.structurenooverlap.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.structure.Structure;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LocatedStructuresData extends PersistentState {

    private final Map<Identifier, Set<Long>> locatedPositions = new HashMap<>();

    // 记录 locate 返回的实际结构位置，位置以结构的 locate 坐标保存而不是简单的区块坐标。
    public void recordLocated(Identifier structureId, BlockPos locatePos) {
        Set<Long> positions = locatedPositions.computeIfAbsent(structureId, key -> new LinkedHashSet<>());
        if (positions.add(locatePos.asLong())) {
            markDirty();
        }
    }

    // 根据结构的实际生成区块和所有结构放置规则，判断它是否就是 locate 返回的目标。
    public boolean isLocatedTarget(Identifier structureId, StructureStart start, List<StructurePlacement> placements) {
        Set<Long> positions = locatedPositions.get(structureId);
        if (positions == null || positions.isEmpty()) {
            return false;
        }

        ChunkPos startPos = start.getPos();
        for (StructurePlacement placement : placements) {
            if (positions.contains(placement.getLocatePos(startPos).asLong())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        NbtList list = new NbtList();
        for (Map.Entry<Identifier, Set<Long>> entry : locatedPositions.entrySet()) {
            NbtCompound structureTag = new NbtCompound();
            structureTag.putString("structure", entry.getKey().toString());
            structureTag.putLongArray("positions", entry.getValue().stream().mapToLong(Long::longValue).toArray());
            list.add(structureTag);
        }
        tag.put("located", list);
        return tag;
    }

    // 从世界存档恢复已经定位过的结构目标。
    public static LocatedStructuresData load(NbtCompound tag) {
        LocatedStructuresData data = new LocatedStructuresData();
        NbtList list = tag.getList("located", NbtElement.COMPOUND_TYPE);

        for (int i = 0; i < list.size(); i++) {
            NbtCompound structureTag = list.getCompound(i);
            Identifier structureId = Identifier.tryParse(structureTag.getString("structure"));
            if (structureId == null) {
                continue;
            }

            Set<Long> positions = Arrays.stream(structureTag.getLongArray("positions"))
                .boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));
            data.locatedPositions.put(structureId, positions);
        }

        return data;
    }

    // 获取当前维度的定位目标数据，使目标在区块卸载后仍然有效。
    public static LocatedStructuresData get(ServerWorld level) {
        return level.getPersistentStateManager().getOrCreate(
            LocatedStructuresData::load,
            LocatedStructuresData::new,
            "located_structures"
        );
    }
}
