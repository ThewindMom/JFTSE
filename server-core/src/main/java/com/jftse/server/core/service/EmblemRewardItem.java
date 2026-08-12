package com.jftse.server.core.service;

import java.util.Date;

public record EmblemRewardItem(int pocketId, byte category, int itemIndex, byte useType,
                               int count, Date created, byte enchantStrength, byte enchantStamina,
                               byte enchantDexterity, byte enchantWillpower, byte enchantElement,
                               byte enchantLevel) {
    public EmblemRewardItem { created = new Date(created.getTime()); }
    @Override public Date created() { return new Date(created.getTime()); }
}
