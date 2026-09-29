package org.hp.structurenooverlap.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AcceptedStructuresData extends SavedData {

    private final Map<String, AcceptedStructure> acceptedStructures = new LinkedHashMap<>();

    public synchronized void recordAccepted(
        ResourceLocation structureId,
        ChunkPos chunkPos,
        BoundingBox boundingBox
    ) {
        String key = structureId + "|" + chunkPos.toLong();
        if (acceptedStructures.containsKey(key)) {
            return;
        }

        acceptedStructures.put(key, new AcceptedStructure(
            structureId,
            chunkPos,
            boundingBox.getCenter().asLong(),
            boundingBox.minX() >> 4,
            boundingBox.minY() >> 4,
            boundingBox.minZ() >> 4,
            boundingBox.maxX() >> 4,
            boundingBox.maxY() >> 4,
            boundingBox.maxZ() >> 4
        ));
        setDirty();
    }

    public synchronized List<AcceptedStructure> getAcceptedStructures() {
        return List.copyOf(acceptedStructures.values());
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (AcceptedStructure accepted : acceptedStructures.values()) {
            CompoundTag structureTag = new CompoundTag();
            structureTag.putString("structure", accepted.structureId().toString());
            structureTag.putLong("chunk", accepted.chunkPos().toLong());
            structureTag.putLong("center", accepted.center());
            structureTag.putIntArray("bounds", accepted.bounds());
            list.add(structureTag);
        }
        tag.put("accepted", list);
        return tag;
    }

    public static AcceptedStructuresData load(CompoundTag tag) {
        AcceptedStructuresData data = new AcceptedStructuresData();
        ListTag list = tag.getList("accepted", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag structureTag = list.getCompound(i);
            ResourceLocation structureId = ResourceLocation.tryParse(structureTag.getString("structure"));
            int[] bounds = structureTag.getIntArray("bounds");
            if (structureId == null || bounds.length != 6) {
                continue;
            }

            ChunkPos chunkPos = new ChunkPos(structureTag.getLong("chunk"));
            AcceptedStructure accepted = new AcceptedStructure(
                structureId,
                chunkPos,
                structureTag.getLong("center"),
                bounds[0],
                bounds[1],
                bounds[2],
                bounds[3],
                bounds[4],
                bounds[5]
            );
            data.acceptedStructures.put(
                structureId + "|" + chunkPos.toLong(),
                accepted
            );
        }
        return data;
    }

    public static AcceptedStructuresData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            AcceptedStructuresData::load,
            AcceptedStructuresData::new,
            "accepted_structures"
        );
    }

    public record AcceptedStructure(
        ResourceLocation structureId,
        ChunkPos chunkPos,
        long center,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
    ) {
        public int[] bounds() {
            return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
        }
    }
}
