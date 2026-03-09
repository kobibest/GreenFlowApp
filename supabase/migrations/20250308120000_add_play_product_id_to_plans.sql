-- Add play_product_id to plans for Google Play Billing product mapping
ALTER TABLE public.plans ADD COLUMN IF NOT EXISTS play_product_id text;

-- Update monthly plan: greenflow_monthly, 39 ILS, Hebrew name
UPDATE public.plans
SET play_product_id = 'greenflow_monthly', price = 39, name = 'מנוי חודשי'
WHERE id = 'fac6f1a7-a1ab-4090-a71b-69f1978df026';
