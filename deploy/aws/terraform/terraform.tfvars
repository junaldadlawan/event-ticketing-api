# Non-secret settings for this environment (secrets live in Secrets Manager, never here).

# Browser origins allowed to call the API (CORS): the Netlify front end. Exact origin, no trailing slash.
# Netlify deploy previews / branch deploys have other subdomains and are NOT allowed unless added here.
cors_allowed_origins = ["https://event-ticketing-staging.netlify.app"]
