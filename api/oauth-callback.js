export default async function handler(req, res) {
  try {
    const code = req.query?.code;
    if (!code) {
      res.status(400).send("Código de autorização não recebido.");
      return;
    }

    const clientId = process.env.MS_CLIENT_ID;
    const clientSecret = process.env.MS_CLIENT_SECRET;
    const baseUrl = process.env.PUBLIC_BASE_URL || ("https://" + req.headers.host);
    const redirectUri = baseUrl.replace(/\/$/, "") + "/api/oauth-callback";

    const body = new URLSearchParams({
      client_id: clientId || "",
      client_secret: clientSecret || "",
      code: String(code),
      redirect_uri: redirectUri,
      grant_type: "authorization_code",
      scope: "offline_access Files.ReadWrite"
    });

    const response = await fetch(
      "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
      {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body
      }
    );

    const json = await response.json();
    if (!response.ok) {
      res.status(500).send(
        "<h2>Falha ao gerar token</h2><pre>" +
        String(json.error_description || json.error || "Erro desconhecido")
          .replace(/&/g, "&amp;")
          .replace(/</g, "&lt;") +
        "</pre>"
      );
      return;
    }

    const refresh = json.refresh_token;
    if (!refresh) {
      res.status(500).send("A Microsoft não retornou refresh_token.");
      return;
    }

    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.end(`
      <!doctype html>
      <html lang="pt-BR">
      <meta name="viewport" content="width=device-width,initial-scale=1">
      <body style="font-family:Arial,sans-serif;padding:24px;max-width:720px;margin:auto">
        <h2>Autorização concluída ✅</h2>
        <p>Copie o valor abaixo e salve no Vercel como <b>MS_REFRESH_TOKEN</b>.</p>
        <textarea readonly style="width:100%;height:220px;font-size:14px">${refresh}</textarea>
        <p><b>Não envie esse valor por mensagem.</b> Depois de salvar no Vercel, você pode fechar esta página.</p>
      </body>
      </html>
    `);
  } catch (error) {
    res.status(500).send("Erro: " + (error instanceof Error ? error.message : String(error)));
  }
}
