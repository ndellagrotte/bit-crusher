# Bit Crusher

A client-side Cleanroom (Minecraft 1.12.2) mod that bit-crushes the game's sounds. Sounds that come from a resource pack play clean unless you turn that off, and you can whitelist single sounds, or every sound from a mod, so they play clean too.

It's a port of the Fabric 1.21.1 mod "Bit cruncher" by ghouldubs, and it applies the same effect.

## Settings

Change them in game under **Mods → Bit Crusher → Config**, or in `config/bitcrusher.cfg`.

### Effect

| Setting | Default | Range | What it does |
|---|---|---|---|
| `bits` | 8 | 1–16 | Bit depth each sample is reduced to. Lower sounds crunchier. 16 leaves the depth alone. |
| `sampleRateDivisor` | 4 | 1–8 | Divides the effective sample rate by holding samples. Every sound ends up at 44100 / this Hz, whatever rate its file uses, so a mod's 192 kHz sounds get crushed as much as vanilla's. 1 leaves the rate alone. |
| `gain` | 0.5 | 0–10 | Volume multiplier applied before crushing. Anything pushed past full scale clips. |

Changing an effect setting restarts the sound engine, the same way F3+T does. Sounds that are playing stop, and the music comes back a little later. Setting `bits = 16`, `sampleRateDivisor = 1` and `gain = 1.0` makes everything sound like vanilla.

### Whitelist

Whitelisted sounds play clean. Changes apply from the next sound played, with no restart.

- **`applyToSoundPacks`** (default `false`): whether sounds from resource packs get crushed too. While it's off, any sound whose file comes from an enabled resource pack, or a server's or world's pack, plays clean. It goes by file, so a pack that only edits `sounds.json` to point events at vanilla or mod sounds doesn't count.
- **`sounds`**: sound event IDs, one per line. These are the same IDs `/playsound` takes, e.g. `minecraft:ui.button.click`.
  - A missing namespace means `minecraft:`.
  - `*` matches anything: `minecraft:music.*` covers all the music, and `*:ui.*` covers every mod's UI sounds.
  - A lone `*` means `minecraft:*`. Use `*:*` for everything.
- **`mods`**: mod IDs, one per line. Every sound whose event ID is in that mod's namespace plays clean, so `minecraft` covers all of vanilla. A mod that isn't installed only gets a warning in the log.

Matching ignores case. Invalid entries are skipped and logged.

```
whitelist {
    B:applyToSoundPacks=false
    S:mods <
        somemod
     >
    S:sounds <
        minecraft:ui.button.click
        minecraft:music.*
     >
}
```

## Building

```
./gradlew build
```

The mod jar is written to `build/libs/bitcrusher-<version>.jar`. `./gradlew runClient` starts a dev client.

## Credits

Based on Bit cruncher by ghouldubs, released under CC0-1.0 ([license text](src/main/resources/LICENSE_bit-cruncher), also shipped in the jar). This port is licensed under the GPLv3 (see [LICENSE](LICENSE)).
