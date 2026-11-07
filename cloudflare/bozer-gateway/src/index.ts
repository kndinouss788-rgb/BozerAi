export interface Env {
  DB: D1Database;
  GEMINI_API_KEY?: string;
  GROQ_API_KEY?: string;
  GEMINI_MODEL?: string;
  GROQ_MODEL?: string;
  GEMINI_DAILY_REQUEST_CAP?: string;
  GROQ_DAILY_REQUEST_CAP?: string;
  USER_DAILY_REQUEST_CAP?: string;
  DAILY_PROVIDER_ATTEMPT_BUDGET?: string;
}

type ChatMessage = { role: "system" | "user" | "assistant"; content: string };

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
  });
}

function positiveInt(value: string | undefined, fallback: number): number {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : fallback;
}

async function reserve(db: D1Database, day: string, provider: string, client: string, cap: number): Promise<boolean> {
  const result = await db.prepare(
    `INSERT INTO quota_usage (day, provider, client_id, requests)
     VALUES (?, ?, ?, 1)
     ON CONFLICT(day, provider, client_id)
     DO UPDATE SET requests = requests + 1
     WHERE requests < ?
     RETURNING requests`
  ).bind(day, provider, client, cap).first<{ requests: number }>();
  return result !== null;
}

async function callGemini(env: Env, messages: ChatMessage[], maxTokens: number): Promise<string> {
  const model = env.GEMINI_MODEL || "gemini-2.5-flash";
  const system = messages.find(m => m.role === "system")?.content || "You are BOZER, a helpful assistant.";
  const contents = messages.filter(m => m.role !== "system").map(m => ({
    role: m.role === "assistant" ? "model" : "user",
    parts: [{ text: m.content }]
  }));
  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`,
    {
      method: "POST",
      headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY! },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: system }] },
        contents,
        generationConfig: { maxOutputTokens: maxTokens, temperature: 0.7 }
      }),
      signal: AbortSignal.timeout(35_000)
    }
  );
  if (!response.ok) throw new ProviderError("gemini", response.status);
  const data: any = await response.json();
  const text = data.candidates?.[0]?.content?.parts?.map((p: any) => p.text || "").join("").trim();
  if (!text) throw new ProviderError("gemini", 502);
  return text;
}

async function callGroq(env: Env, messages: ChatMessage[], maxTokens: number): Promise<string> {
  const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${env.GROQ_API_KEY!}` },
    body: JSON.stringify({
      model: env.GROQ_MODEL || "openai/gpt-oss-20b",
      messages,
      max_tokens: maxTokens,
      temperature: 0.7
    }),
    signal: AbortSignal.timeout(35_000)
  });
  if (!response.ok) throw new ProviderError("groq", response.status);
  const data: any = await response.json();
  const text = data.choices?.[0]?.message?.content?.trim();
  if (!text) throw new ProviderError("groq", 502);
  return text;
}

class ProviderError extends Error {
  constructor(readonly provider: string, readonly status: number) { super(provider); }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: {
        "access-control-allow-origin": "*",
        "access-control-allow-methods": "POST, OPTIONS",
        "access-control-allow-headers": "content-type, x-bozer-client"
      }});
    }
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);

    const client = request.headers.get("X-Bozer-Client") || "";
    if (client.length < 16 || client.length > 128) return json({ error: "invalid_client_id" }, 400);

    let body: any;
    try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
    if (!Array.isArray(body.messages) || body.messages.length < 1 || body.messages.length > 13) {
      return json({ error: "invalid_messages" }, 400);
    }
    const messages: ChatMessage[] = [];
    for (const item of body.messages) {
      if (!["system", "user", "assistant"].includes(item?.role) ||
          typeof item?.content !== "string" || item.content.length > 6000) {
        return json({ error: "invalid_message" }, 400);
      }
      messages.push({ role: item.role, content: item.content });
    }
    const totalChars = messages.reduce((sum, item) => sum + item.content.length, 0);
    if (totalChars > 24000) return json({ error: "context_too_large" }, 413);
    const maxTokens = Math.min(900, Math.max(64, positiveInt(String(body.max_tokens || ""), 700)));
    const day = new Date().toISOString().slice(0, 10);
    const userCap = positiveInt(env.USER_DAILY_REQUEST_CAP, 20);

    const providers = [
      { name: "gemini", key: env.GEMINI_API_KEY, cap: positiveInt(env.GEMINI_DAILY_REQUEST_CAP, 0),
        call: () => callGemini(env, messages, maxTokens) },
      { name: "groq", key: env.GROQ_API_KEY, cap: positiveInt(env.GROQ_DAILY_REQUEST_CAP, 0),
        call: () => callGroq(env, messages, maxTokens) }
    ].filter(p => !!p.key && p.cap > 0);

    if (providers.length === 0) return json({ error: "no_provider_configured" }, 503);
    const attemptBudget = positiveInt(env.DAILY_PROVIDER_ATTEMPT_BUDGET, 45_000);
    const configuredDailyTotal = providers.reduce((sum, provider) => sum + provider.cap, 0);
    if (configuredDailyTotal > attemptBudget) {
      return json({ error: "provider_caps_exceed_gateway_safety_budget", max_daily_provider_attempts: attemptBudget }, 503);
    }

    const userAllowed = await reserve(env.DB, day, "user", client, userCap);
    if (!userAllowed) return json({ error: "user_daily_limit_reached" }, 429);
    const failures: string[] = [];
    for (const provider of providers) {
      const globalAllowed = await reserve(env.DB, day, provider.name, "*", provider.cap);
      if (!globalAllowed) {
        failures.push(provider.name + ":daily_cap");
        continue;
      }
      try {
        const reply = await provider.call();
        return json({ reply, provider: provider.name });
      } catch (error) {
        const status = error instanceof ProviderError ? error.status : 503;
        failures.push(provider.name + ":" + status);
        // Fall back only for quota, capacity and transient provider failures.
        if (!(status === 429 || status === 408 || status >= 500)) {
          return json({ error: "provider_request_rejected" }, 502);
        }
      }
    }
    return json({ error: "all_providers_unavailable", reasons: failures }, 503);
  }
};
