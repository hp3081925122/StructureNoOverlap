package org.hp.structurenooverlap.api;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import org.hp.structurenooverlap.world.StructureSectionClaim;

import java.util.Map;

public interface StructureOverlapChecker {
    Map<Long, StructureSectionClaim> getStructureSectionClaims();

    Map<Long, Boolean> getOverlapChecks();

    boolean tryClaimStructure(StructureStart start, Identifier structureId, ServerWorld world);
}
