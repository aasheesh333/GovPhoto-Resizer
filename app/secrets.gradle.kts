// Gradle script plugin: loads secrets.properties (gitignored) at repo root
// and exposes its keys as Gradle project properties (via extra), so that
// `project.findProperty("PRIVACY_URL")` in app/build.gradle.kts returns
// the value (or null when the file is absent — caller falls back to defaults).
//
// Apply in app/build.gradle.kts with:
//   apply(from = "secrets.gradle.kts")
//
// The file is intentionally a sibling of build.gradle.kts so the relative
// apply path is short; it reads secrets.properties from the repo root
// (two levels up).

import java.util.Properties

val secretsFile = rootProject.file("secrets.properties")
if (secretsFile.exists()) {
    val props = Properties().apply { secretsFile.inputStream().use { load(it) } }
    props.forEach { (k, v) ->
        // Expose as a Gradle project property. findProperty() reads these.
        extensions.extraProperties.set(k.toString(), v.toString())
    }
    logger.info("Loaded ${props.size} secrets from secrets.properties")
} else {
    logger.info("secrets.properties not found — using in-source fallback BuildConfig values")
}
