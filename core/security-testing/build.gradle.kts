dependencies {
    implementation(project(":core:identity-domain"))
    implementation(project(":core:security-domain"))
    implementation(project(":core:security-contracts"))
    implementation(project(":storage:security-storage-contracts"))
    implementation(project(":core:security-application"))

    implementation(libs.coroutines.core)
    testImplementation(libs.bundles.testing)
}
