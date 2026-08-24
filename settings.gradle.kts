plugins {
    id("com.gradleup.nmcp.settings") version "1.4.4"
}

rootProject.name = "clarity"

if (file("../clarity-protobuf").exists())
    includeBuild("../clarity-protobuf")

nmcpSettings {
    centralPortal {
        username = providers.gradleProperty("mavenCentralUsername").orNull
        password = providers.gradleProperty("mavenCentralPassword").orNull
    }
}
