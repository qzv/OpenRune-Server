plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.bossHpBarPlugin)
    implementation(projects.api.bosses)
    implementation(projects.api.config)
    implementation(projects.api.death)
    implementation(projects.api.instances)
    implementation(projects.api.music)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.repo)
}
