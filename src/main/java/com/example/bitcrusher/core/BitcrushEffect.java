package com.example.bitcrusher.core;

import javax.sound.sampled.AudioFormat;

/**
 * The bit crusher itself, ported from the original mod's {@code BitcrushEffect}. It only uses JDK
 * classes so it can be unit tested without Minecraft.
 */
public final class BitcrushEffect {

    /**
     * The rate {@code sampleRateDivisor} divides. Holding to a fixed rate, not a fraction of each
     * file's own, crushes a 192 kHz file as much as a 44.1 kHz one.
     */
    static final int REFERENCE_RATE = 44100;

    private BitcrushEffect() {}

    /**
     * An immutable snapshot of the effect settings. Forge doesn't enforce the config ranges when the
     * file loads, so out-of-range values are pulled back in here instead of throwing on the sound
     * thread.
     */
    public record Settings(int bits, int sampleRateDivisor, float gain) {

        public static final Settings DEFAULT = new Settings(4, 3, 0.5F);

        public Settings {
            bits = Math.clamp(bits, 1, 16);
            sampleRateDivisor = Math.max(1, sampleRateDivisor);
            if (Float.isNaN(gain)) {
                gain = 0.5F;
            }
        }
    }

    /**
     * Crushes 16-bit signed PCM in place. Every channel of a frame is multiplied by the gain, clipped
     * to 16 bits and has its low {@code 16 - bits} bits cleared, and the result is held for
     * {@code sampleRateDivisor} frames at 44.1 kHz, however many frames that is at the sound's own
     * rate. A divisor of 1 holds nothing, whatever the rate. For 44.1 kHz mono this matches the
     * original sample for sample; holding whole frames keeps stereo channels from bleeding into each
     * other.
     *
     * <p>Other formats, and any trailing partial frame, are left untouched.
     */
    public static void process(byte[] data, AudioFormat format, Settings settings) {
        if (data == null || !isSupported(format)) {
            return;
        }
        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        int frames = data.length / frameSize;
        boolean bigEndian = format.isBigEndian();
        int mask = ~((1 << (16 - settings.bits())) - 1);
        int divisor = settings.sampleRateDivisor();
        float gain = settings.gain();
        short[] held = new short[channels];

        // A new sample is taken whenever the frame reaches the next slot of the reduced rate. At 44.1 kHz
        // the slot is frame / divisor.
        int sourceRate = Math.round(format.getSampleRate());
        if (sourceRate <= 0) {
            // Unspecified (-1) or NaN
            sourceRate = REFERENCE_RATE;
        }
        long period = divisor == 1 ? REFERENCE_RATE : (long) sourceRate * divisor;
        long lastSlot = -1;

        for (int frame = 0; frame < frames; frame++) {
            long slot = frame * (long) REFERENCE_RATE / period;
            boolean sample = slot != lastSlot;
            lastSlot = slot;
            int offset = frame * frameSize;
            for (int channel = 0; channel < channels; channel++, offset += 2) {
                if (sample) {
                    int gained = (int) ((float) read(data, offset, bigEndian) * gain);
                    gained = Math.max(-32768, Math.min(32767, gained));
                    held[channel] = (short) (gained & mask);
                }
                write(data, offset, held[channel], bigEndian);
            }
        }
    }

    /** Whether {@link #process} changes audio in this format: 16-bit signed PCM, any channel count. */
    public static boolean isSupported(AudioFormat format) {
        return format != null
                && AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                && format.getSampleSizeInBits() == 16
                && format.getChannels() > 0
                && format.getFrameSize() == 2 * format.getChannels();
    }

    private static short read(byte[] data, int offset, boolean bigEndian) {
        int first = data[offset] & 0xFF;
        int second = data[offset + 1] & 0xFF;
        return (short) (bigEndian ? first << 8 | second : second << 8 | first);
    }

    private static void write(byte[] data, int offset, short value, boolean bigEndian) {
        byte high = (byte) (value >> 8);
        byte low = (byte) value;
        data[offset] = bigEndian ? high : low;
        data[offset + 1] = bigEndian ? low : high;
    }
}
