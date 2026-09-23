package com.example.bitcrusher;

import com.example.bitcrusher.client.BitCrusherClient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.VERSION, clientSideOnly = true, acceptableRemoteVersions = "*")
public class BitCrusher {

    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        BitCrusherClient.reloadConfig();
        LOGGER.info("Started Crunching your audio");
    }
}
