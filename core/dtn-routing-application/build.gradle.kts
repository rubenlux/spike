dependencies {
    implementation(project(":core:identity-domain"))
    implementation(project(":core:security-domain"))
    implementation(project(":core:security-contracts"))
    implementation(project(":core:dtn-routing-domain"))
    implementation(project(":storage:dtn-routing-storage-contracts"))
    implementation(project(":core:dtn-routing-contracts"))

    implementation(libs.coroutines.core)
    testImplementation(libs.bundles.testing)
}
