package org.hp.structurenooverlap;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue PREVENT_STRUCTURE_OVERLAP = BUILDER
        .comment("是否启用结构重叠检测，防止结构生成时相互重叠 / Whether to prevent structures from overlapping during generation")
        .define("preventStructureOverlap", true);

    private static final ModConfigSpec.IntValue MAX_CANCELLED_RECORDS = BUILDER
        .comment("每个结构最多记录多少个取消位置 / Maximum cancelled positions recorded per structure")
        .defineInRange("maxCancelledRecords", 1000, 100, 10000);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_WHITELIST = BUILDER
        .comment("结构 ID 白名单，白名单中的结构永远不会被取消生成 / Structure ID whitelist; listed structures are never cancelled",
                 "格式：完整结构 ID，如 minecraft:village_plains / Format: full structure ID, such as minecraft:village_plains")
        .defineListAllowEmpty("structureWhitelist", List.of(), Config::validateStructureId);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> NAMESPACE_WHITELIST = BUILDER
        .comment("模组命名空间白名单，该命名空间下的所有结构永远不会被取消生成 / Namespace whitelist; all structures in listed namespaces are never cancelled",
                 "格式：命名空间，如 minecraft / Format: namespace, such as minecraft")
        .defineListAllowEmpty("namespaceWhitelist", List.of(), Config::validateNamespace);

    private static final ModConfigSpec.BooleanValue LOG_CANCELLED_STRUCTURES = BUILDER
        .comment("是否在结构被取消生成时输出日志 / Whether to log structures cancelled during generation")
        .define("logCancelledStructures", true);

    static final ModConfigSpec SPEC = BUILDER.build();

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

    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        preventStructureOverlap = PREVENT_STRUCTURE_OVERLAP.get();
        maxCancelledRecords = MAX_CANCELLED_RECORDS.get();
        structureWhitelist = List.copyOf(STRUCTURE_WHITELIST.get());
        namespaceWhitelist = List.copyOf(NAMESPACE_WHITELIST.get());
        logCancelledStructures = LOG_CANCELLED_STRUCTURES.get();
    }
}
