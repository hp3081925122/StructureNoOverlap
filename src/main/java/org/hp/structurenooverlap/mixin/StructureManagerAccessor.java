package org.hp.structurenooverlap.mixin;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = StructureManager.class, remap = false)
public interface StructureManagerAccessor {
    @Accessor(value = "level", remap = false)
    LevelAccessor structurenooverlap$getLevel();
}
