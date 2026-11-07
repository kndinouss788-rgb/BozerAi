# BOZER remote AI gateway

This Worker is the server-side provider router for BOZER. It supports Gemini Developer API first and Groq second. Provider API keys are stored as Cloudflare Worker secrets and are never embedded in the Android APK.

## Important capacity rule

This code does **not** promise 10,000 daily users. Free-tier limits are account-, model- and time-window-dependent. Configure the daily provider caps from the actual provider dashboards, leaving a safety margin. The Worker reserves quota before each provider attempt, so failed attempts are conservatively counted. Each provider also has a per-install daily limit.

## Setup

1. Create a Cloudflare Worker and a D1 database.
2. Apply `schema.sql` to the D1 database and bind it as `DB`.
3. Add secrets: `GEMINI_API_KEY` and `GROQ_API_KEY` (at least one).
4. Set variables from the quotas actually displayed in the provider accounts:
   - `GEMINI_DAILY_REQUEST_CAP`
   - `GROQ_DAILY_REQUEST_CAP`
   - `USER_DAILY_REQUEST_CAP` (default 20)
5. Optionally set `GEMINI_MODEL` and `GROQ_MODEL`.
6. Deploy the Worker and configure the Android Gradle property `BOZER_GATEWAY_URL` to its HTTPS endpoint.

Example Gradle property (keep the URL only; never put provider keys here):

```properties
BOZER_GATEWAY_URL=https://your-worker.your-subdomain.workers.dev
```

## Security before public launch

The current `X-Bozer-Client` identifier is a quota partition, **not authentication**. It can be forged or reset by a determined user. Before serving the public, add Firebase App Check or an authenticated BOZER account token and verify it in this Worker. Add abuse monitoring, an emergency global kill switch, and budget alerts. Never expose provider keys to the app.

## Capacity planning

10,000 daily active users is not the same as 10,000 requests. At 20 messages per user, BOZER would need 200,000 successful model calls/day. A free tier may be far below that. The Worker Free plan currently includes 100,000 Worker requests/day and D1 Free includes 100,000 rows written/day. This implementation performs two D1 counter upserts for each provider attempt (one per-install and one global), so its default combined provider-attempt ceiling is 45,000/day to leave a safety margin. The Worker rejects a configuration whose combined Gemini + Groq caps exceed that ceiling. Provider fallback attempts also consume budget. These Cloudflare limits are separate from provider limits and may change. The router prevents configured caps from being exceeded; it cannot create free inference capacity. When all configured providers reach their caps, it returns a controlled unavailable response and the Android app can fall back to its local model.
