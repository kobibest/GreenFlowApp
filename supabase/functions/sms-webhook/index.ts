import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS")
    return new Response(null, { headers: corsHeaders });

  try {
    const { from, body, user_id } = await req.json();
    if (!from || !body)
      return new Response(JSON.stringify({ error: "Missing fields" }), { status: 400, headers: corsHeaders });

    const blocked = false;
    const reason = "";

    return new Response(
      JSON.stringify({
        filtered: blocked,
        reason,
        update_filters: []
      }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), { status: 500, headers: corsHeaders });
  }
});