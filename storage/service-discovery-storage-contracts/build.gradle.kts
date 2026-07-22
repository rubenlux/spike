dependencies {
    implementation(project(":core:identity-domain"))
    implementation(project(":core:security-domain"))
    implementation(project(":core:service-discovery-domain"))

    implementation(libs.coroutines.core)
}
