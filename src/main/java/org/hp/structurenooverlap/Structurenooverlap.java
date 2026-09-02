package org.hp.structurenooverlap;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public final class Structurenooverlap implements ModInitializer {
    public static final String MODID = "structurenooverlap";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        Config.load();
        LOGGER.info("StructureNoOverlap initialized for Minecraft 1.20.1 Fabric");
    }
}
