package com.halloweenswords;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Cooldowns {
    private final Map<UUID, Map<Ability, Long>> map = new HashMap<>();

    public long remainingMs(UUID player, Ability ability) {
        Map<Ability, Long> m = map.get(player);
        if (m == null) return 0L;
        Long until = m.get(ability);
        if (until == null) return 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public void set(UUID player, Ability ability, long durationMs) {
        map.computeIfAbsent(player, k -> new EnumMap<>(Ability.class))
                .put(ability, System.currentTimeMillis() + durationMs);
    }

    public void clear(UUID player) {
        map.remove(player);
    }
}
