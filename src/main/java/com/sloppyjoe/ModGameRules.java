package com.sloppyjoe;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;

public class ModGameRules {

    /**
     * If true (default), Hollow Purple destroys blocks in its path.
     * Set to false to make the attack entity-only (no terrain damage).
     * /gamerule hollowPurpleGriefing [true|false]
     */
    public static final GameRules.Key<GameRules.BooleanRule> HOLLOW_PURPLE_GRIEFING =
            GameRuleRegistry.register("hollowPurpleGriefing", GameRules.Category.PLAYER,
                    GameRuleFactory.createBooleanRule(true));

    /** Call during mod init to trigger static class loading. */
    public static void register() {}
}
