dependencies {
    implementation(project(":core:identity-domain"))
    implementation(project(":core:dtn-routing-domain"))
    implementation(project(":storage:dtn-routing-storage-contracts"))

    implementation(libs.coroutines.core)
    testImplementation(libs.bundles.testing)
}
