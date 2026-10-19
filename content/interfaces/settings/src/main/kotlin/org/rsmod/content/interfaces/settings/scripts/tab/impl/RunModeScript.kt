package org.rsmod.content.interfaces.settings.scripts.tab.impl

import jakarta.inject.Inject
import org.rsmod.api.player.hook.PlayerRunValidateHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RunModeScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val protectedAccess: ProtectedAccessLauncher,
    private val runHooks: Set<PlayerRunValidateHook>,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOverlayButton("component.orbs:runbutton") { player.selectRunToggle() }
        onIfOverlayButton("component.settings_side:runmode") { player.selectRunToggle() }
        onPlayerQueue("queue.runmode_toggle") { validateAndToggleRun() }
    }

    private fun Player.selectRunToggle() {
        if ("queue.runmode_toggle" in queueList) {
            return
        }
        ifClose(eventBus)
        val toggled = protectedAccess.launch(this) { validateAndToggleRun() }
        if (!toggled) {
            strongQueue("queue.runmode_toggle", 1)
        }
    }

    private fun ProtectedAccess.validateAndToggleRun() {
        if (player.varMoveSpeed != MoveSpeed.Run) {
            val denial = runHooks.firstNotNullOfOrNull { it.validate(player) }
            if (denial != null) {
                mes(denial)
                player.resyncVar("varp.option_run")
                return
            }
        }
        toggleRun()
    }
}
