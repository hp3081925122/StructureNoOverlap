package org.hp.structurenooverlap.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.command.argument.RegistryPredicateArgumentType;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.LocateCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.Structure;
import org.hp.structurenooverlap.data.LocatedStructuresData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Duration;

@Mixin(LocateCommand.class)
public class LocateCommandMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("structurenooverlap");

    // 1.21.1 把原先的 showLocateResult 改为 sendCoordinates；这里只拦截结构谓词重载。
    @Inject(
        method = "sendCoordinates(Lnet/minecraft/server/command/ServerCommandSource;Lnet/minecraft/command/argument/RegistryPredicateArgumentType$RegistryPredicate;Lnet/minecraft/util/math/BlockPos;Lcom/mojang/datafixers/util/Pair;Ljava/lang/String;ZLjava/time/Duration;)I",
        at = @At("HEAD")
    )
    private static void recordLocatedStructure(
        ServerCommandSource source,
        RegistryPredicateArgumentType.RegistryPredicate<?> predicate,
        BlockPos currentPos,
        Pair<BlockPos, ? extends RegistryEntry<?>> result,
        String successMessage,
        boolean includeY,
        Duration timeTaken,
        CallbackInfoReturnable<Integer> callbackInfo
    ) {
        RegistryEntry<?> entry = result.getSecond();
        if (!(entry.value() instanceof Structure structure)) {
            return;
        }

        Registry<Structure> registry = source.getRegistryManager().get(RegistryKeys.STRUCTURE);
        Identifier structureId = registry.getId(structure);
        if (structureId == null) {
            return;
        }

        BlockPos locatePos = result.getFirst();
        LocatedStructuresData.get(source.getWorld()).recordLocated(structureId, locatePos);
        LOGGER.debug("Recorded located structure {} at {}", structureId, locatePos);
    }
}
