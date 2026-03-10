import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

/*
  Migration SQL for subscriptions table (run if columns don't exist):

  ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS auto_renewing boolean,
    ADD COLUMN IF NOT EXISTS payment_state text;

  Upsert uses conflict on (user_id, plan_id); ensure a unique constraint exists on those columns.
*/

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS")
    return new Response(null, { headers: corsHeaders });

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const authHeader = req.headers.get("Authorization") ?? "";
    const jwt = authHeader.replace("Bearer ", "");
    const { data: { user }, error: authErr } = await supabase.auth.getUser(jwt);
    if (authErr || !user)
      return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401, headers: corsHeaders });

    const { purchase_token, product_id, package_name } = await req.json();
    if (!purchase_token || !product_id)
      return new Response(JSON.stringify({ error: "Missing fields" }), { status: 400, headers: corsHeaders });

    const pkg = package_name || Deno.env.get("GOOGLE_PACKAGE_NAME");
    const saJson = JSON.parse(Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON")!);
    const accessToken = await getGoogleAccessToken(saJson);

    // Subscriptions API (not products API)
    const googleRes = await fetch(
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${pkg}/purchases/subscriptions/${product_id}/tokens/${purchase_token}`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
    if (!googleRes.ok)
      return new Response(JSON.stringify({ error: "Invalid purchase" }), { status: 402, headers: corsHeaders });

    const purchase = await googleRes.json();
    const expiryTimeMillis = purchase.expiryTimeMillis ? Number(purchase.expiryTimeMillis) : 0;
    const ends_at = expiryTimeMillis > 0
      ? new Date(expiryTimeMillis).toISOString()
      : null;
    const auto_renewing = Boolean(purchase.autoRenewing);
    const paymentState = purchase.paymentState != null ? Number(purchase.paymentState) : 1; // 0=pending, 1=received, 2=free_trial
    const payment_state = paymentState === 0 ? "pending" : paymentState === 2 ? "free_trial" : "received";

    let status: string;
    if (paymentState === 2) {
      status = "trial";
    } else if (paymentState === 1 && auto_renewing) {
      status = "active";
    } else if (paymentState === 1 && !auto_renewing) {
      status = "cancelled"; // still valid if ends_at in future
    } else {
      status = "active"; // pending or other
    }

    const { data: plan } = await supabase
      .from("plans")
      .select("id, duration_days")
      .eq("play_product_id", product_id)
      .single();
    if (!plan)
      return new Response(JSON.stringify({ error: "Plan not found" }), { status: 404, headers: corsHeaders });

    const startTimeMillis = purchase.startTimeMillis ? Number(purchase.startTimeMillis) : Date.now();
    const starts_at = new Date(startTimeMillis).toISOString();

    const row: Record<string, unknown> = {
      user_id: user.id,
      plan_id: plan.id,
      status,
      starts_at,
      ends_at,
      auto_renewing,
      payment_state,
    };

    const { data: sub, error: upsertErr } = await supabase
      .from("subscriptions")
      .upsert(row, {
        onConflict: "user_id,plan_id",
        ignoreDuplicates: false,
      })
      .select("id")
      .single();

    if (upsertErr)
      return new Response(JSON.stringify({ error: String(upsertErr) }), { status: 500, headers: corsHeaders });

    return new Response(
      JSON.stringify({ success: true, subscription_id: sub?.id, ends_at }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), { status: 500, headers: corsHeaders });
  }
});

async function getGoogleAccessToken(sa: any): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = btoa(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = btoa(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600
  }));

  const keyData = sa.private_key.replace(/-----.*?-----/g, "").replace(/\n/g, "");
  const binaryKey = Uint8Array.from(atob(keyData), c => c.charCodeAt(0));
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8", binaryKey.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false, ["sign"]
  );

  const toSign = new TextEncoder().encode(`${header}.${payload}`);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", cryptoKey, toSign);
  const jwt = `${header}.${payload}.${btoa(String.fromCharCode(...new Uint8Array(sig)))}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`
  });

  const data = await res.json();
  return data.access_token;
}
