package com.example.bitcrusher.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Decides which sound events play clean. Entries are matched against the sound event's ID (e.g.
 * {@code minecraft:ui.button.click}), not the sound file it plays. It only uses JDK classes so it
 * can be unit tested without Minecraft.
 */
public final class SoundWhitelist {

    public static final SoundWhitelist EMPTY = new SoundWhitelist(Set.of(), List.of(), Set.of());

    private static final String DEFAULT_NAMESPACE = "minecraft";
    // Resource location characters, plus * for wildcards
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.\\-*]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_.\\-/*]+");
    private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_.\\-]+");

    private final Set<String> sounds;
    private final List<Wildcard> wildcards;
    private final Set<String> mods;

    private SoundWhitelist(Set<String> sounds, List<Wildcard> wildcards, Set<String> mods) {
        this.sounds = sounds;
        this.wildcards = wildcards;
        this.mods = mods;
    }

    /**
     * Builds a whitelist from the config lists. Entries are case-insensitive and blank ones are
     * skipped. Invalid entries are skipped too, with a line added to {@code problems} for the caller
     * to log.
     *
     * @param sounds sound event IDs; a missing namespace means {@code minecraft:} and {@code *}
     *               matches anything, e.g. {@code minecraft:music.*} or {@code *:ui.*}
     * @param mods   mod IDs, matched against the sound event's namespace
     */
    public static SoundWhitelist compile(String[] sounds, String[] mods, List<String> problems) {
        Set<String> exact = new HashSet<>();
        List<Wildcard> wildcards = new ArrayList<>();
        for (String entry : normalize(sounds)) {
            int colon = entry.indexOf(':');
            String namespace = colon < 0 ? "" : entry.substring(0, colon);
            String path = entry.substring(colon + 1);
            if (namespace.isEmpty()) {
                // Same as ResourceLocation
                namespace = DEFAULT_NAMESPACE;
            }
            if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
                problems.add("Ignoring whitelisted sound '" + entry + "': expected a sound event ID like minecraft:ui.button.click");
            } else if (namespace.indexOf('*') < 0 && path.indexOf('*') < 0) {
                exact.add(namespace + ':' + path);
            } else {
                wildcards.add(new Wildcard(glob(namespace), glob(path)));
            }
        }

        Set<String> modIds = new HashSet<>();
        for (String entry : normalize(mods)) {
            if (MOD_ID.matcher(entry).matches()) {
                modIds.add(entry);
            } else {
                problems.add("Ignoring whitelisted mod '" + entry + "': expected a mod ID like minecraft");
            }
        }

        return new SoundWhitelist(Set.copyOf(exact), List.copyOf(wildcards), Set.copyOf(modIds));
    }

    /** Whether the sound event {@code namespace:path} should play clean. */
    public boolean matches(String namespace, String path) {
        namespace = namespace.toLowerCase(Locale.ROOT);
        path = path.toLowerCase(Locale.ROOT);
        if (mods.contains(namespace) || sounds.contains(namespace + ':' + path)) {
            return true;
        }
        for (Wildcard wildcard : wildcards) {
            if (wildcard.namespace.matcher(namespace).matches() && wildcard.path.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    /** The whitelisted mod IDs, lowercase. */
    public Set<String> mods() {
        return mods;
    }

    private static List<String> normalize(String[] entries) {
        List<String> normalized = new ArrayList<>();
        if (entries != null) {
            for (String entry : entries) {
                if (entry != null && !entry.isBlank()) {
                    normalized.add(entry.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return normalized;
    }

    private static Pattern glob(String glob) {
        StringJoiner regex = new StringJoiner(".*");
        for (String literal : glob.split("\\*", -1)) {
            regex.add(literal.isEmpty() ? "" : Pattern.quote(literal));
        }
        return Pattern.compile(regex.toString());
    }

    private record Wildcard(Pattern namespace, Pattern path) {}
}
