dependencies {
    implementation(project(":core:identity-domain"))
    implementation(project(":core:peer-directory-domain"))
    implementation(project(":core:security-domain"))
    implementation(project(":core:security-contracts"))
    implementation(project(":storage:peer-directory-storage-contracts"))
    implementation(project(":core:peer-directory-contracts"))

    implementation(libs.coroutines.core)
    testImplementation(libs.bundles.testing)
}
