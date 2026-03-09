import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS")
    return new Response(null, { headers: corsHeaders });

  try {
    const payload = await req.json();
    const {
      device_id, user_id, app_version, android_version,
      device_model, description, error_message, stack_trace,
      logs, metadata, status
    } = payload;

    if (!device_id || !error_message)
      return new Response(JSON.stringify({ error: "Missing required fields" }), { status: 400, headers: corsHeaders });

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const { data, error } = await supabase
      .from("bug_reports")
      .insert({
        device_id, user_id, app_version, android_version,
        device_model, description, error_message, stack_trace,
        logs: logs ?? [],
        metadata: metadata ?? {},
        status: status ?? "new"
      })
      .select("id")
      .single();

    if (error) throw error;

    return new Response(
      JSON.stringify({ success: true, report_id: data.id }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), { status: 500, headers: corsHeaders });
  }
});