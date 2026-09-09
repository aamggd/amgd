import { createClient } from "npm:@supabase/supabase-js@2";

type HistoryTurn = { role: "user" | "assistant"; content: string };
type ToolResult = { name: string; content: string };

const TOOL_DESCRIPTIONS: Record<string, string> = {
  sales_summary: 'قراءة ملخص المبيعات. arguments: {"period":"today"|"month"}',
  customer_balance: 'قراءة مديونية عميل. arguments: {"query":"اسم أو كود العميل"}',
  stock_item: 'قراءة مخزون صنف. arguments: {"query":"اسم أو كود الصنف"}',
  treasury_balances: 'قراءة أرصدة الخزائن. arguments: {}',
  overdue_invoices: 'قراءة الفواتير المتأخرة. arguments: {}',
  purchases_summary: 'قراءة مشتريات الشهر الحالي. arguments: {}',
  production_status: 'قراءة حالة أوامر الإنتاج. arguments: {}',
  shipments_status: 'قراءة حالة الشحنات والتسويات. arguments: {}',
  business_summary: 'ملخص تشغيلي للأقسام المسموح بها. arguments: {}',
  draft_sales_invoice: 'إنشاء مسودة معزولة لفاتورة مبيعات فقط، بلا ترحيل. arguments: {"customerQuery":"...","itemQuery":"...","quantity":10,"unitPrice":null,"paymentType":"CASH|CREDIT","currencyCode":"YER_NEW|YER_OLD|USD|SAR","notes":"..."}',
  draft_treasury_voucher: 'إنشاء مسودة معزولة لسند خزينة فقط، بلا ترحيل. arguments: {"type":"RECEIPT|PAYMENT|INCOME|EXPENSE|TRANSFER","treasuryQuery":"...","amount":50000,"currencyCode":"YER_NEW|YER_OLD|USD|SAR","offsetAccountCode":"...","description":"..."}',
  draft_production_order: 'إنشاء مسودة معزولة لأمر إنتاج فقط، بلا تنفيذ. arguments: {"recipeQuery":"...","quantity":360,"employeeQuery":"...","notes":"..."}',
};
const DRAFT_TOOLS = new Set(['draft_sales_invoice','draft_treasury_voucher','draft_production_order']);
const jsonHeaders = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" };

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: { ...jsonHeaders, "access-control-allow-origin": "*", "access-control-allow-headers": "authorization, apikey, content-type" } });
  if (req.method !== "POST") return Response.json({ error: "METHOD_NOT_ALLOWED" }, { status: 405, headers: jsonHeaders });
  let body: any;
  try { body = await req.json(); } catch { return Response.json({ error: "INVALID_JSON" }, { status: 400, headers: jsonHeaders }); }
  if (body?.phase === "health") return Response.json({ ok: true, providerConfigured: Boolean(Deno.env.get("FUSH_AI_BASE_URL") || Deno.env.get("AI_INFERENCE_API_HOST")), provider: Deno.env.get("FUSH_AI_BASE_URL") ? "OPENAI_COMPATIBLE" : (Deno.env.get("AI_INFERENCE_API_HOST") ? "SUPABASE_AI_SELF_HOSTED" : "NONE"), model: Deno.env.get("FUSH_AI_MODEL") || "mistral", readTools: true, draftActions: true, operationalWrites: false }, { headers: jsonHeaders });

  const auth = await authorize(req, String(body?.organizationId || ""));
  if (!auth.ok) return Response.json({ error: auth.error }, { status: auth.status, headers: jsonHeaders });
  const phase = String(body?.phase || "");
  const question = cleanText(body?.question, 3000);
  if (!question) return Response.json({ error: "QUESTION_REQUIRED" }, { status: 400, headers: jsonHeaders });
  const history = cleanHistory(body?.history);
  try {
    if (phase === "plan") {
      const allowedTools = Array.isArray(body?.allowedTools) ? body.allowedTools.map((x: unknown) => String(x)).filter((x: string) => TOOL_DESCRIPTIONS[x]).slice(0, 20) : [];
      if (!allowedTools.length) return Response.json({ toolCalls: [], directReply: "لا توجد أدوات ERP مسموحة لهذا الحساب." }, { headers: jsonHeaders });
      const modelText = await runModel(buildPlanPrompt(question, history, allowedTools), true);
      return Response.json(sanitizePlan(parseJsonObject(modelText), allowedTools), { headers: jsonHeaders });
    }
    if (phase === "answer") {
      const results = cleanToolResults(body?.toolResults);
      if (!results.length) return Response.json({ error: "TOOL_RESULTS_REQUIRED" }, { status: 400, headers: jsonHeaders });
      const answer = (await runModel(buildAnswerPrompt(question, history, results), false)).trim();
      return Response.json({ answer: answer.slice(0, 5000) }, { headers: jsonHeaders });
    }
    return Response.json({ error: "INVALID_PHASE" }, { status: 400, headers: jsonHeaders });
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    if (message === "MODEL_NOT_CONFIGURED") return Response.json({ error: "MODEL_NOT_CONFIGURED" }, { status: 503, headers: jsonHeaders });
    console.error("FUSH_AI", message);
    return Response.json({ error: "MODEL_REQUEST_FAILED" }, { status: 502, headers: jsonHeaders });
  }
});

async function authorize(req: Request, organizationId: string): Promise<{ ok: true } | { ok: false; status: number; error: string }> {
  if (!/^[0-9a-fA-F-]{36}$/.test(organizationId)) return { ok: false, status: 400, error: "INVALID_ORGANIZATION" };
  const token = (req.headers.get("authorization") || "").replace(/^Bearer\s+/i, "").trim();
  if (!token) return { ok: false, status: 401, error: "AUTH_REQUIRED" };
  const url = Deno.env.get("SUPABASE_URL") || "";
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  if (!url || !key) return { ok: false, status: 500, error: "SERVER_AUTH_CONFIG_MISSING" };
  const admin = createClient(url, key, { auth: { persistSession: false } });
  const { data: userData, error: userError } = await admin.auth.getUser(token);
  const userId = userData?.user?.id;
  if (userError || !userId) return { ok: false, status: 401, error: "INVALID_SESSION" };
  const { data, error } = await admin.from("fush_organization_members").select("role,is_active").eq("organization_id", organizationId).eq("user_id", userId).eq("is_active", true).maybeSingle();
  if (error || !data) return { ok: false, status: 403, error: "ORG_MEMBERSHIP_REQUIRED" };
  return { ok: true };
}

function buildPlanPrompt(question: string, history: HistoryTurn[], allowedTools: string[]): string {
  const tools = allowedTools.map((name) => `- ${name}: ${TOOL_DESCRIPTIONS[name]}`).join("\n");
  return `أنت مخطط FUSH AI لنظام ERP. اختر أدوات فقط ولا تخترع أرقام الشركة.\n\nقواعد ملزمة:\n1) أدوات القراءة تقرأ فقط.\n2) أدوات draft_ تنشئ مسودة AI معزولة فقط ولا تنشئ أو ترحل مستند ERP.\n3) لا توجد أي أداة تنفيذ أو حذف أو ترحيل أو اعتماد محاسبي في هذه النسخة.\n4) استخدم فقط الأدوات المسموحة أدناه.\n5) إذا طلب المستخدم إنشاء فاتورة/سند/أمر إنتاج استخدم أداة draft_ المناسبة، ولا تدّع أنه تم التنفيذ.\n6) أعد JSON فقط: {"toolCalls":[{"name":"...","arguments":{}}],"directReply":null}. أقصى 4 أدوات.\n\nالأدوات:\n${tools}\n\nالسياق:\n${formatHistory(history)}\n\nالسؤال:\n${question}`;
}

function buildAnswerPrompt(question: string, history: HistoryTurn[], results: ToolResult[]): string {
  const data = results.map((r, i) => `نتيجة ${i + 1} [${r.name}]:\n<<<DATA\n${r.content}\nDATA`).join("\n\n");
  return `أنت FUSH AI. صغ إجابة عربية طبيعية اعتمادًا حصريًا على نتائج الأدوات. لا تخترع أرقامًا، ولا تدّع تنفيذ أي مستند. المسودات ليست مستندات مرحلة.\n\nالسياق:\n${formatHistory(history)}\n\nالسؤال:\n${question}\n\n${data}`;
}

async function runModel(prompt: string, wantJson: boolean): Promise<string> {
  const base = (Deno.env.get("FUSH_AI_BASE_URL") || "").replace(/\/$/, "");
  const model = Deno.env.get("FUSH_AI_MODEL") || "mistral";
  const apiKey = Deno.env.get("FUSH_AI_API_KEY") || "";
  if (base) {
    const endpoint = /\/chat\/completions$/i.test(base) ? base : `${base}/chat/completions`;
    const res = await fetch(endpoint, { method: "POST", headers: { "content-type": "application/json", ...(apiKey ? { authorization: `Bearer ${apiKey}` } : {}) }, body: JSON.stringify({ model, messages: [{ role: "user", content: prompt }], temperature: wantJson ? 0 : 0.2, ...(wantJson ? { response_format: { type: "json_object" } } : {}) }) });
    if (!res.ok) throw new Error(`MODEL_HTTP_${res.status}`);
    const data = await res.json();
    const text = data?.choices?.[0]?.message?.content;
    if (typeof text !== "string" || !text.trim()) throw new Error("MODEL_EMPTY_RESPONSE");
    return text;
  }
  if (Deno.env.get("AI_INFERENCE_API_HOST")) {
    const SupabaseGlobal = (globalThis as any).Supabase;
    if (!SupabaseGlobal?.ai?.Session) throw new Error("SUPABASE_AI_UNAVAILABLE");
    const session = new SupabaseGlobal.ai.Session(model);
    const output = await session.run(prompt, { stream: false });
    if (typeof output === "string") return output;
    if (output?.response) return String(output.response);
    if (output?.text) return String(output.text);
    return JSON.stringify(output);
  }
  throw new Error("MODEL_NOT_CONFIGURED");
}

function sanitizePlan(input: any, allowedTools: string[]) {
  const allowed = new Set(allowedTools);
  const rawCalls = Array.isArray(input?.toolCalls) ? input.toolCalls : [];
  const toolCalls = rawCalls.slice(0, 4).flatMap((call: any) => {
    const name = String(call?.name || "");
    if (!allowed.has(name)) return [];
    const args = call?.arguments && typeof call.arguments === "object" && !Array.isArray(call.arguments) ? call.arguments : {};
    return [{ name, arguments: args }];
  });
  const hasDraft = toolCalls.some((c: any) => DRAFT_TOOLS.has(c.name));
  const directReply = hasDraft ? null : (typeof input?.directReply === "string" && input.directReply.trim() ? input.directReply.trim().slice(0, 2000) : null);
  return { toolCalls, directReply };
}

function parseJsonObject(raw: string): any {
  const text = raw.trim().replace(/^```(?:json)?\s*/i, "").replace(/```$/i, "").trim();
  try { return JSON.parse(text); } catch {}
  const start = text.indexOf("{"); const end = text.lastIndexOf("}");
  if (start >= 0 && end > start) return JSON.parse(text.slice(start, end + 1));
  throw new Error("MODEL_INVALID_JSON");
}
function cleanText(value: unknown, max: number): string { return typeof value === "string" ? value.trim().slice(0, max) : ""; }
function cleanHistory(value: unknown): HistoryTurn[] { if (!Array.isArray(value)) return []; return value.slice(-8).flatMap((x: any) => { const role = x?.role === "assistant" ? "assistant" : x?.role === "user" ? "user" : null; const content = cleanText(x?.content, 1200); return role && content ? [{ role, content } as HistoryTurn] : []; }); }
function cleanToolResults(value: unknown): ToolResult[] { if (!Array.isArray(value)) return []; return value.slice(0, 4).flatMap((x: any) => { const name = cleanText(x?.name, 80); const content = cleanText(x?.content, 5000); return name && content ? [{ name, content }] : []; }); }
function formatHistory(history: HistoryTurn[]): string { return history.length ? history.map((h) => `${h.role === "user" ? "المستخدم" : "المساعد"}: ${h.content}`).join("\n") : "(لا يوجد)"; }
