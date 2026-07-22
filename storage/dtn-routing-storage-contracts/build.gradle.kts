dependencies {
    implementation(project(":core:identity-domain"))
    implementation(project(":core:dtn-routing-domain"))

    implementation(libs.coroutines.core)
    testImplementation(libs.bundles.testing)
}
