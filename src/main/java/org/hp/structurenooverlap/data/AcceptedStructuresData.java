package org.hp.structurenooverlap.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AcceptedStructuresData extends PersistentState {

    private final Map<String, AcceptedStructure> acceptedStructures = new LinkedHashMap<>();

    public synchronized void recordAccepted(Identifier structureId, ChunkPos chunkPos, BlockBox boundingBox) {
        String key = structureId + "|" + chunkPos.toLong();
        if (acceptedStructures.containsKey(key)) {
            return;
        }
        acceptedStructures.put(key, new AcceptedStructure(
            structureId,
            chunkPos,
            boundingBox.getCenter().asLong(),
            boundingBox.getMinX() >> 4,
            boundingBox.getMinY() >> 4,
            boundingBox.getMinZ() >> 4,
            boundingBox.getMaxX() >> 4,
            boundingBox.getMaxY() >> 4,
            boundingBox.getMaxZ() >> 4
        ));
        markDirty();
    }

    public synchronized List<AcceptedStructure> getAcceptedStructures() {
        return List.copyOf(acceptedStructures.values());
    }

    @Override
    public synchronized NbtCompound writeNbt(NbtCompound tag) {
        NbtList list = new NbtList();
        for (AcceptedStructure accepted : acceptedStructures.values()) {
            NbtCompound structureTag = new NbtCompound();
            structureTag.putString("structure", accepted.structureId().toString());
            structureTag.putLong("chunk", accepted.chunkPos().toLong());
            structureTag.putLong("center", accepted.center());
            structureTag.putIntArray("bounds", accepted.bounds());
            list.add(structureTag);
        }
        tag.put("accepted", list);
        return tag;
    }

    public static AcceptedStructuresData load(NbtCompound tag) {
        AcceptedStructuresData data = new AcceptedStructuresData();
        NbtList list = tag.getList("accepted", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound structureTag = list.getCompound(i);
            Identifier structureId = Identifier.tryParse(structureTag.getString("structure"));
            int[] bounds = structureTag.getIntArray("bounds");
            if (structureId == null || bounds.length != 6) {
                continue;
            }
            ChunkPos chunkPos = new ChunkPos(structureTag.getLong("chunk"));
            AcceptedStructure accepted = new AcceptedStructure(
                structureId,
                chunkPos,
                structureTag.getLong("center"),
                bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]
            );
            data.acceptedStructures.put(structureId + "|" + chunkPos.toLong(), accepted);
        }
        return data;
    }

    public static AcceptedStructuresData get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
            AcceptedStructuresData::load,
            AcceptedStructuresData::new,
            "accepted_structures"
        );
    }

    public record AcceptedStructure(
        Identifier structureId,
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
