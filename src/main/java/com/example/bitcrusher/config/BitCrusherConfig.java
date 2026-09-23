package com.example.bitcrusher.config;

import com.example.bitcrusher.Reference;
import net.minecraftforge.common.config.Config;

/**
 * Saved to {@code config/bitcrusher.cfg}. Forge also builds a config screen from it under
 * Mods → Bit Crusher → Config.
 */
@Config(modid = Reference.MOD_ID, category = "")
public class BitCrusherConfig {

    @Config.Comment("How sounds are crushed. Changing any of these restarts the sound engine, like F3+T.")
    public static final Effect effect = new Effect();

    @Config.Comment("Sounds that play clean. Changes apply to the next sound played.")
    public static final Whitelist whitelist = new Whitelist();

    public static class Effect {

        @Config.Comment({
                "Bit depth each sample is reduced to. Lower is crunchier, and 16 leaves the depth alone.",
                "Changing this restarts the sound engine."
        })
        @Config.RangeInt(min = 1, max = 16)
        @Config.SlidingOption
        public int bits = 4;

        @Config.Comment({
                "Each sample is held for this many frames, dividing the effective sample rate. 1 leaves the rate alone.",
                "Changing this restarts the sound engine."
        })
        @Config.RangeInt(min = 1, max = 8)
        @Config.SlidingOption
        public int sampleRateDivisor = 3;

        @Config.Comment({
                "Volume multiplier applied before crushing. Anything pushed past full scale clips.",
                "Changing this restarts the sound engine."
        })
        @Config.RangeDouble(min = 0.0, max = 10.0)
        public float gain = 0.5F;
    }

    public static class Whitelist {

        @Config.Comment({
                "Sound event IDs that play clean, one per line, e.g. minecraft:ui.button.click.",
                "A missing namespace means minecraft:, and * matches anything, e.g. minecraft:music.* or *:ui.*"
        })
        public String[] sounds = new String[0];

        @Config.Comment({
                "Mod IDs whose sounds all play clean, one per line.",
                "Matched against the sound event's namespace, so minecraft covers all of vanilla."
        })
        public String[] mods = new String[0];
    }
}
