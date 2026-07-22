// security-domain: depends only on identity-domain leaf module

dependencies {
    implementation(project(":core:identity-domain"))
    testImplementation(libs.bundles.testing)
}
