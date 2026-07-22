dependencies {
    implementation(project(":core:identity-domain"))
    implementation(project(":core:security-domain"))
    implementation(project(":core:security-contracts"))
    implementation(project(":core:peer-directory-domain"))
    implementation(project(":core:peer-directory-contracts"))
    implementation(project(":core:service-discovery-domain"))
    implementation(project(":storage:service-discovery-storage-contracts"))
    implementation(project(":core:service-discovery-contracts"))

    implementation(libs.coroutines.core)
    testImplementation(libs.bundles.testing)
}
