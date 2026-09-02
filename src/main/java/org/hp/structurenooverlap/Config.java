package org.hp.structurenooverlap;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern QUOTED_VALUE = Pattern.compile("\\\"([^\\\"]*)\\\"");
    private static final String CONFIG_FILE_NAME = Structurenooverlap.MODID + "-common.toml";
    private static final String DEFAULT_CONFIG = """
        # 是否启用结构重叠检测，防止结构生成时相互重叠
        # Enables structure overlap checks to prevent structures from overlapping during generation
        preventStructureOverlap = true

        # 每个结构最多记录多少个取消位置（用于 locate 命令跳过）
        # Maximum number of cancelled positions recorded for each structure (used by locate handling)
        maxCancelledRecords = 1000

        # 结构 ID 白名单，格式为完整 ID，例如 minecraft:village_plains
        # Structure ID whitelist using full IDs such as minecraft:village_plains
        structureWhitelist = []

        # 模组命名空间白名单，格式为命名空间，例如 minecraft
        # Namespace whitelist using namespace values such as minecraft
        namespaceWhitelist = []

        # 是否在结构被取消生成时输出日志（显示结构 ID 和位置）
        # Logs the structure ID and position when generation is cancelled
        logCancelledStructures = true
        """;

    public static boolean preventStructureOverlap;
    public static int maxCancelledRecords;
    public static List<String> structureWhitelist;
    public static List<String> namespaceWhitelist;
    public static boolean logCancelledStructures;

    private Config() {
    }

    public static synchronized void load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                Files.writeString(configPath, DEFAULT_CONFIG, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }

            Map<String, String> values = readValues(configPath);
            preventStructureOverlap = readBoolean(values, "preventStructureOverlap", true);
            maxCancelledRecords = readInteger(values, "maxCancelledRecords", 1000, 100, 10000);
            structureWhitelist = readList(values, "structureWhitelist", Config::isValidStructureId);
            namespaceWhitelist = readList(values, "namespaceWhitelist", Config::isValidNamespace);
            logCancelledStructures = readBoolean(values, "logCancelledStructures", true);
            LOGGER.info("Loaded StructureNoOverlap config from {}", configPath);
        } catch (IOException exception) {
            useDefaults();
            LOGGER.error("Failed to load StructureNoOverlap config from {}", configPath, exception);
        }
    }

    public static boolean isWhitelisted(ResourceLocation structureId) {
        return namespaceWhitelist.contains(structureId.getNamespace())
            || structureWhitelist.contains(structureId.toString());
    }

    private static Map<String, String> readValues(Path configPath) throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(configPath, StandardCharsets.UTF_8)) {
            String content = line.split("#", 2)[0].trim();
            int separator = content.indexOf('=');
            if (separator > 0) {
                values.put(content.substring(0, separator).trim(), content.substring(separator + 1).trim());
            }
        }
        return values;
    }

    private static boolean readBoolean(Map<String, String> values, String key, boolean defaultValue) {
        String value = values.get(key);
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        if (value != null) {
            LOGGER.warn("Invalid boolean value for config key {}: {}", key, value);
        }
        return defaultValue;
    }

    private static int readInteger(Map<String, String> values, String key, int defaultValue, int minimum, int maximum) {
        String value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed >= minimum && parsed <= maximum) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        LOGGER.warn("Invalid integer value for config key {}: {}", key, value);
        return defaultValue;
    }

    private static List<String> readList(Map<String, String> values, String key, Predicate<String> validator) {
        String value = values.get(key);
        if (value == null) {
            return List.of();
        }
        if (!value.startsWith("[") || !value.endsWith("]")) {
            LOGGER.warn("Invalid list value for config key {}: {}", key, value);
            return List.of();
        }

        String content = value.substring(1, value.length() - 1).trim();
        if (content.isEmpty()) {
            return List.of();
        }

        List<String> entries = new ArrayList<>();
        Matcher matcher = QUOTED_VALUE.matcher(content);
        int consumed = 0;
        while (matcher.find()) {
            if (!content.substring(consumed, matcher.start()).matches("\\s*,?\\s*")) {
                LOGGER.warn("Invalid list value for config key {}: {}", key, value);
                return List.of();
            }
            String entry = matcher.group(1);
            if (!validator.test(entry)) {
                LOGGER.warn("Invalid entry for config key {}: {}", key, entry);
                return List.of();
            }
            entries.add(entry);
            consumed = matcher.end();
        }

        if (entries.isEmpty() || !content.substring(consumed).matches("\\s*")) {
            LOGGER.warn("Invalid list value for config key {}: {}", key, value);
            return List.of();
        }
        return List.copyOf(entries);
    }

    private static boolean isValidStructureId(String id) {
        return id.contains(":") && ResourceLocation.tryParse(id) != null;
    }

    private static boolean isValidNamespace(String namespace) {
        return !namespace.isEmpty() && !namespace.contains(":") && namespace.matches("[a-z0-9_.-]+");
    }

    private static void useDefaults() {
        preventStructureOverlap = true;
        maxCancelledRecords = 1000;
        structureWhitelist = List.of();
        namespaceWhitelist = List.of();
        logCancelledStructures = true;
    }
}
