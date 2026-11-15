# BOZER NEURAL — runtime integration contract

## Permanent core

BOZER NEURAL is the permanent identity, policy, memory, planning and action-verification core. Online/offline choices select only a language-generation backend; they must never switch off, replace, or redefine the BOZER core. Device actions remain controlled by explicit Android capabilities, permissions and confirmation gates.

## Honest state of the current implementation

- The shared Android policy/orchestration object is BozerNeuralBrain. It builds the BOZER identity prompt and local/remote context. It is software policy, not a trained foundation model.
- The from-scratch 1.116B-parameter configuration in bozer_neural/config_1b.py is an architecture definition. No production checkpoint has been trained, evaluated or integrated into the Android app.
- The Android app currently has an optional, pre-trained Qwen2.5-0.5B-Instruct GGUF auxiliary model for local text generation. It is not BOZER NEURAL and must never be labelled as BOZER's original trained brain.
- RemoteBrainClient talks to BOZER's server gateway for optional remote text generation. The gateway/provider is also an auxiliary generation route, not BOZER NEURAL's own weights.
- The current AUTO/ONLINE/OFFLINE setting selects the auxiliary generation route. The common BOZER policy layer remains applied to each route.

This distinction is deliberate: BOZER may use a clearly disclosed helper model while its original model is being built, but there is no hidden claim that Qwen or a remote provider is BOZER NEURAL.

## Integration boundary

1. BOZER NEURAL owns identity, memory policy, intent interpretation, planning policy, safety constraints and the final decision.
2. A local or remote language backend may propose text or candidate reasoning only. It cannot change permissions, erase safety rules, authorize actions, or report an unverified action as complete.
3. Android executes only typed, allow-listed actions after the required permission and confirmation checks.
4. Execution results return to BOZER's core for verification before BOZER confirms success to the user.
5. Auxiliary provider unavailability must be disclosed. No provider may silently replace BOZER NEURAL or become a required paid dependency.
6. When BOZER's own trained checkpoint becomes usable, it is integrated behind the same boundary and evaluated against held-out tests before becoming the default generation engine.

## Release gates for the original model

- Train from random initialization with a lawful and sufficiently large corpus.
- Preserve source provenance and isolate whole source documents between train, validation and test.
- Evaluate on unseen dialogue, reasoning, planning, tool-verification and safety examples.
- Verify model loading, tokenization, latency, memory, thermal behaviour and stability on representative Android devices.
- Keep the existing Android action confirmation and permission protections independent of model output.
- Only then change user-facing status from "under development" to "BOZER NEURAL ready".

The current 1B architecture, small synthetic corpus, and CI smoke tests are not evidence that the final model has learned useful conversation.
