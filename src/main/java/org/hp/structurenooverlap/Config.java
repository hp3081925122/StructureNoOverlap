package org.hp.structurenooverlap;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

@Mod.EventBusSubscriber(modid = Structurenooverlap.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue PREVENT_STRUCTURE_OVERLAP = BUILDER
        .comment("是否启用结构重叠检测，防止结构生成时相互重叠")
        .define("preventStructureOverlap", true);

    private static final ForgeConfigSpec.IntValue MAX_CANCELLED_RECORDS = BUILDER
        .comment("每个结构最多记录多少个取消位置（用于 locate 命令跳过）")
        .defineInRange("maxCancelledRecords", 1000, 100, 10000);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_WHITELIST = BUILDER
        .comment("结构ID白名单，白名单中的结构永远不会被取消生成",
                 "格式：完整结构ID，如 minecraft:village_plains")
        .defineListAllowEmpty(List.of("structureWhitelist"), List::of, Config::validateStructureId);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> NAMESPACE_WHITELIST = BUILDER
        .comment("模组命名空间白名单，该命名空间下的所有结构永远不会被取消生成",
                 "格式：命名空间，如 minecraft")
        .defineListAllowEmpty(List.of("namespaceWhitelist"), List::of, Config::validateNamespace);

    private static final ForgeConfigSpec.BooleanValue LOG_CANCELLED_STRUCTURES = BUILDER
        .comment("是否在结构被取消生成时输出日志（显示结构ID和位置）")
        .define("logCancelledStructures", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean preventStructureOverlap;
    public static int maxCancelledRecords;
    public static List<String> structureWhitelist;
    public static List<String> namespaceWhitelist;
    public static boolean logCancelledStructures;

    public static boolean isWhitelisted(ResourceLocation structureId) {
        String namespace = structureId.getNamespace();
        if (namespaceWhitelist.contains(namespace)) {
            return true;
        }

        String fullId = structureId.toString();
        if (structureWhitelist.contains(fullId)) {
            return true;
        }

        return false;
    }

    private static boolean validateStructureId(final Object obj) {
        if (!(obj instanceof String id)) {
            return false;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null && id.contains(":");
    }

    private static boolean validateNamespace(final Object obj) {
        if (!(obj instanceof String ns)) {
            return false;
        }
        return !ns.isEmpty() && !ns.contains(":") && ns.matches("[a-z0-9_.-]+");
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        preventStructureOverlap = PREVENT_STRUCTURE_OVERLAP.get();
        maxCancelledRecords = MAX_CANCELLED_RECORDS.get();
        structureWhitelist = List.copyOf(STRUCTURE_WHITELIST.get());
        namespaceWhitelist = List.copyOf(NAMESPACE_WHITELIST.get());
        logCancelledStructures = LOG_CANCELLED_STRUCTURES.get();
    }
}
