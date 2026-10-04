package com.halloweenswords;

public enum Ability {
    PUMPKIN_BOMB("pumpkin_bomb", "Pumpkin Bomb", 45,
            "Launch a flaming jack-o'-lantern that explodes on impact."),
    TRICK_OR_TREAT("trick_or_treat", "Trick or Treat", 70,
            "Random powerful buff... or a small chance of a curse."),
    HAUNT("haunt", "Haunt", 50,
            "Your target is blinded by Darkness and sees ghosts."),
    GHOSTWALK("ghostwalk", "Ghostwalk", 75,
            "Invisible, Speed II, no fall damage. Attacking ends it."),
    GRAVE_GRIP("grave_grip", "Grave Grip", 55,
            "Grasping hands pin your target in place."),
    RISE_FROM_BELOW("rise_from_below", "Rise From Below", 80,
            "Undead claw their way out of the ground and attack your target."),
    BLOOD_HUNT("blood_hunt", "Blood Hunt", 55,
            "Mark a target. They glow and your hits heal you."),
    BLOOD_MOON("blood_moon", "Blood Moon", 90,
            "Strength I + Speed I. Every kill restores health."),
    BAT_SWARM("bat_swarm", "Bat Swarm", 45,
            "A swarm of bats blinds and bites your target."),
    NIGHT_FLIGHT("night_flight", "Night Flight", 65,
            "Launch forward and upward in a trail of bats and smoke."),
    HEX("hex", "Hex", 60,
            "Your target is hurt every time they attack you."),
    POSSESSION("possession", "Possession", 90,
            "Scramble your target's hotbar and blind them."),
    WEB_SHOT("web_shot", "Web Shot", 45,
            "Shoot a web that traps whoever it hits."),
    SPIDERS_FEAST("spiders_feast", "Spider's Feast", 75,
            "Web-covered zone that slows enemies while you get Speed II.");

    private final String id;
    private final String displayName;
    private final int defaultCooldown;
    private final String description;

    Ability(String id, String displayName, int defaultCooldown, String description) {
        this.id = id;
        this.displayName = displayName;
        this.defaultCooldown = defaultCooldown;
        this.description = description;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int defaultCooldown() { return defaultCooldown; }
    public String description() { return description; }
}
