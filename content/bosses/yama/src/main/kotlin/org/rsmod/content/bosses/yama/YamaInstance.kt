package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.startEncounter
import org.rsmod.api.bosses.runtime.suppressAttacks
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceEnterTransition
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.ui.ifCloseChat
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.bosses.yama.YamaThroneTalk.SIGN_STATE_BACKED_OUT
import org.rsmod.content.bosses.yama.YamaThroneTalk.SIGN_STATE_SIGNED
import org.rsmod.content.bosses.yama.YamaThroneTalk.contractOffer
import org.rsmod.content.bosses.yama.YamaThroneTalk.offerContract
import org.rsmod.content.bosses.yama.YamaThroneTalk.talk
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class YamaInstance
@Inject
internal constructor(
    registry: BossInstanceRegistry,
    private val deps: BossDeps,
    private val bossHpBar: BossHpBarScript,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val fights: YamaFights,
    private val firewall: YamaFirewall,
    private val contractOverlay: YamaContractOverlay,
    private val music: YamaMusic,
    private val eventBus: EventBus,
) : InstanceScript(registry) {

    private val summoning = HashSet<InstanceSession>()

    override fun settingsRow(): String = SETTINGS_ROW

    override fun area(): InstanceArea = INSTANCE

    override fun destroyWhenEmpty(): Boolean = true

    override fun ScriptContext.configure() {
        onEnterTransition(InstanceEnterTransition(message = ENTER_MESSAGE))
        onOpNpc1(VOICE_OF_YAMA) { startDialogue(it.npc) { voiceTalk() } }
        onOpNpc3(VOICE_OF_YAMA) { travel() }
        onOpNpc4(VOICE_OF_YAMA) { joinPartner() }
        onOpNpc1(THRONE) {
            startDialogue(it.npc) {
                talk(
                    player.displayName,
                    player.contractSigned,
                    onDecline = { player.contractSigned = SIGN_STATE_BACKED_OUT },
                    onSign = { player.contractSigned = SIGN_STATE_SIGNED },
                    onChallenge = { challenge(player, it.npc, spawnDelay = DIALOGUE_SPAWN_DELAY) },
                )
            }
        }
        onOpNpc3(THRONE) {
            if (player.contractSigned != SIGN_STATE_SIGNED) {
                startDialogue(it.npc) {
                    offerContract(
                        player.displayName,
                        onDecline = { player.contractSigned = SIGN_STATE_BACKED_OUT },
                        onSign = { player.contractSigned = SIGN_STATE_SIGNED },
                    )
                }
            } else {
                greet(player, it.npc)
                challenge(player, it.npc)
            }
        }
        registerContractUses()
        onInstancePlayerJoin {
            player.throneState = THRONE_STATE_IDLE
            music.play(player)
        }
        onInstancePlayerLeave {
            player.throneState = THRONE_STATE_IDLE
            val fight = fights.of(player)
            fights.leave(instanceId, player)
            if (fight != null && fight.players.isEmpty()) fight.throne?.showAllOps()
        }
        onInstanceEnded {
            fights.end(instanceId)
            summoning.removeAll { it.id == instanceId }
            firewall.forget(instanceId)
        }
    }

    private fun ScriptContext.registerContractUses() {
        val throneType = ServerCacheManager.getNpc(THRONE.asRSCM(RSCMType.NPC)) ?: return
        for (contract in YamaContract.entries) {
            val item = ServerCacheManager.getItem(contract.item.asRSCM(RSCMType.OBJ)) ?: continue
            onOpNpcU(throneType, item) {
                startDialogue(it.npc) {
                    contractOffer(contract) { piece ->
                        signContract(player, it.npc, contract, piece)
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.voiceTalk() {
        if (access.player.voiceProgress == VOICE_PROGRESS_NONE) {
            firstMeeting()
        } else {
            chatNpc(
                angry,
                "Proceed or begone, human. I will not bear your foul presence any longer than " +
                    "is necessary.",
            )
        }
        access.player.voiceProgress = VOICE_PROGRESS_ENTERED
        val enter =
            choice2("Enter Yama's domain.", true, "Stay.", false, title = "Select an option")
        if (!enter) {
            chatPlayer(neutral, "I think I'd rather stay here.")
            chatNpc(angry, "Weakness! Cowardice! Disgusting. Begone, human!")
            return
        }
        access.travel()
    }

    private suspend fun ProtectedAccess.travel() {
        if (manager.sessionForPlayer(player) != null) {
            mes(ALREADY_INSIDE)
            return
        }
        val owned = player.uuid?.let { manager.sessionOwnedBy(key, it) }
        val result =
            if (owned != null) {
                manager.join(player, owned, worldClock.cycle, forceAccess = true)
            } else {
                manager.create(player, key, buildSpec(), InstanceAccess.Friends, worldClock.cycle)
            }
        completeInstanceEntry(result)
    }

    private suspend fun ProtectedAccess.joinPartner() {
        if (manager.sessionForPlayer(player) != null) {
            mes(ALREADY_INSIDE)
            return
        }
        joinByName()
    }

    private suspend fun Dialogue.firstMeeting() {
        chatNpc(angry, "The arrogant human finally arrives...")
        chatPlayer(happy, "Upset that I disrupted your plans?")
        chatNpc(angry, "Disgusting. My master, the great demon Yama, wishes to speak with you.")
        chatPlayer(
            confused,
            "Yama wants to speak with me? How do you know that? You shouldn't even be able to " +
                "talk to him.",
        )
        chatNpc(
            angry,
            "My master wishes to have the satisfaction of explaining that to you himself. For " +
                "this, I envy him. I will make do with the idiotic expression you wear presently.",
        )
        chatPlayer(angry, "I planted the device! Your connection should be severed!")
        chatNpc(
            angry,
            "Silence, human! You will make no demands of me. Proceed to my master's domain and " +
                "speak with him yourself if you desire an answer.",
        )
    }

    private suspend fun ProtectedAccess.greet(player: Player, throne: Npc) {
        if (!canChallenge(player)) return
        val line = YamaLines.pick(YamaLineGroup.GREETING, deps.random) ?: return
        throne.say(line)
        startDialogue(throne) {
            chatNpcSpecificNp(THRONE_TITLE, THRONE, angry, line.replace(PAUSE_TAG, ""))
        }
        deps.worldQueues.add(GREETING_CLOSE_DELAY) { player.ifCloseChat(eventBus) }
    }

    private fun canChallenge(player: Player): Boolean {
        val session = manager.sessionForPlayer(player) ?: return false
        val idle = player.throneState == THRONE_STATE_IDLE && session !in summoning
        return idle && fights.of(player)?.ticking != true
    }

    private fun signContract(player: Player, throne: Npc, contract: YamaContract, piece: String?) {
        if (!canChallenge(player)) return
        if (player.invDel(player.inv, contract.item, 1).success.not()) return
        player.mes(
            "The <col=ff3045>${contract.title}</col> burns up and Yama breathes a contented sigh..."
        )
        contractOverlay.open(player, contract)
        throne.say(CONTRACT_SEND_OFF)
        challenge(player, throne, contract, piece, CONTRACT_SPAWN_DELAY)
    }

    private fun challenge(
        player: Player,
        throne: Npc,
        contract: YamaContract? = null,
        piece: String? = null,
        spawnDelay: Int = YAMA_SPAWN_DELAY,
    ) {
        val session = manager.sessionForPlayer(player) ?: return
        if (player.throneState != THRONE_STATE_IDLE || fights.of(player)?.ticking == true) return
        if (!summoning.add(session)) return
        val animDelay = spawnDelay - THRONE_TO_YAMA_DELAY
        val summonThrone = {
            throne.hideAllOps()
            throne.anim(THRONE_SUMMON_SEQ)
            throne.spotanim(THRONE_SUMMON_SPOTANIM)
            if (manager.sessionForPlayer(player) == session) {
                firewall.raise(session)
                deps.playerList
                    .filter { manager.sessionForPlayer(it) == session }
                    .forEach(music::play)
            }
        }
        if (animDelay <= 0) summonThrone() else deps.worldQueues.add(animDelay) { summonThrone() }
        deps.worldQueues.add(spawnDelay) {
            if (manager.sessionForPlayer(player) == session) {
                spawnYama(player, session, throne, contract, piece)
            }
        }
        deps.worldQueues.add(spawnDelay + THRONE_STATE_AFTER_SPAWN) {
            fights.of(player)?.players?.forEach { it.throneState = THRONE_STATE_FIGHT }
        }
        deps.worldQueues.add(spawnDelay + ATTACKABLE_AFTER_SPAWN) { summoning.remove(session) }
    }

    private fun spawnYama(
        player: Player,
        session: InstanceSession,
        throne: Npc,
        contract: YamaContract?,
        piece: String?,
    ) {
        val coords = manager.resolveCoord(session, YamaPhases.SPAWN) ?: return
        val type = ServerCacheManager.getNpc(YAMA_NPC_ID) ?: return
        val npc = Npc(type, coords)
        val layout = GlyphLayout.entries[deps.random.of(GlyphLayout.entries.size)]
        val participants = participants(player, session, contract)
        val fight = fights.create(session, participants, layout, contract)
        fight.startTick = deps.mapClock.cycle - THRONE_TO_YAMA_DELAY
        fight.throne = throne
        fight.chosenPiece = piece
        if (contract != null) fight.contractHolder = player
        if (contract != null && contract.maxHp != npc.baseHitpointsLvl) {
            npc.baseHitpointsLvl = contract.maxHp
            npc.hitpoints = contract.maxHp
        }
        val opening = layout.opening
        npc.respawnDir = Direction.North
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        npc.hideAllOps()
        deps.startEncounter(npc, YamaSpecs.forFight(opening, deps.random.of(2), contract))
        deps.worldQueues.add(GLYPH_DELAY) {
            if (manager.sessionForPlayer(player) != session) return@add
            fights.placeGlyphs(session, fight)
            fights.startTicking(session, fight, npc)
        }
        manager.registerSessionNpc(player, npc)
        npc.anim(YAMA_SUMMON_SEQ)
        npc.spotanim(YAMA_SUMMON_SPOTANIM)
        deps.suppressAttacks(npc, FIRST_ATTACK_SUPPRESS_TICKS)
        npc.apRequiresLineOfSight = false
        deps.worldQueues.add(ATTACKABLE_AFTER_SPAWN) {
            npc.showAllOps()
            npc.facePlayer(fight.target)
            npc.apPlayer2(fight.target, aiPlayerInteractions)
            fight.players.forEach { bossHpBar.onOpen(it, npc) }
        }
    }

    private fun participants(
        challenger: Player,
        session: InstanceSession,
        contract: YamaContract?,
    ): List<Player> {
        if (contract != null && !contract.partnerAllowed) return listOf(challenger)
        val present =
            deps.playerList.filter { manager.sessionForPlayer(it) == session && it.hitpoints > 0 }
        val players = if (challenger in present) present else present + challenger
        return players.sortedByDescending { it.uuid == session.owner }.take(MAX_PLAYERS)
    }

    private companion object {
        private const val SETTINGS_ROW = "dbrow.instance_yama"
        private const val ENTER_MESSAGE = "You enter Yama's Domain."
        private const val ALREADY_INSIDE = "You are already inside an instance."

        private const val VOICE_PROGRESS_NONE = 0
        private const val VOICE_PROGRESS_ENTERED = 2
        private const val THRONE_STATE_IDLE = 0
        private const val THRONE_STATE_FIGHT = 1

        private const val VOICE_OF_YAMA = "npc.voice_of_yama"
        private const val THRONE = "npc.yama_throne"

        private const val THRONE_SUMMON_SEQ = "seq.npc_yama_noncombat_summon01"
        private const val THRONE_SUMMON_SPOTANIM = "spotanim.vfx_yama_noncombat_summon_spotanim01"
        private const val YAMA_SUMMON_SEQ = "seq.npc_yama_summon01"
        private const val YAMA_SUMMON_SPOTANIM = "spotanim.vfx_yama_combat_summon_spotanim01"

        private const val YAMA_SPAWN_DELAY = 3
        private const val CONTRACT_SPAWN_DELAY = 2
        private const val DIALOGUE_SPAWN_DELAY = 2
        private const val THRONE_TO_YAMA_DELAY = 2
        private const val THRONE_STATE_AFTER_SPAWN = 2
        private const val ATTACKABLE_AFTER_SPAWN = 3
        private const val CONTRACT_SEND_OFF = "Good luck."
        private const val FIRST_ATTACK_SUPPRESS_TICKS = 8
        private const val GLYPH_DELAY = 5
        private const val MAX_PLAYERS = 2
        private const val GREETING_CLOSE_DELAY = 2
        private const val THRONE_TITLE = "Yama"
        private val PAUSE_TAG = Regex("<p=\\d+>")

        private val YAMA_NPC_ID = "npc.yama".asRSCM(RSCMType.NPC)
        private val THRONE_COORD = CoordGrid(1500, 10089)

        private val INSTANCE =
            InstanceArea.copyRegions(
                centerRegionId = 6045,
                npcSpawns = listOf(InstanceNpc(THRONE, THRONE_COORD)),
            )
    }
}
