plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core:discovery-contracts"))
    implementation(project(":core:discovery-domain"))
    implementation(project(":core:encounter-domain"))
}
