package org.hp.structurenooverlap;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

@EventBusSubscriber(modid = Structurenooverlap.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue PREVENT_STRUCTURE_OVERLAP = BUILDER
        .comment("是否启用结构重叠检测，防止结构生成时相互重叠")
        .define("preventStructureOverlap", true);

    private static final ModConfigSpec.IntValue MAX_CANCELLED_RECORDS = BUILDER
        .comment("每个结构最多记录多少个取消位置（用于 locate 命令跳过）")
        .defineInRange("maxCancelledRecords", 1000, 100, 10000);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_WHITELIST = BUILDER
        .comment("结构ID白名单，白名单中的结构永远不会被取消生成",
                 "格式：完整结构ID，如 minecraft:village_plains")
        .defineListAllowEmpty("structureWhitelist", List.of(), () -> "", Config::validateStructureId);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> NAMESPACE_WHITELIST = BUILDER
        .comment("模组命名空间白名单，该命名空间下的所有结构永远不会被取消生成",
                 "格式：命名空间，如 minecraft")
        .defineListAllowEmpty("namespaceWhitelist", List.of(), () -> "", Config::validateNamespace);

    private static final ModConfigSpec.BooleanValue LOG_CANCELLED_STRUCTURES = BUILDER
        .comment("是否在结构被取消生成时输出日志（显示结构ID和位置）")
        .define("logCancelledStructures", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean preventStructureOverlap;
    public static int maxCancelledRecords;
    public static List<String> structureWhitelist;
    public static List<String> namespaceWhitelist;
    public static boolean logCancelledStructures;

    public static boolean isWhitelisted(Identifier structureId) {
        if (namespaceWhitelist.contains(structureId.getNamespace())) {
            return true;
        }

        return structureWhitelist.contains(structureId.toString());
    }

    private static boolean validateStructureId(final Object obj) {
        if (!(obj instanceof String id)) {
            return false;
        }

        Identifier identifier = Identifier.tryParse(id);
        return identifier != null && id.contains(":");
    }

    private static boolean validateNamespace(final Object obj) {
        if (!(obj instanceof String namespace)) {
            return false;
        }

        return !namespace.isEmpty()
            && !namespace.contains(":")
            && namespace.matches("[a-z0-9_.-]+");
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent.Loading event) {
        load(event.getConfig());
    }

    @SubscribeEvent
    static void onReload(final ModConfigEvent.Reloading event) {
        load(event.getConfig());
    }

    private static void load(final ModConfig config) {
        if (config.getSpec() != SPEC) return;

        preventStructureOverlap = PREVENT_STRUCTURE_OVERLAP.getAsBoolean();
        maxCancelledRecords = MAX_CANCELLED_RECORDS.getAsInt();
        structureWhitelist = List.copyOf(STRUCTURE_WHITELIST.get());
        namespaceWhitelist = List.copyOf(NAMESPACE_WHITELIST.get());
        logCancelledStructures = LOG_CANCELLED_STRUCTURES.getAsBoolean();
    }
}
