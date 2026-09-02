package org.hp.structurenooverlap.api;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.hp.structurenooverlap.world.StructureSectionClaim;

import java.util.List;
import java.util.Map;

public interface StructureOverlapChecker {
    Map<Long, StructureSectionClaim> getStructureSectionClaims();

    Map<Long, Boolean> getOverlapChecks();

    List<StructurePlacement> getStructurePlacements(Holder<Structure> structure, RandomState randomState);

    boolean tryClaimStructure(StructureStart start, ResourceLocation structureId, ServerLevel level);
}
