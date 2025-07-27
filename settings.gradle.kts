rootProject.name = "CloudStreamPlugins"
include(":CuevanaProvider")

// Disable unused plugins for now
val disabled = listOf<String>(
    // Add any providers you want to disable here
)

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && 
        File(dir, "build.gradle.kts").exists() && 
        dir.name != "CuevanaProvider") { // Already included above
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
