package org.rsmod.content.bosses.nex

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.proj.ProjAnim

@Singleton
internal class NexZaros
@Inject
constructor(
    private val deps: BossDeps,
    private val fights: NexFights,
    private val casts: NexCasts,
    private val melee: NexMelee,
) {
    fun standard(nex: Npc, target: Player) {
        val fight = fights.fightFor(nex) ?: return
        fight.zarosAttacks++
        NexZarosRules.formChange(fight.zarosAttacks)?.let { transform(fight, nex, it) }
        val meleed =
            melee.attempt(
                fight,
                nex,
                target,
                MELEE_START_CHANCE,
                MELEE_START_OUT_OF,
                MELEE_PENETRATION,
            ) { rolled, dealt ->
                meleeHit(fight, nex, target, rolled, dealt)
            }
        if (!meleed) cast(fight, nex)
    }

    private fun transform(fight: NexFight, nex: Npc, form: NexForm) {
        val type = ServerCacheManager.getNpc(form.npc.asRSCM(RSCMType.NPC)) ?: return
        fight.form = form
        nex.transmog(type, Int.MAX_VALUE)
        val centre = NexGeometry.centre(nex.coords, nex.size)
        deps.worldRepo.soundArea(centre, TRANSFORM_SYNTH, radius = SOUND_RADIUS)
    }

    private fun meleeHit(fight: NexFight, nex: Npc, target: Player, rolled: Int, dealt: Int) {
        if (dealt <= 0) return
        when (fight.form) {
            NexForm.Plain -> leech(nex, target)
            NexForm.SoulSplit -> {
                val heal = NexZarosRules.soulSplitHeal(rolled)
                soulSplit(fight, nex, target, MELEE_SOUL_DELAY, mapOf(MELEE_HEAL_DELAY to heal))
            }
            NexForm.Deflect -> Unit
        }
    }

    private fun leech(nex: Npc, target: Player) {
        val type =
            ServerCacheManager.getProjectile(LEECH_TRAVEL.asRSCM(RSCMType.PROJANIM)) ?: return
        val spotanim = LEECH_SPOTANIM.asRSCM(RSCMType.SPOTANIM)
        val centre = NexGeometry.centre(nex.coords, nex.size)
        val proj = ProjAnim.fromNpcToPlayer(nex, target, spotanim, type)
        deps.worldRepo.projAnim(proj.copy(startCoord = centre))
        target.soundSynth(LEECH_SYNTH)
        deps.worldQueues.add(LEECH_DRAIN_DELAY) {
            if (!target.isValidTarget()) return@add
            target.soundSynth(LEECH_DRAIN_SYNTH)
            for (stat in LEECHED_STATS) target.statSub(stat, LEECH_DRAIN, 0)
        }
    }

    private fun soulSplit(
        fight: NexFight,
        nex: Npc,
        player: Player,
        projectileDelay: Int,
        heals: Map<Int, Int>,
    ) {
        deps.worldQueues.add(projectileDelay) {
            if (fight.nex !== nex || nex.hitpoints <= 0 || !player.isValidTarget()) return@add
            val spotanim = SOUL_SPLIT_SPOTANIM.asRSCM(RSCMType.SPOTANIM)
            val proj = ProjAnim.fromPlayerToNpc(player, nex, spotanim, SOUL_SPLIT_TRAVEL)
            val centre = NexGeometry.centre(nex.coords, nex.size)
            deps.worldRepo.projAnim(proj.copy(endCoord = centre))
            player.soundSynth(SOUL_SPLIT_SYNTH)
        }
        for ((tick, amount) in heals) {
            deps.worldQueues.add(tick) { fights.heal(fight, nex, amount) }
        }
    }

    private fun cast(fight: NexFight, nex: Npc) {
        nex.anim(CAST_SEQ)
        val centre = NexGeometry.centre(nex.coords, nex.size)
        deps.worldRepo.soundArea(centre, CAST_SYNTH, radius = SOUND_RADIUS)
        val targets =
            fights.players(fight).filter {
                NexGeometry.footprintDistance(nex.coords, nex.size, it.coords) <= ATTACK_RANGE
            }
        val heals = HashMap<Int, Int>()
        for (target in targets) {
            val result = casts.cast(nex, target, FINALE_SPELL) ?: continue
            val dealt = result.damage ?: continue
            if (dealt <= 0) continue
            val drain = NexDrains.castPrayerDrain(dealt)
            val drainTick = result.impactTick - MAGIC_DRAIN_LEAD
            deps.worldQueues.add(drainTick) { drainPrayer(target, drain) }
            if (fight.form != NexForm.SoulSplit) continue
            val heal = NexZarosRules.soulSplitHeal(result.rolled)
            heals.merge(result.impactTick + MAGIC_HEAL_LAG, heal, Int::plus)
            soulSplit(fight, nex, target, drainTick, emptyMap())
        }
        for ((tick, amount) in heals) {
            deps.worldQueues.add(tick) { fights.heal(fight, nex, amount) }
        }
    }

    private fun drainPrayer(player: Player, amount: Int) {
        if (amount > 0 && player.isValidTarget()) player.statSub(PRAYER, amount, 0)
    }

    private companion object {
        private const val PRAYER = "stat.prayer"
        private const val SOUND_RADIUS = 15
        private const val ATTACK_RANGE = 10
        private const val TRANSFORM_SYNTH = "synth.nex_transform"

        private const val MELEE_START_CHANCE = 50
        private const val MELEE_START_OUT_OF = 60
        private const val MELEE_PENETRATION = 33

        private const val LEECH_TRAVEL = "projanim.nex_turmoil_leech"
        private const val LEECH_SPOTANIM = "spotanim.nex_spot_leech_projanim_defence"
        private const val LEECH_SYNTH = "synth.nex_turmoil_leech"
        private const val LEECH_DRAIN_SYNTH = "synth.nex_turmoil_drain"
        private const val LEECH_DRAIN_DELAY = 1
        private const val LEECH_DRAIN = 2
        private val LEECHED_STATS =
            listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic")

        private const val SOUL_SPLIT_TRAVEL = "projanim.nex_soul_split"
        private const val SOUL_SPLIT_SPOTANIM = "spotanim.nex_soulsplit_projanim"
        private const val SOUL_SPLIT_SYNTH = "synth.nex_soul_split"
        private const val MELEE_SOUL_DELAY = 1
        private const val MELEE_HEAL_DELAY = 3
        private const val MAGIC_DRAIN_LEAD = 1
        private const val MAGIC_HEAL_LAG = 1

        private const val CAST_SEQ = "seq.nex_cast_attack"
        private const val CAST_SYNTH = "synth.nex_finale_cast"
        private val FINALE_SPELL =
            NexSpell(
                spotanim = "spotanim.nex_finale_attack_proj",
                travel = "projanim.nex_finale_attack",
                impact = "spotanim.nex_finale_attack_impact",
                impactHeight = 124,
                impactSynth = "synth.nex_finale_impact",
                maxHit = 33,
                penetration = 50,
                minEndTime = 60,
                extraHitDelay = 1,
            )
    }
}
