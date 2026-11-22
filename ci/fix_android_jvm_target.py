from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: fix_android_jvm_target.py <app-build.gradle.kts>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
if "sourceCompatibility = JavaVersion.VERSION_17" in text and 'jvmTarget = "17"' in text:
    print("Android Java/Kotlin bytecode targets already match at JVM 17.")
    raise SystemExit(0)

needle = "    buildFeatures { compose = true }"
replacement = """    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures { compose = true }"""

if needle not in text:
    raise SystemExit(f"Expected insertion point not found in {path}")

path.write_text(text.replace(needle, replacement, 1), encoding="utf-8")
print("Aligned Android Java and Kotlin bytecode targets to JVM 17.")
