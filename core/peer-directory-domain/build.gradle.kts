dependencies {
    implementation(project(":core:identity-domain"))
    implementation(libs.coroutines.core)

    testImplementation(libs.bundles.testing)
}
