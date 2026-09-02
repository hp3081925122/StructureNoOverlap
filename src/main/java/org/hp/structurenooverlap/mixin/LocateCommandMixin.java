package org.hp.structurenooverlap.mixin;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.hp.structurenooverlap.data.LocatedStructuresData;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Duration;

@Mixin(LocateCommand.class)
public class LocateCommandMixin {

    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    // 在 locate 向执行者发送结果前，保存实际返回的结构和坐标。
    @Inject(
        method = "showLocateResult(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/commands/arguments/ResourceOrTagKeyArgument$Result;Lnet/minecraft/core/BlockPos;Lcom/mojang/datafixers/util/Pair;Ljava/lang/String;ZLjava/time/Duration;)I",
        at = @At("HEAD")
    )
    private static void recordLocatedStructure(
        CommandSourceStack source,
        ResourceOrTagKeyArgument.Result<?> requestedStructure,
        BlockPos origin,
        Pair<BlockPos, ? extends Holder<?>> result,
        String messageKey,
        boolean includeY,
        Duration elapsed,
        CallbackInfoReturnable<Integer> cir
    ) {
        Holder<?> holder = result.getSecond();
        if (!(holder.value() instanceof Structure structure)) {
            return;
        }

        Registry<Structure> registry = source.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceLocation structureId = registry.getKey(structure);
        if (structureId == null) {
            return;
        }

        BlockPos locatePos = result.getFirst();
        LocatedStructuresData.get(source.getLevel()).recordLocated(structureId, locatePos);
        LOGGER.debug("Recorded located structure {} at {}", structureId, locatePos);
    }
}
