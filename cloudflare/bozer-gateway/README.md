# BOZER remote AI gateway

This Cloudflare Worker is BOZER's remote inference gateway. Provider API keys stay in Cloudflare Worker secrets and are never packaged in the Android APK.

## Provider routing

The router supports these adapters, in the order configured by `PROVIDER_ORDER`:

- Gemini Developer API
- Groq
- Cerebras
- SambaNova
- Mistral
- NVIDIA NIM
- OpenRouter
- Cloudflare Workers AI (requires the `AI` binding)

A provider is eligible only when its required secret (or Cloudflare AI binding) and a positive `*_DAILY_REQUEST_CAP` are configured. The router tries eligible providers in order. A failed request or exhausted provider cap moves to the next eligible provider. Provider attempts are reserved before the upstream call, so failed attempts also consume the configured attempt budget.

## Configuration

Required Cloudflare bindings:
- D1 database binding `DB`, initialized with `schema.sql`.
- Workers AI binding `AI` if Cloudflare Workers AI is to be used as a fallback.

Secrets (add only the providers you actually use):
- `GEMINI_API_KEY`
- `GROQ_API_KEY`
- `CEREBRAS_API_KEY`
- `SAMBANOVA_API_KEY`
- `MISTRAL_API_KEY`
- `NVIDIA_API_KEY`
- `OPENROUTER_API_KEY`

Variables:
- `PROVIDER_ORDER`: comma-separated provider order.
- `USER_DAILY_REQUEST_CAP`: daily cap per installation identifier.
- `GEMINI_MODEL`, `GROQ_MODEL`, `CEREBRAS_MODEL`, `SAMBANOVA_MODEL`, `MISTRAL_MODEL`, `NVIDIA_MODEL`, `OPENROUTER_MODEL`, `CLOUDFLARE_AI_MODEL`: optional model overrides.
- One positive `<PROVIDER>_DAILY_REQUEST_CAP` for each provider you enable.

Do not copy provider keys into Android, source files, issue comments, or chat messages.

## Android connection

The Android client calls the HTTPS Worker endpoint using `BOZER_GATEWAY_URL`. The client sends only a random installation identifier in `X-Bozer-Client`; it does not contain provider credentials.

## Important limitations before public launch

- `X-Bozer-Client` is a quota partition, **not authentication**. It can be forged or reset. Add Firebase App Check or authenticated BOZER account tokens and verify them in the Worker before public launch.
- This router uses configured daily attempt caps; it does not discover each provider's live remaining quota or guarantee free capacity.
- Provider quotas differ by model, account, tier, region, and time window. Verify each account's current dashboard limits and commercial terms.
- Every provider attempt writes quota counters to D1. Account for D1 write limits and Workers request/CPU limits when planning capacity.
- A chain of several unavailable providers can be slow. Provider calls currently have a 7-second timeout each; the Android client timeout is longer to allow fallback.
- Add abuse monitoring, a global emergency disable switch, and quota/billing alerts before public launch.

10,000 daily users or any specific daily response volume is **not guaranteed** by combining free tiers. Capacity must be load-tested against actual account quotas and Cloudflare plan limits. When all remote providers are unavailable, Android attempts its local model; that local fallback must also be tested on real target devices.
