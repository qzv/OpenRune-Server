package org.rsmod.content.minigames.colosseum

import org.rsmod.api.combat.commons.player.PlayerAttackRangeHook
import org.rsmod.api.combat.commons.player.PlayerMaxHitHook
import org.rsmod.api.death.PlayerGraveFeeHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.api.player.hook.PlayerDefenceLevelHook
import org.rsmod.api.player.hook.PlayerLogoutRequestHook
import org.rsmod.api.player.hook.PlayerWornButtonHook
import org.rsmod.content.minigames.colosseum.modifiers.TotemicDamageContributor
import org.rsmod.content.minigames.colosseum.sol.SolGrappleHook
import org.rsmod.content.minigames.colosseum.sol.SolParryMaxHitHook
import org.rsmod.plugin.module.PluginModule

class ColosseumModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerRespawnHook>(ColosseumRespawnHook::class.java)
        addSetBinding<PlayerGraveFeeHook>(ColosseumGraveFeeHook::class.java)
        addSetBinding<PlayerWornButtonHook>(SolGrappleHook::class.java)
        addSetBinding<PlayerMaxHitHook>(SolParryMaxHitHook::class.java)
        addSetBinding<PlayerLogoutRequestHook>(ColosseumLogoutHook::class.java)
        addSetBinding<PlayerAttackRangeHook>(MyopiaRangeHook::class.java)
        addSetBinding<PlayerDefenceLevelHook>(RelentlessDefenceHook::class.java)
        addSetBinding<NpcDamageContributor>(TotemicDamageContributor::class.java)
    }
}
