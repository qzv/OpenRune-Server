plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.db)
    implementation(projects.api.dbGateway)
    implementation(projects.api.net)
    implementation(projects.api.pluginCommons)
}
