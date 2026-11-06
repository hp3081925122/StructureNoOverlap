package org.hp.structurenooverlap.compat;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ExplorerCompassSearchContext {

    private static final long EXPIRY_NANOS = 30_000_000_000L;
    private static final Map<Object, Search> SEARCHES = new ConcurrentHashMap<>();

    private ExplorerCompassSearchContext() {
    }

    public static void begin(Object worker, ServerWorld world, ChunkPos chunkPos, List<Structure> structures) {
        Set<Structure> targets = Collections.newSetFromMap(new IdentityHashMap<>());
        targets.addAll(structures);
        SEARCHES.put(worker, new Search(world, chunkPos.toLong(), targets, System.nanoTime()));
    }

    public static void end(Object worker) {
        SEARCHES.remove(worker);
    }

    public static boolean allows(ServerWorld world, ChunkPos chunkPos, Structure structure) {
        long now = System.nanoTime();
        List<Object> expired = new ArrayList<>();
        for (Map.Entry<Object, Search> entry : SEARCHES.entrySet()) {
            Search search = entry.getValue();
            if (now - search.createdAt() > EXPIRY_NANOS) {
                expired.add(entry.getKey());
                continue;
            }
            if (search.world() == world && search.chunkPos() == chunkPos.toLong()
                && search.targets().contains(structure)) {
                return true;
            }
        }
        for (Object worker : expired) {
            SEARCHES.remove(worker);
        }
        return false;
    }

    private record Search(ServerWorld world, long chunkPos, Set<Structure> targets, long createdAt) {
    }
}
