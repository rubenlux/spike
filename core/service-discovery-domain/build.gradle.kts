dependencies {
    implementation(project(":core:identity-domain"))
    implementation(project(":core:security-domain"))

    implementation(libs.coroutines.core)
    testImplementation(libs.bundles.testing)
}
