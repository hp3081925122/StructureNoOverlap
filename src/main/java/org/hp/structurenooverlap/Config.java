package org.hp.structurenooverlap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("structurenooverlap.json");

    public static boolean preventStructureOverlap = true;
    public static int maxCancelledRecords = 1000;
    public static List<String> structureWhitelist = List.of();
    public static List<String> namespaceWhitelist = List.of();
    public static boolean logCancelledStructures = true;

    private Config() {
    }

    public static boolean isWhitelisted(Identifier structureId) {
        return namespaceWhitelist.contains(structureId.getNamespace())
            || structureWhitelist.contains(structureId.toString());
    }

    public static void load() {
        if (Files.notExists(CONFIG_PATH)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                LOGGER.warn("Ignoring invalid StructureNoOverlap config because the root is not an object");
                save();
                return;
            }

            JsonObject config = root.getAsJsonObject();
            preventStructureOverlap = readBoolean(config, "preventStructureOverlap", true);
            maxCancelledRecords = readInteger(config, "maxCancelledRecords", 1000, 100, 10000);
            structureWhitelist = readStructureWhitelist(config);
            namespaceWhitelist = readNamespaceWhitelist(config);
            logCancelledStructures = readBoolean(config, "logCancelledStructures", true);
            LOGGER.info("Loaded StructureNoOverlap config from {}", CONFIG_PATH);
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to load StructureNoOverlap config", exception);
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject config = new JsonObject();
            config.addProperty("preventStructureOverlap", preventStructureOverlap);
            config.addProperty("maxCancelledRecords", maxCancelledRecords);
            config.add("structureWhitelist", toJsonArray(structureWhitelist));
            config.add("namespaceWhitelist", toJsonArray(namespaceWhitelist));
            config.addProperty("logCancelledStructures", logCancelledStructures);
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException exception) {
            LOGGER.error("Failed to save StructureNoOverlap config", exception);
        }
    }

    private static boolean readBoolean(JsonObject config, String key, boolean defaultValue) {
        JsonElement value = config.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
            ? value.getAsBoolean()
            : defaultValue;
    }

    private static int readInteger(
        JsonObject config,
        String key,
        int defaultValue,
        int minimum,
        int maximum
    ) {
        try {
            JsonElement value = config.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                return defaultValue;
            }
            return Math.max(minimum, Math.min(maximum, value.getAsInt()));
        } catch (RuntimeException exception) {
            return defaultValue;
        }
    }

    private static List<String> readStructureWhitelist(JsonObject config) {
        return readStringList(config, "structureWhitelist").stream()
            .filter(Config::validateStructureId)
            .toList();
    }

    private static List<String> readNamespaceWhitelist(JsonObject config) {
        return readStringList(config, "namespaceWhitelist").stream()
            .filter(Config::validateNamespace)
            .toList();
    }

    private static List<String> readStringList(JsonObject config, String key) {
        JsonElement value = config.get(key);
        if (value == null || !value.isJsonArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                values.add(element.getAsString());
            }
        }
        return List.copyOf(values);
    }

    private static JsonArray toJsonArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static boolean validateStructureId(String id) {
        return id.contains(":") && Identifier.tryParse(id) != null;
    }

    private static boolean validateNamespace(String namespace) {
        return !namespace.isEmpty() && namespace.matches("[a-z0-9_.-]+");
    }
}
