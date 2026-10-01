export default async function handler(req, res) {
  const clientId = process.env.MS_CLIENT_ID;
  const baseUrl = process.env.PUBLIC_BASE_URL || ("https://" + req.headers.host);
  if (!clientId) {
    res.status(500).send("MS_CLIENT_ID não configurado.");
    return;
  }

  const redirectUri = baseUrl.replace(/\/$/, "") + "/api/oauth-callback";
  const params = new URLSearchParams({
    client_id: clientId,
    response_type: "code",
    redirect_uri: redirectUri,
    response_mode: "query",
    scope: "offline_access Files.ReadWrite",
    prompt: "consent"
  });

  res.redirect("https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?" + params.toString());
}
