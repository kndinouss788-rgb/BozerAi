export interface Env {
  DB: D1Database;
  AI?: Ai;

  GEMINI_API_KEY?: string;
  GROQ_API_KEY?: string;
  CEREBRAS_API_KEY?: string;
  SAMBANOVA_API_KEY?: string;
  MISTRAL_API_KEY?: string;
  NVIDIA_API_KEY?: string;
  OPENROUTER_API_KEY?: string;

  GEMINI_MODEL?: string;
  GROQ_MODEL?: string;
  CEREBRAS_MODEL?: string;
  SAMBANOVA_MODEL?: string;
  MISTRAL_MODEL?: string;
  NVIDIA_MODEL?: string;
  OPENROUTER_MODEL?: string;
  CLOUDFLARE_AI_MODEL?: string;

  GEMINI_DAILY_REQUEST_CAP?: string;
  GROQ_DAILY_REQUEST_CAP?: string;
  CEREBRAS_DAILY_REQUEST_CAP?: string;
  SAMBANOVA_DAILY_REQUEST_CAP?: string;
  MISTRAL_DAILY_REQUEST_CAP?: string;
  NVIDIA_DAILY_REQUEST_CAP?: string;
  OPENROUTER_DAILY_REQUEST_CAP?: string;
  CLOUDFLARE_AI_DAILY_REQUEST_CAP?: string;

  USER_DAILY_REQUEST_CAP?: string;
  PROVIDER_ORDER?: string;
}

type ChatMessage = { role: "system" | "user" | "assistant"; content: string };
type Provider = { name: string; cap: number; call: () => Promise<string> };

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

class ProviderError extends Error {
  constructor(readonly provider: string, readonly status: number) { super(provider); }
}

async function callOpenAICompatible(
  provider: string,
  endpoint: string,
  apiKey: string,
  model: string,
  messages: ChatMessage[],
  maxTokens: number,
  extraHeaders: Record<string, string> = {}
): Promise<string> {
  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${apiKey}`,
      ...extraHeaders
    },
    body: JSON.stringify({ model, messages, max_tokens: maxTokens, temperature: 0.7 }),
    signal: AbortSignal.timeout(7_000)
  });
  if (!response.ok) throw new ProviderError(provider, response.status);
  const data: any = await response.json();
  const text = data.choices?.[0]?.message?.content;
  const answer = typeof text === "string" ? text.trim() :
    Array.isArray(text) ? text.map((part: any) => part.text || "").join("").trim() : "";
  if (!answer) throw new ProviderError(provider, 502);
  return answer;
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
      signal: AbortSignal.timeout(7_000)
    }
  );
  if (!response.ok) throw new ProviderError("gemini", response.status);
  const data: any = await response.json();
  const text = data.candidates?.[0]?.content?.parts?.map((p: any) => p.text || "").join("").trim();
  if (!text) throw new ProviderError("gemini", 502);
  return text;
}

function buildProviders(env: Env, messages: ChatMessage[], maxTokens: number): Provider[] {
  const providers: Provider[] = [];
  const add = (name: string, key: string | undefined, capValue: string | undefined, call: () => Promise<string>) => {
    const cap = positiveInt(capValue, 0);
    if (key && cap > 0) providers.push({ name, cap, call });
  };

  add("gemini", env.GEMINI_API_KEY, env.GEMINI_DAILY_REQUEST_CAP,
    () => callGemini(env, messages, maxTokens));
  add("groq", env.GROQ_API_KEY, env.GROQ_DAILY_REQUEST_CAP,
    () => callOpenAICompatible("groq", "https://api.groq.com/openai/v1/chat/completions",
      env.GROQ_API_KEY!, env.GROQ_MODEL || "openai/gpt-oss-20b", messages, maxTokens));
  add("cerebras", env.CEREBRAS_API_KEY, env.CEREBRAS_DAILY_REQUEST_CAP,
    () => callOpenAICompatible("cerebras", "https://api.cerebras.ai/v1/chat/completions",
      env.CEREBRAS_API_KEY!, env.CEREBRAS_MODEL || "llama3.1-8b", messages, maxTokens));
  add("sambanova", env.SAMBANOVA_API_KEY, env.SAMBANOVA_DAILY_REQUEST_CAP,
    () => callOpenAICompatible("sambanova", "https://api.sambanova.ai/v1/chat/completions",
      env.SAMBANOVA_API_KEY!, env.SAMBANOVA_MODEL || "Meta-Llama-3.1-8B-Instruct", messages, maxTokens));
  add("mistral", env.MISTRAL_API_KEY, env.MISTRAL_DAILY_REQUEST_CAP,
    () => callOpenAICompatible("mistral", "https://api.mistral.ai/v1/chat/completions",
      env.MISTRAL_API_KEY!, env.MISTRAL_MODEL || "ministral-3b-latest", messages, maxTokens));
  add("nvidia", env.NVIDIA_API_KEY, env.NVIDIA_DAILY_REQUEST_CAP,
    () => callOpenAICompatible("nvidia", "https://integrate.api.nvidia.com/v1/chat/completions",
      env.NVIDIA_API_KEY!, env.NVIDIA_MODEL || "meta/llama-3.1-8b-instruct", messages, maxTokens));
  add("openrouter", env.OPENROUTER_API_KEY, env.OPENROUTER_DAILY_REQUEST_CAP,
    () => callOpenAICompatible("openrouter", "https://openrouter.ai/api/v1/chat/completions",
      env.OPENROUTER_API_KEY!, env.OPENROUTER_MODEL || "meta-llama/llama-3.1-8b-instruct:free",
      messages, maxTokens, { "HTTP-Referer": "https://bozerai.bozerpdg.workers.dev", "X-Title": "BOZER" }));

  const cloudflareCap = positiveInt(env.CLOUDFLARE_AI_DAILY_REQUEST_CAP, 0);
  if (env.AI && cloudflareCap > 0) {
    providers.push({
      name: "cloudflare-ai",
      cap: cloudflareCap,
      call: async () => {
        const result: any = await env.AI!.run(
          (env.CLOUDFLARE_AI_MODEL || "@cf/meta/llama-3.1-8b-instruct") as any,
          { messages, max_tokens: maxTokens }
        );
        const answer = typeof result?.response === "string" ? result.response.trim() :
          typeof result?.result?.response === "string" ? result.result.response.trim() : "";
        if (!answer) throw new ProviderError("cloudflare-ai", 502);
        return answer;
      }
    });
  }

  const order = (env.PROVIDER_ORDER || "gemini,groq,cerebras,sambanova,mistral,nvidia,openrouter,cloudflare-ai")
    .split(",").map(value => value.trim().toLowerCase()).filter(Boolean);
  const rank = new Map(order.map((name, index) => [name, index]));
  return providers.sort((a, b) => (rank.get(a.name) ?? 999) - (rank.get(b.name) ?? 999));
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
    if (!messages.some(item => item.role === "user")) return json({ error: "missing_user_message" }, 400);

    const maxTokens = Math.min(900, Math.max(64, positiveInt(String(body.max_tokens || ""), 700)));
    const day = new Date().toISOString().slice(0, 10);
    const userCap = positiveInt(env.USER_DAILY_REQUEST_CAP, 20);
    const userAllowed = await reserve(env.DB, day, "user", client, userCap);
    if (!userAllowed) return json({ error: "user_daily_limit_reached" }, 429);

    const providers = buildProviders(env, messages, maxTokens);
    if (providers.length === 0) return json({ error: "no_provider_configured" }, 503);

    const failures: string[] = [];
    for (const provider of providers) {
      const allowed = await reserve(env.DB, day, provider.name, "*", provider.cap);
      if (!allowed) {
        failures.push(provider.name + ":daily_cap");
        continue;
      }
      try {
        const reply = await provider.call();
        return json({ reply, provider: provider.name });
      } catch (error) {
        const status = error instanceof ProviderError ? error.status : 503;
        failures.push(provider.name + ":" + status);
        // Any provider-side failure triggers failover. Invalid credentials/model
        // are reported in server logs without exposing keys or details to clients.
        console.warn("BOZER provider failed", provider.name, status);
      }
    }
    return json({ error: "all_providers_unavailable", attempted: failures.map(item => item.split(":")[0]) }, 503);
  }
};
