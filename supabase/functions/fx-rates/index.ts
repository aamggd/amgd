import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const YECES_CSV = "https://yeces.com/?yeces_fx_csv=1";
const YECES_PAGE = "https://yeces.com/exchange-rates-yemen/";
const YETI_PAGE = "https://dev.yemen.yeti.acaps.org/xr-commodities/";
const SAMA_PEG_URL = "https://sama.gov.sa/en-US/MediaCenter/News/Pages/news-557.aspx";
const USD_PER_SAR = 3.75;

const corsHeaders = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, x-client-info, apikey, content-type",
  "access-control-allow-methods": "GET, OPTIONS",
};

type Region = "ADEN" | "SANAA";
type Currency = "USD" | "SAR";
type RateType = "BUY" | "SELL";

type PrimarySet = {
  publishedAt: number;
  hash: string;
  values: Record<string, number>;
};

type YetiSet = {
  publishedAt: number;
  sanaaUsd: number;
  adenUsd: number;
  hash: string;
};

type NormalizedRate = {
  marketRegion: Region;
  currencyCode: Currency;
  rateType: RateType;
  rateYer: number;
  primarySource: string;
  primarySourceUrl: string;
  sourcePublishedAt: number;
  comparisonRateYer: number | null;
  comparisonSource: string;
  comparisonSourceUrl: string;
  comparisonPublishedAt: number | null;
  variancePercent: number | null;
  sourceStatus: string;
  rawHash: string;
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "GET") return json({ error: "Method not allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!supabaseUrl || !serviceRole) return json({ error: "Supabase service configuration is missing" }, 500);
  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });

  const fetchedAt = Date.now();
  const batchId = crypto.randomUUID();
  let primary: PrimarySet | null = null;
  let secondary: YetiSet | null = null;
  const errors: string[] = [];

  try { primary = await fetchYeces(); } catch (e) { errors.push(`YECES: ${messageOf(e)}`); }
  try { secondary = await fetchYeti(); } catch (e) { errors.push(`YETI: ${messageOf(e)}`); }

  let rates: NormalizedRate[] = [];
  let fallbackMode = "LIVE_PRIMARY";
  let primarySource = "YECES";
  let comparisonSource = secondary ? "ACAPS YETI + SAMA USD/SAR 3.75" : "SAMA USD/SAR 3.75";

  if (primary) {
    rates = buildPrimaryRates(primary, secondary);
  } else if (secondary) {
    fallbackMode = "SECONDARY_ONLY";
    primarySource = "ACAPS YETI";
    comparisonSource = "SAMA USD/SAR 3.75";
    rates = buildSecondaryFallbackRates(secondary);
  } else {
    const { data, error } = await db
      .from("fx_rate_feed_batches")
      .select("payload")
      .order("fetched_at", { ascending: false })
      .limit(1)
      .maybeSingle();
    if (!error && data?.payload) {
      const cached = data.payload as Record<string, unknown>;
      return json({ ...cached, fallbackMode: "CACHE", fetchErrors: errors }, 200);
    }
    return json({ error: "تعذر جلب مصدري أسعار الصرف ولا توجد نسخة مخزنة", details: errors }, 503);
  }

  const payload = {
    batchId,
    fetchedAt,
    fallbackMode,
    primarySource,
    comparisonSource,
    fetchErrors: errors,
    rates,
  };

  const sourceHash = await sha256Hex(JSON.stringify(rates));
  const { error: batchError } = await db.from("fx_rate_feed_batches").insert({
    batch_id: batchId,
    fetched_at: new Date(fetchedAt).toISOString(),
    fallback_mode: fallbackMode,
    primary_source: primarySource,
    comparison_source: comparisonSource,
    source_hash: sourceHash,
    status: errors.length ? "PARTIAL" : "OK",
    payload,
  });
  if (batchError) return json({ error: `Failed to persist FX batch: ${batchError.message}` }, 500);

  const rateRows = rates.map((r) => ({
    batch_id: batchId,
    market_region: r.marketRegion,
    currency_code: r.currencyCode,
    rate_type: r.rateType,
    rate_yer: r.rateYer,
    source_published_at: new Date(r.sourcePublishedAt).toISOString(),
    primary_source: r.primarySource,
    primary_source_url: r.primarySourceUrl,
    comparison_rate_yer: r.comparisonRateYer,
    comparison_source: r.comparisonSource,
    comparison_source_url: r.comparisonSourceUrl,
    comparison_published_at: r.comparisonPublishedAt ? new Date(r.comparisonPublishedAt).toISOString() : null,
    variance_percent: r.variancePercent,
    source_status: r.sourceStatus,
    raw_hash: r.rawHash,
  }));
  const { error: rowsError } = await db.from("fx_rate_feed_rates").insert(rateRows);
  if (rowsError) return json({ error: `Failed to persist FX rows: ${rowsError.message}` }, 500);

  return json(payload, 200);
});

async function fetchYeces(): Promise<PrimarySet> {
  let lastError = "";
  try {
    const response = await fetch(YECES_CSV, { headers: { "user-agent": "FUSH-ERP-FX/1.0" } });
    if (!response.ok) throw new Error(`CSV HTTP ${response.status}`);
    const text = await response.text();
    const parsed = parseYecesCsv(text);
    return { ...parsed, hash: await sha256Hex(text) };
  } catch (e) {
    lastError = messageOf(e);
  }

  const response = await fetch(YECES_PAGE, { headers: { "user-agent": "FUSH-ERP-FX/1.0" } });
  if (!response.ok) throw new Error(`${lastError}; HTML HTTP ${response.status}`);
  const html = await response.text();
  const parsed = parseYecesHtml(html);
  return { ...parsed, hash: await sha256Hex(html) };
}

async function fetchYeti(): Promise<YetiSet> {
  const response = await fetch(YETI_PAGE, { headers: { "user-agent": "FUSH-ERP-FX/1.0" } });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const html = await response.text();
  const text = stripHtml(html);
  const section = text.slice(Math.max(0, text.indexOf("Current Exchange Rate")), text.indexOf("Food Basket Price") > 0 ? text.indexOf("Food Basket Price") : undefined);
  const sanaa = captureNumber(section, /Old\s+Notes\s*-\s*Sana'?a[\s\S]{0,180}?USD\s*\/\s*YER[\s\S]{0,80}?([0-9][0-9,.]*)/i);
  const aden = captureNumber(section, /New\s+Notes\s*-\s*Aden[\s\S]{0,180}?USD\s*\/\s*YER[\s\S]{0,80}?([0-9][0-9,.]*)/i);
  const dateMatch = section.match(/(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),\s+[A-Za-z]+\s+\d{1,2},\s+20\d{2}/i);
  const publishedAt = dateMatch ? parseDateFlexible(dateMatch[0]) : Date.now();
  return { publishedAt, sanaaUsd: sanaa, adenUsd: aden, hash: await sha256Hex(html) };
}

function buildPrimaryRates(primary: PrimarySet, secondary: YetiSet | null): NormalizedRate[] {
  const rows: NormalizedRate[] = [];
  for (const region of ["ADEN", "SANAA"] as Region[]) {
    for (const type of ["BUY", "SELL"] as RateType[]) {
      const usd = requireValue(primary.values, `${region}:USD:${type}`);
      const sar = requireValue(primary.values, `${region}:SAR:${type}`);
      const yetiUsd = type === "SELL" && secondary ? (region === "ADEN" ? secondary.adenUsd : secondary.sanaaUsd) : null;
      rows.push(makeRate(region, "USD", type, usd, "YECES", YECES_PAGE, primary.publishedAt, yetiUsd,
        yetiUsd ? "ACAPS YETI" : "", yetiUsd ? YETI_PAGE : "", yetiUsd ? secondary?.publishedAt ?? null : null, primary.hash));
      const sarCross = usd / USD_PER_SAR;
      rows.push(makeRate(region, "SAR", type, sar, "YECES", YECES_PAGE, primary.publishedAt, sarCross,
        "SAMA USD/SAR 3.75 cross-check", SAMA_PEG_URL, primary.publishedAt, primary.hash));
    }
  }
  return rows;
}

function buildSecondaryFallbackRates(secondary: YetiSet): NormalizedRate[] {
  const rows: NormalizedRate[] = [];
  for (const region of ["ADEN", "SANAA"] as Region[]) {
    const usd = region === "ADEN" ? secondary.adenUsd : secondary.sanaaUsd;
    rows.push(makeRate(region, "USD", "SELL", usd, "ACAPS YETI", YETI_PAGE, secondary.publishedAt,
      null, "", "", null, secondary.hash, "FALLBACK_SECONDARY"));
    rows.push(makeRate(region, "SAR", "SELL", usd / USD_PER_SAR, "ACAPS YETI + SAMA 3.75 derived", YETI_PAGE,
      secondary.publishedAt, null, "", "", null, secondary.hash, "FALLBACK_DERIVED"));
  }
  return rows;
}

function makeRate(
  region: Region, currency: Currency, type: RateType, rate: number,
  primarySource: string, primarySourceUrl: string, publishedAt: number,
  comparisonRate: number | null, comparisonSource: string, comparisonUrl: string,
  comparisonPublishedAt: number | null, rawHash: string, forcedStatus?: string,
): NormalizedRate {
  const variance = comparisonRate && comparisonRate > 0 ? Math.abs(rate - comparisonRate) / rate * 100 : null;
  return {
    marketRegion: region,
    currencyCode: currency,
    rateType: type,
    rateYer: round6(rate),
    primarySource,
    primarySourceUrl,
    sourcePublishedAt: publishedAt,
    comparisonRateYer: comparisonRate ? round6(comparisonRate) : null,
    comparisonSource,
    comparisonSourceUrl: comparisonUrl,
    comparisonPublishedAt,
    variancePercent: variance == null ? null : round6(variance),
    sourceStatus: forcedStatus ?? (comparisonRate ? "COMPARED" : "PRIMARY_ONLY"),
    rawHash,
  };
}

function parseYecesCsv(csv: string): Omit<PrimarySet, "hash"> {
  const rows = parseCsv(csv).filter((r) => r.some((c) => c.trim()));
  if (rows.length < 2) throw new Error("CSV is empty");
  const headers = rows[0].map((h) => normalizeText(h));
  const dateIndex = headers.findIndex((h) => /date|تاريخ/.test(h));
  const data = rows.slice(1).find((r) => r.some((v) => parseNumber(v) != null));
  if (!data) throw new Error("CSV has no data rows");
  const values: Record<string, number> = {};

  for (let i = 0; i < headers.length; i++) {
    const h = headers[i];
    const cell = data[i] ?? "";
    const currency: Currency | null = /usd|دولار/.test(h) ? "USD" : (/sar|سعود/.test(h) ? "SAR" : null);
    const region: Region | null = /aden|عدن/.test(h) ? "ADEN" : (/sanaa|sana|صنعاء/.test(h) ? "SANAA" : null);
    if (!currency || !region) continue;
    const buy = /buy|شراء/.test(h);
    const sell = /sell|بيع/.test(h);
    if (buy || sell) {
      const n = parseNumber(cell);
      if (n != null) values[`${region}:${currency}:${buy ? "BUY" : "SELL"}`] = n;
      continue;
    }
    const pair = parseBuySellCell(cell);
    if (pair) {
      values[`${region}:${currency}:BUY`] = pair.buy;
      values[`${region}:${currency}:SELL`] = pair.sell;
    }
  }

  // Some CSV generators use plain positional columns. Accept the documented page order as a last resort.
  const required = ["SANAA:USD:BUY","SANAA:USD:SELL","ADEN:USD:BUY","ADEN:USD:SELL","SANAA:SAR:BUY","SANAA:SAR:SELL","ADEN:SAR:BUY","ADEN:SAR:SELL"];
  if (!required.every((k) => values[k] != null)) {
    const nums = data.map(parseNumber).filter((v): v is number => v != null);
    if (nums.length >= 8) {
      const offset = nums.length >= 9 ? 1 : 0; // first numeric column may be a date serial/index
      const candidate = nums.slice(offset, offset + 8);
      if (candidate.length === 8) {
        [values["SANAA:USD:BUY"], values["SANAA:USD:SELL"], values["ADEN:USD:BUY"], values["ADEN:USD:SELL"],
         values["SANAA:SAR:BUY"], values["SANAA:SAR:SELL"], values["ADEN:SAR:BUY"], values["ADEN:SAR:SELL"]] = candidate;
      }
    }
  }
  if (!required.every((k) => values[k] != null && values[k] > 0)) throw new Error("CSV columns could not be mapped safely");
  const publishedAt = dateIndex >= 0 ? parseDateFlexible(data[dateIndex]) : Date.now();
  return { publishedAt, values };
}

function parseYecesHtml(html: string): Omit<PrimarySet, "hash"> {
  const text = stripHtml(html);
  const start = Math.max(0, text.indexOf("سجل الأسعار اليومية"));
  const section = text.slice(start, start + 7000);
  const re = /(\d{1,2}\s+[\p{L}\u0600-\u06FF]+\s+20\d{2})[\s\S]{0,260}?شراء\s*([0-9٠-٩.,]+)\s*بيع\s*([0-9٠-٩.,]+)[\s\S]{0,180}?شراء\s*([0-9٠-٩.,]+)\s*بيع\s*([0-9٠-٩.,]+)[\s\S]{0,240}?شراء\s*([0-9٠-٩.,]+)\s*بيع\s*([0-9٠-٩.,]+)[\s\S]{0,180}?شراء\s*([0-9٠-٩.,]+)\s*بيع\s*([0-9٠-٩.,]+)/u;
  const m = section.match(re);
  if (!m) throw new Error("HTML daily rate row not found");
  const n = (v: string) => requireNumber(v);
  return {
    publishedAt: parseDateFlexible(m[1]),
    values: {
      "SANAA:USD:BUY": n(m[2]), "SANAA:USD:SELL": n(m[3]),
      "ADEN:USD:BUY": n(m[4]), "ADEN:USD:SELL": n(m[5]),
      "SANAA:SAR:BUY": n(m[6]), "SANAA:SAR:SELL": n(m[7]),
      "ADEN:SAR:BUY": n(m[8]), "ADEN:SAR:SELL": n(m[9]),
    },
  };
}

function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [], cell = "", quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { cell += '"'; i++; }
      else if (c === '"') quoted = false;
      else cell += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') { row.push(cell); cell = ""; }
    else if (c === '\n') { row.push(cell.replace(/\r$/, "")); rows.push(row); row = []; cell = ""; }
    else cell += c;
  }
  if (cell.length || row.length) { row.push(cell); rows.push(row); }
  return rows;
}

function parseBuySellCell(cell: string): { buy: number; sell: number } | null {
  const ar = cell.match(/شراء\s*([0-9٠-٩.,]+)[\s\S]*?بيع\s*([0-9٠-٩.,]+)/u);
  if (ar) return { buy: requireNumber(ar[1]), sell: requireNumber(ar[2]) };
  const nums = (cell.match(/[0-9٠-٩][0-9٠-٩.,]*/g) ?? []).map(requireNumber);
  return nums.length >= 2 ? { buy: nums[0], sell: nums[1] } : null;
}

function normalizeText(v: string): string {
  return arabicDigitsToLatin(v).toLowerCase().replace(/[\s_\-\/|]+/g, " ").trim();
}

function stripHtml(html: string): string {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;|&#160;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&quot;/gi, '"')
    .replace(/\s+/g, " ")
    .trim();
}

function captureNumber(text: string, re: RegExp): number {
  const m = text.match(re);
  if (!m) throw new Error(`pattern not found: ${re}`);
  return requireNumber(m[1]);
}

function arabicDigitsToLatin(v: string): string {
  return v.replace(/[٠-٩]/g, (d) => String("٠١٢٣٤٥٦٧٨٩".indexOf(d)));
}

function parseNumber(v: string): number | null {
  const cleaned = arabicDigitsToLatin(v).replace(/,/g, "").match(/-?\d+(?:\.\d+)?/)?.[0];
  if (!cleaned) return null;
  const n = Number(cleaned);
  return Number.isFinite(n) ? n : null;
}
function requireNumber(v: string): number { const n = parseNumber(v); if (n == null || n <= 0) throw new Error(`invalid number: ${v}`); return n; }
function requireValue(values: Record<string, number>, key: string): number { const v = values[key]; if (!v || !Number.isFinite(v)) throw new Error(`missing ${key}`); return v; }

function parseDateFlexible(raw: string): number {
  const text = arabicDigitsToLatin(raw).trim();
  const direct = Date.parse(text);
  if (Number.isFinite(direct)) return direct;
  const months: Record<string, number> = {
    "يناير":0,"فبراير":1,"مارس":2,"أبريل":3,"ابريل":3,"مايو":4,"يونيو":5,"يوليو":6,
    "أغسطس":7,"اغسطس":7,"سبتمبر":8,"أكتوبر":9,"اكتوبر":9,"نوفمبر":10,"ديسمبر":11,
  };
  const m = text.match(/(\d{1,2})\s+([\u0600-\u06FF]+)\s+(20\d{2})/u);
  if (m && months[m[2]] != null) return Date.UTC(Number(m[3]), months[m[2]], Number(m[1]), 12, 0, 0);
  throw new Error(`unparseable date: ${raw}`);
}

function variance(primary: number, secondary: number): number { return Math.abs(primary-secondary)/primary*100; }
function round6(v: number): number { return Math.round(v * 1_000_000) / 1_000_000; }
function messageOf(e: unknown): string { return e instanceof Error ? e.message : String(e); }
async function sha256Hex(text: string): Promise<string> {
  const hash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(hash)).map((b) => b.toString(16).padStart(2, "0")).join("");
}
function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers: { ...corsHeaders, "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });
}
