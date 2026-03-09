# verify-play-purchase

Edge Function that verifies a Google Play subscription with the Google Play Developer API and, only after successful verification, inserts a row into the `subscriptions` table.

## Setup

1. **Google Play Console**
   - Create a subscription product and note its **Product ID** (e.g. `greenflow_monthly`).
   - In **Setup → API access**, link a **Google Cloud project** and create a **Service account** with "View financial data" (or use an existing one). Download the JSON key.

2. **Supabase**
   - Deploy this function: `supabase functions deploy verify-play-purchase`
   - Set secrets:
     - `GOOGLE_SERVICE_ACCOUNT_JSON`: full contents of the service account JSON key file.
     - `GOOGLE_PACKAGE_NAME`: your app’s package name (e.g. `com.tanglycohort.greenflow`).

3. **Database**
   - In the `plans` table, set `play_product_id` for each plan that is sold via Google Play (use the same Product ID as in Play Console).

## Request

- Method: `POST`
- Headers: `Authorization: Bearer <Supabase JWT (user session)>`, `Content-Type: application/json`
- Body: `{ "purchase_token", "product_id", "user_id", "order_id?" }` — `plan_id` is optional; the function resolves it from `plans.play_product_id = product_id`.

The function checks the JWT, verifies that `product_id` matches a plan’s `play_product_id`, verifies the purchase with Google, then inserts one row into `subscriptions` with `user_id`, `plan_id`, `status: "active"`, and dates from Google.

## Response

On success: `200` with body `{ "active": true, "subscription": <inserted row> }`.
