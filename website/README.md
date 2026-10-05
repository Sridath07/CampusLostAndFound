# Campus Lost-and-Found Website

This standalone page connects directly to Supabase through the Data API. Open `index.html` in a browser after running `../supabase/schema.sql` in the Supabase SQL Editor. Supabase settings are in `supabase-config.js`; its publishable key is safe for browser use only with the RLS rules and column grants in the SQL setup.

`app.js` uses browser-local storage only as a fallback when the Supabase API is unreachable. The Java HTTP server is optional for serving these files; the website itself does not require `127.0.0.1`.
