package org.hp.structurenooverlap.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.saveddata.SavedData;
import org.hp.structurenooverlap.api.StructureOverlapChecker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LocatedStructuresData extends SavedData {

    private final Map<ResourceLocation, Set<Long>> locatedPositions = new HashMap<>();

    // 记录 locate 返回的实际结构位置，位置以结构的 locate 坐标保存而不是简单的区块坐标。
    public void recordLocated(ResourceLocation structureId, BlockPos locatePos) {
        Set<Long> positions = locatedPositions.computeIfAbsent(structureId, key -> new LinkedHashSet<>());
        if (positions.add(locatePos.asLong())) {
            setDirty();
        }
    }

    // 根据结构的实际生成区块和所有结构放置规则，判断它是否就是 locate 返回的目标。
    public boolean isLocatedTarget(ResourceLocation structureId, StructureStart start, ServerLevel level) {
        Set<Long> positions = locatedPositions.get(structureId);
        if (positions == null || positions.isEmpty()) {
            return false;
        }

        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);
        Holder<Structure> holder = registry.getHolder(ResourceKey.create(Registry.STRUCTURE_REGISTRY, structureId)).orElse(null);
        if (holder == null) {
            return false;
        }

        if (!(level.getChunkSource().getGenerator() instanceof StructureOverlapChecker checker)) {
            return false;
        }

        ChunkPos startPos = start.getChunkPos();
        for (StructurePlacement placement : checker.getStructurePlacements(holder, level.getChunkSource().randomState())) {
            if (positions.contains(placement.getLocatePos(startPos).asLong())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceLocation, Set<Long>> entry : locatedPositions.entrySet()) {
            CompoundTag structureTag = new CompoundTag();
            structureTag.putString("structure", entry.getKey().toString());
            structureTag.putLongArray("positions", entry.getValue().stream().mapToLong(Long::longValue).toArray());
            list.add(structureTag);
        }
        tag.put("located", list);
        return tag;
    }

    // 从世界存档恢复已经定位过的结构目标。
    public static LocatedStructuresData load(CompoundTag tag) {
        LocatedStructuresData data = new LocatedStructuresData();
        ListTag list = tag.getList("located", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag structureTag = list.getCompound(i);
            ResourceLocation structureId = ResourceLocation.tryParse(structureTag.getString("structure"));
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
    public static LocatedStructuresData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            LocatedStructuresData::load,
            LocatedStructuresData::new,
            "located_structures"
        );
    }
}
