#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>
#include <thread>
#include "llama.h"

namespace {
std::mutex engine_mutex;
llama_model * model = nullptr;
bool backend_initialized = false;

void throw_java(JNIEnv * env, const char * message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) env->ThrowNew(type, message);
}

std::string from_jstring(JNIEnv * env, jstring value) {
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ai_bozer_app_LocalLlamaEngine_nativeLoadModel(
        JNIEnv * env, jobject, jstring path, jint) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    if (model) return JNI_TRUE;

    const std::string model_path = from_jstring(env, path);
    if (model_path.empty()) {
        throw_java(env, "Chemin du modèle vide.");
        return JNI_FALSE;
    }

    if (!backend_initialized) {
        llama_backend_init();
        backend_initialized = true;
    }

    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;
    model = llama_model_load_from_file(model_path.c_str(), params);
    if (!model) {
        throw_java(env, "llama.cpp n'a pas pu ouvrir le fichier GGUF.");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_ai_bozer_app_LocalLlamaEngine_nativeGenerate(
        JNIEnv * env, jobject, jstring input, jint max_tokens) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    if (!model) {
        throw_java(env, "Le modèle n'est pas chargé.");
        return nullptr;
    }

    const std::string user_prompt = from_jstring(env, input);
    const std::string prompt =
        "<|im_start|>system\nTu es BOZER, un assistant francophone naturel, chaleureux, direct et honnête. "
        "Réponds clairement. N'invente jamais une action que tu n'as pas exécutée.<|im_end|>\n"
        "<|im_start|>user\n" + user_prompt + "<|im_end|>\n"
        "<|im_start|>assistant\n";

    const llama_vocab * vocab = llama_model_get_vocab(model);
    const int32_t needed = -llama_tokenize(vocab, prompt.c_str(),
        static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
    if (needed <= 0 || needed > 1792) {
        throw_java(env, "La conversation est trop longue pour le contexte local.");
        return nullptr;
    }

    std::vector<llama_token> tokens(static_cast<size_t>(needed));
    const int32_t token_count = llama_tokenize(vocab, prompt.c_str(),
        static_cast<int32_t>(prompt.size()), tokens.data(), needed, true, true);
    if (token_count < 0) {
        throw_java(env, "Échec de la tokenisation du message.");
        return nullptr;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = 2048;
    context_params.n_batch = 2048;
    const int threads = std::max(2, std::min(4, static_cast<int>(std::thread::hardware_concurrency())));
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    llama_context * ctx = llama_init_from_model(model, context_params);
    if (!ctx) {
        throw_java(env, "Mémoire insuffisante pour préparer le contexte du modèle.");
        return nullptr;
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string output;
    llama_batch batch = llama_batch_get_one(tokens.data(), token_count);
    int32_t generated = 0;
    // llama_batch_get_one keeps a pointer to the supplied token. Keep this
    // token alive across loop iterations instead of pointing at a block local.
    llama_token next_token = 0;
    while (generated < std::min(static_cast<int>(max_tokens), 256)) {
        if (llama_decode(ctx, batch) != 0) break;
        next_token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, next_token)) break;

        char piece[256];
        const int32_t length = llama_token_to_piece(vocab, next_token, piece, sizeof(piece), 0, false);
        if (length > 0) output.append(piece, static_cast<size_t>(length));
        batch = llama_batch_get_one(&next_token, 1);
        ++generated;
    }

    llama_sampler_free(sampler);
    llama_free(ctx);
    if (output.empty()) {
        throw_java(env, "Le moteur local n'a pas produit de texte.");
        return nullptr;
    }
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_ai_bozer_app_LocalLlamaEngine_nativeRelease(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    if (model) {
        llama_model_free(model);
        model = nullptr;
    }
    if (backend_initialized) {
        llama_backend_free();
        backend_initialized = false;
    }
}
