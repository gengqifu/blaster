plugins {
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}

tasks.register("checkMainNoMock") {
    group = "verification"
    description = "Fail when production source sets contain mock/test/demo placeholder paths."
    doLast {
        val roots = listOf(
            file("core/src/main"),
            file("demo/src/main"),
        )
        val banned = listOf(
            "MockCloudMatchGateway",
            "TestLocalSongScanner",
            "content://demo/",
            "DemoAudio",
            "not wired",
            "audio-identity:",
            "mvp4-local",
        )
        val violations = mutableListOf<String>()
        roots.filter { it.exists() }.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "md" || it.extension == "xml") }
                .forEach { file ->
                    val text = file.readText()
                    banned.forEach { token ->
                        if (text.contains(token)) {
                            violations += "${file.relativeTo(projectDir).path}:$token"
                        }
                    }
                }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Production source contains banned mock/placeholder tokens:")
                    violations.sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }
}
