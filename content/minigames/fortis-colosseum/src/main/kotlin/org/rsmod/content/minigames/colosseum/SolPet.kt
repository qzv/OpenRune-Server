package org.rsmod.content.minigames.colosseum

import org.rsmod.api.table.PetDropsRow

internal object SolPet {
    private val drop by lazy { PetDropsRow.getRow("dbrow.pet_drop_colosseum_sol_p1") }

    val obj: String
        get() = drop.pet.internalName

    val rate: Int
        get() = drop.rate
}
