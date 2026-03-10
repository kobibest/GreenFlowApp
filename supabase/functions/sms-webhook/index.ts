import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function extractCode(body: string): string | null {
  const match = body.match(/\b(\d{4,8})\b/);
  return match ? match[1] : null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS")
    return new Response(null, { headers: corsHeaders });

  try {
    const { from, body, user_id } = await req.json();
    if (!from || !body)
      return new Response(JSON.stringify({ error: "Missing fields" }), { status: 400, headers: corsHeaders });

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    // שליפת פרופיל המשתמש
    const { data: profile, error: profileError } = await supabase
      .from("profiles")
      .select("whatsapp_group_id")
      .eq("id", user_id)
      .single();

    if (profileError || !profile?.whatsapp_group_id) {
      return new Response(
        JSON.stringify({ error: "No WhatsApp group found for user" }),
        { status: 400, headers: corsHeaders }
      );
    }

    // שליפת שם הבנק מטבלת sms_filters לפי sender
    const { data: filters } = await supabase
      .from("sms_filters")
      .select("bank_name")
      .eq("sender", from)
      .eq("enabled", true)
      .limit(1);

    const bankName = filters?.[0]?.bank_name || from;
    const code = extractCode(body);

    // Green API credentials
    const greenApiUrl = Deno.env.get("GREEN_API_URL");
    const greenApiId = Deno.env.get("GREEN_API_ID_INSTANCE");
    const greenApiToken = Deno.env.get("GREEN_API_TOKEN");

    const chatId = profile.whatsapp_group_id;
    let sendRes: Response;

    if (code) {
      // ניסיון שליחה עם כפתור העתקה
      sendRes = await fetch(
        `${greenApiUrl}/waInstance${greenApiId}/sendInteractiveButtons/${greenApiToken}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            chatId,
            body: `${bankName}:\n${code}`,
            buttons: [
              {
                type: "copy",
                buttonId: "copy_code",
                buttonText: "📋 העתק קוד",
                copyCode: code,
              },
            ],
          }),
        }
      );

      // fallback להודעה רגילה
      if (!sendRes.ok) {
        sendRes = await fetch(
          `${greenApiUrl}/waInstance${greenApiId}/sendMessage/${greenApiToken}`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              chatId,
              message: `🔑 ${bankName}:\n${code}`,
            }),
          }
        );
      }
    } else {
      // אין קוד — שלח הודעה רגילה
      sendRes = await fetch(
        `${greenApiUrl}/waInstance${greenApiId}/sendMessage/${greenApiToken}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            chatId,
            message: `📩 הודעה מ-${bankName}:\n${body}`,
          }),
        }
      );
    }

    if (!sendRes.ok) {
      const errText = await sendRes.text();
      console.error("Green API error:", errText);
      return new Response(
        JSON.stringify({ error: "Failed to send WhatsApp message", details: errText }),
        { status: 500, headers: corsHeaders }
      );
    }

    return new Response(
      JSON.stringify({ filtered: false, reason: "", update_filters: [] }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), { status: 500, headers: corsHeaders });
  }
});