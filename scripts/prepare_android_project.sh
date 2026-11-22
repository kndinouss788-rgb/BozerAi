#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${1:?Usage: prepare_android_project.sh /path/to/llama.cpp/examples/llama.android}"
MODEL_URL="https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true"
MODEL_FILE="$APP_DIR/app/src/main/assets/bozer-model.gguf"
MODEL_LICENSE="$APP_DIR/app/src/main/assets/BOZER_MODEL_LICENSE.txt"

test -f "$APP_DIR/app/src/main/java/com/example/llama/MainActivity.kt"
mkdir -p "$(dirname "$MODEL_FILE")"

echo "Downloading Qwen2.5-1.5B-Instruct Q4_K_M (~1.12 GB) for inclusion in the APK..."
curl --fail --location --retry 5 --retry-delay 3 --continue-at - "$MODEL_URL" --output "$MODEL_FILE"
test "$(stat -c%s "$MODEL_FILE")" -gt 900000000
curl --fail --location --retry 3 "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/LICENSE" --output "$MODEL_LICENSE"

python3 - "$APP_DIR" <<'PY'
from pathlib import Path
import re
import sys

app = Path(sys.argv[1])
main = app / "app/src/main/java/com/example/llama/MainActivity.kt"
source = main.read_text(encoding="utf-8")

pattern = r'        lifecycleScope\.launch\(Dispatchers\.Default\)\s*\{\s*engine = AiChat\.getInferenceEngine\(applicationContext\)\s*\}'
replacement = """        userInputEt.isEnabled = false
        userActionFab.isEnabled = false
        userInputEt.hint = "Préparation du cerveau de BOZER..."

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                engine = AiChat.getInferenceEngine(applicationContext)
                val bundledModel = withContext(Dispatchers.IO) {
                    val file = File(ensureModelsDirectory(), "bozer-model.gguf")
                    if (!file.exists() || file.length() < 900_000_000L) {
                        assets.open("bozer-model.gguf").use { input ->
                            FileOutputStream(file).use { output -> input.copyTo(output) }
                        }
                    }
                    file
                }
                engine.loadModel(bundledModel.absolutePath)
                withContext(Dispatchers.Main) {
                    isModelReady = true
                    ggufTv.text = "BOZER est prêt — cerveau local chargé"
                    userInputEt.hint = "Écris à BOZER..."
                    userInputEt.isEnabled = true
                    userActionFab.setImageResource(R.drawable.outline_send_24)
                    userActionFab.isEnabled = true
                }
            } catch (error: Exception) {
                Log.e(TAG, "Impossible de charger le cerveau embarqué", error)
                withContext(Dispatchers.Main) {
                    ggufTv.text = "Le cerveau de BOZER n'a pas pu démarrer : ${error.message}"
                    userInputEt.hint = "Chargement du cerveau impossible"
                    userInputEt.isEnabled = false
                    userActionFab.isEnabled = false
                }
            }
        }"""
source, count = re.subn(pattern, replacement, source, count=1)
if count != 1:
    raise SystemExit("Upstream MainActivity initialization changed; refusing to patch an unknown source.")

source = source.replace(
    'engine.sendUserPrompt(userMsg)',
    'engine.sendUserPrompt("Tu es BOZER, un assistant IA francophone naturel, direct, respectueux et utile. Réponds dans la langue de l’utilisateur, clairement et sans inventer.\\n\\nUtilisateur : " + userMsg)',
    1
)
source = source.replace('        engine.destroy()', '        if (::engine.isInitialized) engine.destroy()', 1)
main.write_text(source, encoding="utf-8")

gradle = app / "app/build.gradle.kts"
g = gradle.read_text(encoding="utf-8")
g = g.replace('applicationId = "com.example.llama.aichat"', 'applicationId = "com.bozer.ai"', 1)
if 'androidResources {' not in g:
    g = g.replace('android {', 'android {\n    androidResources { noCompress += "gguf" }', 1)
gradle.write_text(g, encoding="utf-8")

strings = app / "app/src/main/res/values/strings.xml"
if strings.exists():
    s = strings.read_text(encoding="utf-8").replace("AI Chat Sample", "BOZER").replace("AI Chat", "BOZER")
    strings.write_text(s, encoding="utf-8")

print("BOZER branding and bundled-model auto-load patch applied.")
PY

echo "Embedded model ready: $(du -h "$MODEL_FILE" | cut -f1)"
