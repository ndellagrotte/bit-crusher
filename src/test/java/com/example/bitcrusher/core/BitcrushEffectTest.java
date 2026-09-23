package com.example.bitcrusher.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.bitcrusher.core.BitcrushEffect.Settings;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.Random;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;

class BitcrushEffectTest {

    private static final float[] GAINS = {0F, 0.5F, 1F, 2F, 10F};

    @Test
    void monoMatchesTheOriginal() {
        byte[] input = randomPcm(4096);
        for (int bits = 1; bits <= 16; bits++) {
            for (int divisor = 1; divisor <= 8; divisor++) {
                for (float gain : GAINS) {
                    byte[] actual = input.clone();
                    BitcrushEffect.process(actual, pcm16(1, false), new Settings(bits, divisor, gain));
                    assertArrayEquals(original(input, bits, divisor, gain), actual,
                            "bits=" + bits + " divisor=" + divisor + " gain=" + gain);
                }
            }
        }
    }

    @Test
    void stereoCrushesEachChannelLikeMono() {
        byte[] input = randomPcm(4096);
        for (float rate : new float[] {44100F, 192000F}) {
            for (int divisor = 1; divisor <= 8; divisor++) {
                Settings settings = new Settings(5, divisor, 1.5F);
                byte[] stereo = input.clone();
                byte[] left = channel(stereo, 0);
                byte[] right = channel(stereo, 1);
                BitcrushEffect.process(stereo, pcm16(rate, 2, false), settings);
                BitcrushEffect.process(left, pcm16(rate, 1, false), settings);
                BitcrushEffect.process(right, pcm16(rate, 1, false), settings);
                assertArrayEquals(left, channel(stereo, 0), "rate=" + rate + " divisor=" + divisor);
                assertArrayEquals(right, channel(stereo, 1), "rate=" + rate + " divisor=" + divisor);
            }
        }
    }

    @Test
    void holdsEveryRateToTheSameEffectiveRate() {
        // 30 ms at 44100 / 3 Hz is 441 held samples, whatever rate the sound was recorded at
        for (int rate : new int[] {44100, 48000, 32000, 96000, 192000}) {
            byte[] data = ramp(rate * 3 / 100);
            BitcrushEffect.process(data, pcm16(rate, 1, false), new Settings(16, 3, 1F));
            assertEquals(441, runs(data), "rate=" + rate);
        }
    }

    @Test
    void divisorOneLeavesAnyRateAlone() {
        for (float rate : new float[] {8000F, 32000F, 44100F, 48000F, 192000F}) {
            byte[] input = randomPcm(4096);
            byte[] data = input.clone();
            BitcrushEffect.process(data, pcm16(rate, 2, false), new Settings(16, 1, 1F));
            assertArrayEquals(input, data, "rate=" + rate);
        }
    }

    @Test
    void unspecifiedSampleRateActsLike44100() {
        byte[] input = randomPcm(4096);
        for (int divisor = 1; divisor <= 8; divisor++) {
            Settings settings = new Settings(6, divisor, 1F);
            byte[] expected = input.clone();
            byte[] actual = input.clone();
            BitcrushEffect.process(expected, pcm16(1, false), settings);
            BitcrushEffect.process(actual, pcm16(AudioSystem.NOT_SPECIFIED, 1, false), settings);
            assertArrayEquals(expected, actual, "divisor=" + divisor);
        }
    }

    @Test
    void bigEndianMatchesByteSwappedLittleEndian() {
        for (int channels = 1; channels <= 2; channels++) {
            byte[] little = randomPcm(4096);
            byte[] big = swapBytes(little);
            Settings settings = new Settings(6, 3, 2F);
            BitcrushEffect.process(little, pcm16(channels, false), settings);
            BitcrushEffect.process(big, pcm16(channels, true), settings);
            assertArrayEquals(little, swapBytes(big), "channels=" + channels);
        }
    }

    @Test
    void unsupportedFormatsAreLeftUntouched() {
        AudioFormat[] formats = {
                null,
                new AudioFormat(44100F, 8, 1, true, false),
                new AudioFormat(44100F, 16, 1, false, false),
                new AudioFormat(44100F, 24, 1, true, false),
                new AudioFormat(Encoding.PCM_FLOAT, 44100F, 32, 1, 4, 44100F, false),
                new AudioFormat(Encoding.ULAW, 44100F, 8, 1, 1, 44100F, false),
                // 16-bit samples padded to 32-bit frames
                new AudioFormat(Encoding.PCM_SIGNED, 44100F, 16, 1, 4, 44100F, false),
        };
        byte[] input = randomPcm(1024);
        for (AudioFormat format : formats) {
            byte[] data = input.clone();
            BitcrushEffect.process(data, format, Settings.DEFAULT);
            assertArrayEquals(input, data, String.valueOf(format));
        }
        assertDoesNotThrow(() -> BitcrushEffect.process(null, pcm16(1, false), Settings.DEFAULT));
    }

    @Test
    void trailingPartialFrameIsLeftUntouched() {
        byte[] input = randomPcm(1026);
        byte[] data = input.clone();
        BitcrushEffect.process(data, pcm16(2, false), Settings.DEFAULT);
        assertFalse(Arrays.equals(input, 0, 1024, data, 0, 1024));
        assertEquals(input[1024], data[1024]);
        assertEquals(input[1025], data[1025]);
    }

    @Test
    void neutralSettingsLeaveAudioUnchanged() {
        byte[] input = randomPcm(4096);
        byte[] data = input.clone();
        BitcrushEffect.process(data, pcm16(2, false), new Settings(16, 1, 1F));
        assertArrayEquals(input, data);
    }

    @Test
    void outOfRangeSettingsAreClampedInsteadOfThrowing() {
        assertEquals(new Settings(1, 1, 0.5F), new Settings(0, 0, Float.NaN));
        assertEquals(new Settings(1, 1, 0F), new Settings(Integer.MIN_VALUE, Integer.MIN_VALUE, 0F));
        assertEquals(16, new Settings(17, 1, 1F).bits());
        assertEquals(16, new Settings(Integer.MAX_VALUE, 1, 1F).bits());

        Settings[] extremes = {
                new Settings(0, 0, Float.NaN),
                new Settings(-5, -1, Float.POSITIVE_INFINITY),
                new Settings(17, Integer.MAX_VALUE, Float.NEGATIVE_INFINITY),
                new Settings(Integer.MAX_VALUE, Integer.MIN_VALUE, Float.MAX_VALUE),
        };
        for (Settings settings : extremes) {
            for (int channels = 1; channels <= 2; channels++) {
                byte[] data = randomPcm(1024);
                AudioFormat format = pcm16(channels, false);
                assertDoesNotThrow(() -> BitcrushEffect.process(data, format, settings), settings::toString);
            }
        }
    }

    private static AudioFormat pcm16(int channels, boolean bigEndian) {
        return pcm16(44100F, channels, bigEndian);
    }

    private static AudioFormat pcm16(float rate, int channels, boolean bigEndian) {
        return new AudioFormat(rate, 16, channels, true, bigEndian);
    }

    /** Mono 16-bit little-endian samples 0, 1, 2, ..., so every frame holds a different value. */
    private static byte[] ramp(int frames) {
        ByteBuffer buffer = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int frame = 0; frame < frames; frame++) {
            buffer.putShort((short) frame);
        }
        return buffer.array();
    }

    /** How many runs of repeated samples mono 16-bit little-endian data has. */
    private static int runs(byte[] data) {
        ShortBuffer samples = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        int runs = 0;
        for (int i = 0; i < samples.limit(); i++) {
            if (i == 0 || samples.get(i) != samples.get(i - 1)) {
                runs++;
            }
        }
        return runs;
    }

    /** Random 16-bit little-endian samples, starting with the extremes so clipping is always exercised. */
    private static byte[] randomPcm(int bytes) {
        byte[] data = new byte[bytes];
        new Random(bytes).nextBytes(data);
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (short extreme : new short[] {Short.MIN_VALUE, Short.MAX_VALUE, 0, -1, 1, Short.MIN_VALUE + 1}) {
            buffer.putShort(extreme);
        }
        return data;
    }

    private static byte[] channel(byte[] stereo, int channel) {
        byte[] mono = new byte[stereo.length / 2];
        for (int i = 0; i < mono.length; i += 2) {
            mono[i] = stereo[2 * i + 2 * channel];
            mono[i + 1] = stereo[2 * i + 2 * channel + 1];
        }
        return mono;
    }

    private static byte[] swapBytes(byte[] data) {
        byte[] swapped = new byte[data.length];
        for (int i = 0; i + 1 < data.length; i += 2) {
            swapped[i] = data[i + 1];
            swapped[i + 1] = data[i];
        }
        return swapped;
    }

    private static byte[] original(byte[] input, int bits, int sampleRateDivisor, float gain) {
        ByteBuffer output = process(ByteBuffer.wrap(input.clone()), bits, sampleRateDivisor, gain);
        byte[] bytes = new byte[output.remaining()];
        output.get(bytes);
        return bytes;
    }

    /**
     * The oracle: the original mod's {@code BitcrushEffect.process}, copied verbatim from the decompiled
     * source except that the three config reads are now parameters.
     */
    private static ByteBuffer process(ByteBuffer input, int bits, int sampleRateDivisor, float gain) {
        input.rewind();
        input.order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer output = ByteBuffer.allocateDirect(input.capacity()).order(ByteOrder.LITTLE_ENDIAN);
        int step = 1 << 16 - bits;
        short held = 0;

        for(int sampleIndex = 0; input.remaining() >= 2; ++sampleIndex) {
            short sample = input.getShort();
            if (sampleIndex % sampleRateDivisor == 0) {
                int gained = (int)((float)sample * gain);
                gained = Math.max(-32768, Math.min(32767, gained));
                held = (short)(gained & ~(step - 1));
            }

            output.putShort(held);
        }

        output.flip();
        return output;
    }
}
