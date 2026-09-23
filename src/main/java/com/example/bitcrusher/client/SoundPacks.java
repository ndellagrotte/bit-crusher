package com.example.bitcrusher.client;

import com.example.bitcrusher.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.sound.SoundLoadEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Tells which sound files come from resource packs. Enabled packs and the server's or world's pack
 * always sit above vanilla's and the mods' assets, so if one of them has a file, that copy is what
 * plays.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class SoundPacks {

    private static volatile Snapshot snapshot = new Snapshot(List.of());

    private SoundPacks() {}

    /** Whether an enabled resource pack, or the server's or world's pack, has {@code file}. */
    public static boolean provides(ResourceLocation file) {
        return snapshot.provides(file);
    }

    // Pack changes always reload the sounds, which restarts the sound system and posts this event. The
    // restart also empties Paulscode's cache, so no routing decision outlives the audio it was made for.
    @SubscribeEvent
    public static void onSoundLoad(SoundLoadEvent event) {
        ResourcePackRepository repository = Minecraft.getMinecraft().getResourcePackRepository();
        List<IResourcePack> packs = new ArrayList<>();
        for (ResourcePackRepository.Entry entry : repository.getRepositoryEntries()) {
            packs.add(entry.getResourcePack());
        }
        IResourcePack serverPack = repository.getServerResourcePack();
        if (serverPack != null) {
            packs.add(serverPack);
        }
        snapshot = new Snapshot(List.copyOf(packs));
    }

    // Enabled packs as of the last sound reload, and the lookups made against them since
    private record Snapshot(List<IResourcePack> packs, Map<ResourceLocation, Boolean> provided) {

        Snapshot(List<IResourcePack> packs) {
            this(packs, new ConcurrentHashMap<>());
        }

        boolean provides(ResourceLocation file) {
            // A folder pack checks the disk on every resourceExists call, so each file is looked up once
            return !packs.isEmpty() && provided.computeIfAbsent(file, f -> packs.stream().anyMatch(pack -> pack.resourceExists(f)));
        }
    }
}
