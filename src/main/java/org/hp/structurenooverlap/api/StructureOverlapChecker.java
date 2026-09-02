package org.hp.structurenooverlap.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.hp.structurenooverlap.world.StructureSectionClaim;

import java.util.Map;

public interface StructureOverlapChecker {
    Map<Long, StructureSectionClaim> getStructureSectionClaims();

    Map<Long, Boolean> getOverlapChecks();

    boolean tryClaimStructure(StructureStart start, Identifier structureId, ServerLevel level);
}
