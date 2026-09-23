package com.example.bitcrusher.mixin;

import com.example.bitcrusher.client.BitCrusherClient;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Swaps the identifier {@code playSound} hands to Paulscode (argument 3, {@code <ns>:sounds/<path>.ogg})
 * for the one {@link BitCrusherClient#route} picks.
 *
 * <p>The invoke targets aren't remapped: the owner has the same name in MCP and SRG, and
 * {@code newSource}/{@code newStreamingSource} are Paulscode methods, which are never obfuscated.
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {

    // argsOnly reads the parameter's slot at the call, which by then holds the sound Forge's PlaySoundEvent returned
    @ModifyArg(
            method = "playSound",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/audio/SoundManager$SoundSystemStarterThread;newSource(ZLjava/lang/String;Ljava/net/URL;Ljava/lang/String;ZFFFIF)V",
                    remap = false
            ),
            index = 3,
            require = 1,
            allow = 1
    )
    private String bitcrusher$routeSource(String identifier, @Local(argsOnly = true) ISound sound) {
        return BitCrusherClient.route(identifier, sound);
    }

    @ModifyArg(
            method = "playSound",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/audio/SoundManager$SoundSystemStarterThread;newStreamingSource(ZLjava/lang/String;Ljava/net/URL;Ljava/lang/String;ZFFFIF)V",
                    remap = false
            ),
            index = 3,
            require = 1,
            allow = 1
    )
    private String bitcrusher$routeStreamingSource(String identifier, @Local(argsOnly = true) ISound sound) {
        return BitCrusherClient.route(identifier, sound);
    }
}
