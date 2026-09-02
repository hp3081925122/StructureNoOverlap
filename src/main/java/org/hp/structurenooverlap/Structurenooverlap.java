package org.hp.structurenooverlap;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(Structurenooverlap.MODID)
public class Structurenooverlap {

    public static final String MODID = "structurenooverlap";

    public Structurenooverlap(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(Config::onLoad);
    }
}
