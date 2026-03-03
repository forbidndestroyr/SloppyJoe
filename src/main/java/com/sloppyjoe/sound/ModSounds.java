package com.sloppyjoe.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {

    /** Voice line: "Domain Expansion: Infinite Void" — place OGG at
     *  assets/sloppyjoe/sounds/domain/unlimited_void_voice.ogg */
    public static final SoundEvent DOMAIN_UNLIMITED_VOID_VOICE =
            register("domain.unlimited_void_voice");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of("sloppyjoe", name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    /** Call once from SloppyJoeMod.onInitialize() to load the class. */
    public static void init() {}
}
