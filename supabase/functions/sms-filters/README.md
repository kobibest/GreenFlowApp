# sms-filters

Returns SMS filter rules for the Green Flow app.

- **Method**: GET
- **Auth**: Optional. If `Authorization: Bearer <JWT>` is sent, the user is validated; rules can later be loaded per user from a table.
- **Response**: `{ "rules": [ { "match?", "words?", "sender?" } ], "version": 1 }`

Deploy: `supabase functions deploy sms-filters`
