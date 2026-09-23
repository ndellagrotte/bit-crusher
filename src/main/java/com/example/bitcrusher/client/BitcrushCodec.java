package com.example.bitcrusher.client;

import com.example.bitcrusher.core.BitcrushEffect;
import java.net.URL;
import javax.sound.sampled.AudioFormat;
import paulscode.sound.ICodec;
import paulscode.sound.SoundBuffer;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.codecs.CodecJOrbis;

/**
 * Paulscode codec for the identifiers {@link BitCrusherClient#route} sends here. It decodes through
 * the codec registered for the underlying file, normally the vanilla ogg one, and crushes every
 * buffer that comes back. Paulscode creates one instance per decode through the public no-argument
 * constructor.
 */
public class BitcrushCodec implements ICodec {

    private ICodec delegate;
    private boolean reverseByteOrder;

    @Override
    public void reverseByteOrder(boolean reverse) {
        // Paulscode calls this before initialize, when there's no delegate yet to pass it to
        reverseByteOrder = reverse;
        if (delegate != null) {
            delegate.reverseByteOrder(reverse);
        }
    }

    @Override
    public boolean initialize(URL url) {
        // Streams are initialized several times with no cleanup in between (once more on every loop),
        // so keep one delegate
        if (delegate == null) {
            delegate = createDelegate(url);
            delegate.reverseByteOrder(reverseByteOrder);
        }
        return delegate.initialize(url);
    }

    @Override
    public boolean initialized() {
        return delegate != null && delegate.initialized();
    }

    @Override
    public SoundBuffer read() {
        return delegate == null ? null : crush(delegate.read());
    }

    @Override
    public SoundBuffer readAll() {
        return delegate == null ? null : crush(delegate.readAll());
    }

    @Override
    public boolean endOfStream() {
        return delegate != null && delegate.endOfStream();
    }

    @Override
    public void cleanup() {
        if (delegate != null) {
            delegate.cleanup();
        }
    }

    @Override
    public AudioFormat getAudioFormat() {
        return delegate == null ? null : delegate.getAudioFormat();
    }

    private static ICodec createDelegate(URL url) {
        // Only the identifier carries our extension. The URL's path still names the real file, e.g.
        // minecraft:sounds/ambient/cave/cave1.ogg, so this finds whichever codec is registered for it.
        String path = url == null ? null : url.getPath();
        ICodec codec = path == null ? null : SoundSystemConfig.getCodec(path);
        return codec == null || codec instanceof BitcrushCodec ? new CodecJOrbis() : codec;
    }

    private static SoundBuffer crush(SoundBuffer buffer) {
        if (buffer != null) {
            BitcrushEffect.process(buffer.audioData, buffer.audioFormat, BitCrusherClient.settings());
        }
        return buffer;
    }
}
