package org.hp.structurenooverlap;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(Structurenooverlap.MODID)
public class Structurenooverlap {
    public static final String MODID = "structurenooverlap";

    public Structurenooverlap() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
