package org.rsmod.api.player.hook

import org.rsmod.api.player.protect.ProtectedAccess

public fun interface PlayerLogoutRequestHook {
    /** @return `true` when the request was consumed and the default logout must not run. */
    public suspend fun intercept(access: ProtectedAccess): Boolean
}
