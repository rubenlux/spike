dependencies {
    implementation(project(":core:identity-domain"))
    implementation(project(":core:security-domain"))
    implementation(project(":core:security-contracts"))
    implementation(project(":storage:security-storage-contracts"))

    implementation(libs.coroutines.core)
    testImplementation(project(":core:security-testing"))
    testImplementation(libs.bundles.testing)
}
