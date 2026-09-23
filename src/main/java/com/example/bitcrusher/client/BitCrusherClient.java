package com.example.bitcrusher.client;

import com.example.bitcrusher.BitCrusher;
import com.example.bitcrusher.Reference;
import com.example.bitcrusher.config.BitCrusherConfig;
import com.example.bitcrusher.core.BitcrushEffect;
import com.example.bitcrusher.core.SoundWhitelist;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.sound.SoundSetupEvent;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.SoundSystemException;

/**
 * Decides which sounds get crushed and keeps the state the codec reads.
 *
 * <p>Crushed sounds are routed to {@link BitcrushCodec} by appending {@link #CODEC_EXTENSION} to
 * their Paulscode identifier; Paulscode picks a codec from the identifier's extension. The vanilla
 * {@code ogg} codec is left alone, so if routing is ever skipped a sound just plays clean.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class BitCrusherClient {

    static final String CODEC_EXTENSION = "bitcrusher";

    // Read by the codec on Paulscode's threads
    private static volatile BitcrushEffect.Settings settings = BitcrushEffect.Settings.DEFAULT;
    private static volatile SoundWhitelist whitelist = SoundWhitelist.EMPTY;
    private static volatile boolean codecRegistered;
    private static boolean routeFailureLogged;
    private static SoundManager soundManager;

    private BitCrusherClient() {}

    public static BitcrushEffect.Settings settings() {
        return settings;
    }

    /** Rebuilds the settings and whitelist from the config fields and logs any bad entries. */
    public static void reloadConfig() {
        BitCrusherConfig.Effect effect = BitCrusherConfig.effect;
        settings = new BitcrushEffect.Settings(effect.bits, effect.sampleRateDivisor, effect.gain);

        List<String> problems = new ArrayList<>();
        SoundWhitelist compiled = SoundWhitelist.compile(BitCrusherConfig.whitelist.sounds, BitCrusherConfig.whitelist.mods, problems);
        whitelist = compiled;
        problems.forEach(BitCrusher.LOGGER::warn);

        Set<String> loaded = Loader.instance().getActiveModList().stream()
                .map(mod -> mod.getModId().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (String mod : compiled.mods()) {
            if (!loaded.contains(mod)) {
                BitCrusher.LOGGER.warn("Whitelisted mod '{}' isn't loaded, but sounds in its namespace will still play clean", mod);
            }
        }
    }

    /**
     * Returns the Paulscode identifier {@code sound} should play under. Crushed sounds get the codec's
     * extension, which also keeps their decoded buffers apart from clean ones in Paulscode's
     * per-identifier cache.
     */
    public static String route(String identifier, ISound sound) {
        try {
            if (!codecRegistered) {
                return identifier;
            }
            ResourceLocation event = sound.getSoundLocation();
            if (whitelist.matches(event.getNamespace(), event.getPath())) {
                return identifier;
            }
            return identifier + '.' + CODEC_EXTENSION;
        } catch (RuntimeException e) {
            if (!routeFailureLogged) {
                routeFailureLogged = true;
                BitCrusher.LOGGER.warn("Couldn't route sound {}, so it plays clean. Later failures aren't logged.", identifier, e);
            }
            return identifier;
        }
    }

    // Posted from SoundManager's constructor, right after vanilla registers its ogg codec
    @SubscribeEvent
    public static void onSoundSetup(SoundSetupEvent event) {
        soundManager = event.getManager();
        try {
            SoundSystemConfig.setCodec(CODEC_EXTENSION, BitcrushCodec.class);
            codecRegistered = true;
            BitCrusher.LOGGER.info("Registered the bitcrush codec for .{} identifiers", CODEC_EXTENSION);
        } catch (SoundSystemException | RuntimeException e) {
            BitCrusher.LOGGER.error("Couldn't register the bitcrush codec, so every sound will play clean", e);
        }
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!Reference.MOD_ID.equals(event.getModID())) {
            return;
        }
        BitcrushEffect.Settings previous = settings;
        ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
        reloadConfig();
        // Paulscode keeps every decoded sound until the sound system shuts down, so sounds crushed with
        // the old settings would keep playing. A whitelist change needs no restart: it only changes which
        // identifier a sound plays under, and each identifier has its own cache entry.
        if (!settings.equals(previous) && soundManager != null) {
            BitCrusher.LOGGER.info("Effect settings changed, restarting the sound engine");
            soundManager.reloadSoundSystem();
        }
    }
}
