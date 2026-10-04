package com.halloweenswords;

import java.util.Locale;

public enum SwordType {
    JACK_O_BLADE("jack_o_blade", "Jack-O-Blade", 0xFF8A1F, "hollow_lantern",
            Ability.PUMPKIN_BOMB, Ability.TRICK_OR_TREAT),
    PHANTOM_FANG("phantom_fang", "Phantom Fang", 0xB9A7FF, "bone_ripper",
            Ability.HAUNT, Ability.GHOSTWALK),
    GRAVEKEEPER("gravekeeper", "Gravekeeper", 0xC8B58A, "bone_ripper",
            Ability.GRAVE_GRIP, Ability.RISE_FROM_BELOW),
    BLOODMOON_BLADE("bloodmoon_blade", "Bloodmoon Blade", 0xD62828, "candy_carver",
            Ability.BLOOD_HUNT, Ability.BLOOD_MOON),
    NIGHTFANG("nightfang", "Nightfang", 0x8A4FFF, "arachnid_fang",
            Ability.BAT_SWARM, Ability.NIGHT_FLIGHT),
    CURSED_BLADE("cursed_blade", "Cursed Blade", 0x6BD26B, "witch_thorn",
            Ability.HEX, Ability.POSSESSION),
    WIDOWMAKER("widowmaker", "Widowmaker", 0xE6E6E6, "arachnid_fang",
            Ability.WEB_SHOT, Ability.SPIDERS_FEAST);

    private final String id;
    private final String displayName;
    private final int color;
    private final String defaultModel;
    private final Ability primary;
    private final Ability secondary;

    SwordType(String id, String displayName, int color, String modelName, Ability primary, Ability secondary) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.defaultModel = "halloween:" + modelName;
        this.primary = primary;
        this.secondary = secondary;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int color() { return color; }
    public String defaultModel() { return defaultModel; }
    public Ability primary() { return primary; }
    public Ability secondary() { return secondary; }

    public static SwordType byId(String id) {
        if (id == null) return null;
        String s = id.toLowerCase(Locale.ROOT);
        for (SwordType t : values()) {
            if (t.id.equals(s)) return t;
        }
        return null;
    }
}
