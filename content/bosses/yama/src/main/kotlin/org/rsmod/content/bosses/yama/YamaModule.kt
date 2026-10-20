package org.rsmod.content.bosses.yama

import org.rsmod.api.combat.commons.player.PlayerAttackHook
import org.rsmod.api.combat.commons.player.PlayerAttackRangeHook
import org.rsmod.api.player.hook.PlayerRunValidateHook
import org.rsmod.plugin.module.PluginModule

class YamaModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerAttackHook>(YamaJudgeAttackHook::class.java)
        addSetBinding<PlayerAttackRangeHook>(YamaRangeHook::class.java)
        addSetBinding<PlayerRunValidateHook>(YamaRunHook::class.java)
    }
}
