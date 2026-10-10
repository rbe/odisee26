import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("com.ullink.msbuild") version "5.0"
}

tasks.register("buildSolution") {
    group = "Odisee"
    description = "Build the VB.NET client solution (Windows only)"
    onlyIf { Os.isFamily(Os.FAMILY_WINDOWS) }
}

tasks.named<com.ullink.Msbuild>("msbuild") {
    solutionFile = "src/vb/OdiseeClient.sln"
    verbosity = "detailed"
    projectName = project.name
    targets = listOf("Clean", "Rebuild")
    version = "14.0"
    msbuildDir = "/Library/Frameworks/Mono.framework/Versions/6.0.0/lib/mono/xbuild/14.0/bin"
    destinationDir = "build/msbuild/bin"
}

tasks.named("assemble") {
    dependsOn("buildSolution")
}
