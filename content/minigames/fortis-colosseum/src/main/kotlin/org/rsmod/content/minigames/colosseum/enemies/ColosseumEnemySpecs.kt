package org.rsmod.content.minigames.colosseum.enemies

import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ImpactRounding
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.content.minigames.colosseum.ColosseumNpcs
import org.rsmod.game.entity.Npc
import org.rsmod.map.util.Bounds

internal object ColosseumEnemyVarns {
    const val SPAWN_TICK = "varn.colosseum_spawn_tick"
    const val ATTACKS = "varn.colosseum_attacks"
    val ORBS = listOf("varn.colosseum_orb_1", "varn.colosseum_orb_2", "varn.colosseum_orb_3")
    const val ORBS_SHOWN = "varn.colosseum_orbs_shown"
    const val GIB_DIRECTION = "varn.colosseum_gib_direction"
    const val LAST_THROW = "varn.colosseum_last_throw"
    const val TOTEM_SPAWN = "varn.colosseum_totem_spawn_tick"
    const val TOTEM_TIMER = "varn.colosseum_totem_timer"
    const val FLARE_STEP = "varn.colosseum_flare_step_tick"

    fun Npc.tickVarn(varn: String): Int? = vars[varn].takeIf { it > 0 }?.minus(1)

    fun Npc.setTickVarn(varn: String, tick: Int) {
        vars[varn] = tick + 1
    }
}

internal class ColosseumEnemySpecs(
    private val deps: BossDeps,
    private val healTarget: (Npc) -> Npc?,
) {
    private val random = deps.random

    private fun warbandTurn(offset: Int): Condition =
        Condition.Custom { npc, target ->
            if (target == null || npc.hasMovedThisCycle || !npc.isWithinDistance(target, 1)) {
                return@Custom false
            }
            val elapsed = deps.mapClock.cycle - npc.vars[ColosseumEnemyVarns.SPAWN_TICK] - offset
            elapsed >= 0 && elapsed % WARBAND_CYCLE == 0
        }

    val berserker: BossSpec =
        boss(ColosseumNpcs.BERSERKER) {
            stats(attackRate = 1)
            val stab =
                ability("stab") {
                    anim("seq.npc_fremennik_warbander_melee_human_sword_stab")
                    hit {
                        damage(ColosseumDamage.unchecked(random, BERSERKER_MAX))
                        type(Melee)
                        delay = 2
                    }
                }
            phase("fight") { forceWhen(warbandTurn(BERSERKER_OFFSET), stab) }
        }

    val seer: BossSpec =
        boss(ColosseumNpcs.SEER) {
            stats(attackRate = 1)
            val cast =
                ability("cast") {
                    anim("seq.npc_fremennik_warbander_mage_zaros_vertical_casting_walkmerge")
                    soundTo("synth.colosseum_seer_cast")
                    projectile {
                        spotanim = "spotanim.fireblast_travel"
                        config =
                            ProjectileConfig.fixed(
                                172,
                                124,
                                delay = 10,
                                travel = 20,
                                angle = 16,
                                progress = 64,
                            )
                        hit {
                            damage(ColosseumDamage.unchecked(random, SEER_MAX))
                            type(Magic)
                            spotanim("spotanim.fireblast_impact", height = 124)
                        }
                    }
                }
            phase("fight") { forceWhen(warbandTurn(SEER_OFFSET), cast) }
            incoming { rule(hitStyle(Ranged)) { floorPercentOfMaxHit(100, Ranged) } }
        }

    val archer: BossSpec =
        boss(ColosseumNpcs.ARCHER) {
            stats(attackRate = 1)
            val shoot =
                ability("shoot") {
                    anim("seq.npc_fremennik_warbander_archer_att_colosseum")
                    projectile {
                        spotanim = "spotanim.iron_arrow_travel"
                        config =
                            ProjectileConfig.fixed(
                                163,
                                146,
                                delay = 10,
                                travel = 20,
                                angle = 15,
                                progress = 64,
                            )
                        hit {
                            damage(ColosseumDamage.unchecked(random, ARCHER_MAX))
                            type(Ranged)
                        }
                    }
                }
            phase("fight") { forceWhen(warbandTurn(ARCHER_OFFSET), shoot) }
            incoming { rule(hitStyle(Melee)) { floorPercentOfMaxHit(100, Melee) } }
        }

    val shaman: BossSpec =
        boss(ColosseumNpcs.SHAMAN) {
            stats(attackRate = 5)
            val surge =
                ability("water_surge") {
                    anim("seq.npc_serpent_mager_casting")
                    spotanim("spotanim.watersurge_casting", height = 92)
                    projectile {
                        spotanim = "spotanim.watersurge_travel"
                        config =
                            ProjectileConfig(
                                startHeight = 172,
                                endHeight = 146,
                                startDelay = 67,
                                travelTime = 0,
                                angle = 14,
                                progress = 32,
                                stepMultiplier = 4,
                                minFlight = SHAMAN_MIN_FLIGHT,
                            )
                        impactRounding = ImpactRounding.Up
                        impactSlack = SHAMAN_IMPACT_SLACK
                        hit {
                            damage(ColosseumDamage.roll(random, SHAMAN_MAX))
                            type(Magic)
                            spotanim("spotanim.watersurge_impact", height = 124)
                        }
                    }
                }
            phase("fight") { weightedSelectorRandom { +random(surge, weight = 1) } }
        }

    val javelin: BossSpec =
        boss(ColosseumNpcs.JAVELIN) {
            stats(attackRate = 5)
            val throwJavelin =
                ability("javelin") {
                    anim("seq.npc_colossi_javelin_01_range_attack")
                    addVarn(ColosseumEnemyVarns.ATTACKS, 1)
                    projectile {
                        spotanim = "spotanim.npc_colossi_javelin_01_spearhead"
                        from = Centre
                        config =
                            ProjectileConfig.fixed(200, 138, delay = 58, travel = 32, angle = 16)
                        hit {
                            damage(ColosseumDamage.roll(random, JAVELIN_MAX))
                            type(Ranged)
                        }
                    }
                    soundTo("synth.colosseum_javelin_impact", delay = 90)
                }
            val artillery =
                ability("artillery") {
                    anim("seq.npc_colossi_javelin_01_artillery_attack")
                    spotanim("spotanim.npc_colossi_javelin_01_artillery_fire", delay = 60)
                    setVarn(ColosseumEnemyVarns.ATTACKS, 0)
                    include(after(ARTILLERY_MARK_TICKS, artilleryStrike()))
                }
            phase("fight") {
                forceWhen(
                    varnAtLeast(ColosseumEnemyVarns.ATTACKS, JAVELINS_PER_ARTILLERY),
                    artillery,
                )
                weightedSelectorRandom { +random(throwJavelin, weight = 1) }
            }
        }

    private fun artilleryStrike(): Effect =
        withTile(
            ARTILLERY_TILE,
            CurrentTargetTile,
            sequence(
                mapSpotanim("spotanim.gargboss_debris_shadow_90", tile(ARTILLERY_TILE), delay = 30),
                external(ColosseumEnemyHandlers.ARTILLERY_MARK, at = tile(ARTILLERY_TILE)),
                soundTo("synth.colosseum_artillery_whistle"),
                after(
                    ARTILLERY_LAND_TICKS,
                    sequence(
                        mapSpotanim("spotanim.wild_falloff_meteor_blast_red", tile(ARTILLERY_TILE)),
                        external(ColosseumEnemyHandlers.ARTILLERY_LAND, at = tile(ARTILLERY_TILE)),
                    ),
                ),
            ),
        )

    val shockwave: BossSpec =
        boss(ColosseumNpcs.SHOCKWAVE) {
            stats(attackRate = 5)
            val clap =
                ability("shockwave") {
                    anim("seq.npc_colossi_shockwave_01_clapattack")
                    projectile {
                        spotanim = "spotanim.vfx_vardorvis_head_projectile_magic_01"
                        from = Centre
                        config =
                            ProjectileConfig(
                                startHeight = 158,
                                endHeight = 100,
                                startDelay = 50,
                                travelTime = 26,
                                angle = 0,
                                progress = 40,
                                stepMultiplier = 8,
                            )
                        hit {
                            damage(ColosseumDamage.roll(random, SHOCKWAVE_MAX))
                            type(Magic)
                            spotanim("spotanim.akkha_magic_impact", height = 64)
                        }
                    }
                }
            phase("fight") { weightedSelectorRandom { +random(clap, weight = 1) } }
        }

    private val jaguarInRange =
        Condition.Custom { npc, target ->
            target != null &&
                npc.level == target.level &&
                Bounds.xDistanceBetween(npc.bounds(), target.bounds()) +
                    Bounds.zDistanceBetween(npc.bounds(), target.bounds()) == 1
        }

    val jaguar: BossSpec =
        boss(ColosseumNpcs.JAGUAR) {
            stats(attackRate = 5)
            val claws =
                ability("claws") {
                    anim("seq.npc_jaguar_ranger_claws_attack")
                    include(external(ColosseumEnemyHandlers.JAGUAR_CLAWS))
                }
            phase("fight") {
                weightedSelectorRandom { +random(claws, weight = 1, requires = jaguarInRange) }
            }
        }

    val minotaur: BossSpec =
        boss(ColosseumNpcs.MINOTAUR, ColosseumNpcs.MINOTAUR_ROUTEFIND) {
            stats(attackRate = 5)
            val heal =
                ability(
                    "heal",
                    external(ColosseumEnemyHandlers.MINOTAUR_HEAL),
                    attackDelay = MINOTAUR_HEAL_DELAY,
                )
            val crush =
                ability("crush") {
                    anim("seq.npc_minotaur_boss_attack_melee")
                    include(after(1, external(ColosseumEnemyHandlers.MINOTAUR_CRUSH)))
                }
            val healing =
                Condition.Custom { npc, target ->
                    target != null && !npc.isWithinDistance(target, 1) && healTarget(npc) != null
                }
            phase("fight") {
                forceWhen(healing, heal)
                weightedSelectorRandom { +random(crush, weight = 1, requires = WithinMeleeRange) }
            }
        }

    val all: List<BossSpec>
        get() = listOf(berserker, seer, archer, shaman, javelin, shockwave, jaguar, minotaur)

    companion object {
        const val WARBAND_CYCLE = 6
        const val BERSERKER_OFFSET = 3
        const val SEER_OFFSET = 4
        const val ARCHER_OFFSET = 5

        const val BERSERKER_MAX = 29
        const val SEER_MAX = 12
        const val ARCHER_MAX = 14
        const val SHAMAN_MAX = 28
        const val SHAMAN_IMPACT_SLACK = 10
        const val SHAMAN_MIN_FLIGHT = 80
        const val JAVELIN_MAX = 48
        const val SHOCKWAVE_MAX = 56
        const val JAGUAR_MAX = 47
        const val JAGUAR_HITS = 3
        const val MINOTAUR_MAX = 74
        const val MINOTAUR_HEAL_DELAY = 10

        const val JAVELINS_PER_ARTILLERY = 4
        const val ARTILLERY_MARK_TICKS = 3
        const val ARTILLERY_LAND_TICKS = 3
        private const val ARTILLERY_TILE = "artillery"
    }
}
