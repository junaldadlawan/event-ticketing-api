# Non-secret settings for this environment (secrets live in Secrets Manager, never here).

# Browser origins allowed to call the API (CORS): the Netlify front end. Exact origin, no trailing slash.
# Netlify deploy previews / branch deploys have other subdomains and are NOT allowed unless added here.
cors_allowed_origins = ["https://event-ticketing-staging.netlify.app", "http://localhost:5173", "https://deploy-preview-16--event-ticketing-staging.netlify.app"]

# Real card payments through PayMongo (needs the two PayMongo secrets filled in Secrets Manager, see secrets.tf).
payments_gateway = "paymongo"
seed_demo_data   = false
