package org.hp.structurenooverlap.mixin;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.command.argument.RegistryPredicateArgumentType;
import net.minecraft.server.command.LocateCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.world.gen.structure.Structure;
import org.hp.structurenooverlap.data.LocatedStructuresData;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocateCommand.class)
public class LocateCommandMixin {

    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    // 在 locate 向执行者发送结果前，保存实际返回的结构和坐标。
    @Inject(method = "sendCoordinates", at = @At("HEAD"))
    private static void recordLocatedStructure(
        ServerCommandSource source,
        RegistryPredicateArgumentType.RegistryPredicate<?> requestedStructure,
        BlockPos origin,
        Pair<BlockPos, ? extends RegistryEntry<?>> result,
        String successMessage,
        boolean includeY,
        CallbackInfoReturnable<Integer> cir
    ) {
        RegistryEntry<?> entry = result.getSecond();
        if (!(entry.value() instanceof Structure structure)) {
            return;
        }

        Registry<Structure> registry = source.getWorld().getRegistryManager().get(Registry.STRUCTURE_KEY);
        Identifier structureId = registry.getKey(structure).map(key -> key.getValue()).orElse(null);
        if (structureId == null) {
            return;
        }

        BlockPos locatePos = result.getFirst();
        LocatedStructuresData.get(source.getWorld()).recordLocated(structureId, locatePos);
        LOGGER.debug("Recorded located structure {} at {}", structureId, locatePos);
    }
}
