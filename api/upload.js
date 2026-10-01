const GRAPH = "https://graph.microsoft.com/v1.0";

function send(res, status, payload) {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.end(JSON.stringify(payload));
}

function safeSegment(value) {
  return String(value || "")
    .trim()
    .replace(/[^A-Za-z0-9_-]/g, "_")
    .slice(0, 80);
}

function encodeSharingUrl(url) {
  return "u!" + Buffer.from(url, "utf8")
    .toString("base64")
    .replace(/=+$/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

async function readRawBody(req, maxBytes = 25 * 1024 * 1024) {
  const chunks = [];
  let total = 0;
  for await (const chunk of req) {
    total += chunk.length;
    if (total > maxBytes) throw new Error("Arquivo acima do limite de 25 MB.");
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

async function refreshAccessToken() {
  const body = new URLSearchParams({
    client_id: process.env.MS_CLIENT_ID || "",
    client_secret: process.env.MS_CLIENT_SECRET || "",
    refresh_token: process.env.MS_REFRESH_TOKEN || "",
    grant_type: "refresh_token",
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

  if (!response.ok) {
    const detail = await response.text();
    throw new Error("Falha ao autenticar no OneDrive: " + detail.slice(0, 300));
  }

  const json = await response.json();
  return json.access_token;
}

async function graph(token, path, options = {}) {
  const response = await fetch(GRAPH + path, {
    ...options,
    headers: {
      Authorization: "Bearer " + token,
      ...(options.headers || {})
    }
  });

  if (!response.ok) {
    const detail = await response.text();
    throw new Error("OneDrive " + response.status + ": " + detail.slice(0, 500));
  }

  const type = response.headers.get("content-type") || "";
  return type.includes("application/json") ? response.json() : response;
}

async function resolveRootFolder(token) {
  const sharedUrl = process.env.ONEDRIVE_SHARED_FOLDER_URL;
  if (!sharedUrl) throw new Error("ONEDRIVE_SHARED_FOLDER_URL não configurada.");
  const shareId = encodeSharingUrl(sharedUrl);
  return graph(token, "/shares/" + shareId + "/driveItem?$select=id,name,parentReference");
}

async function ensureChamadoFolder(token, rootId, chamado) {
  const children = await graph(
    token,
    "/me/drive/items/" + encodeURIComponent(rootId) + "/children?$select=id,name,folder"
  );

  const existing = (children.value || []).find(
    item => item.folder && String(item.name).toUpperCase() === chamado.toUpperCase()
  );
  if (existing) return existing;

  return graph(
    token,
    "/me/drive/items/" + encodeURIComponent(rootId) + "/children",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: chamado,
        folder: {},
        "@microsoft.graph.conflictBehavior": "rename"
      })
    }
  );
}

export const config = {
  api: {
    bodyParser: false
  }
};

export default async function handler(req, res) {
  if (req.method === "GET") {
    return send(res, 200, { ok: true, service: "AgilScanner upload" });
  }

  if (req.method !== "POST") {
    return send(res, 405, { ok: false, error: "Método não permitido." });
  }

  try {
    const expectedKey = process.env.UPLOAD_KEY || "";
    const suppliedKey = req.headers["x-upload-key"] || "";
    if (!expectedKey || suppliedKey !== expectedKey) {
      return send(res, 401, { ok: false, error: "Não autorizado." });
    }

    const chamado = safeSegment(req.headers["x-chamado"]);
    const fileName = safeSegment(
      String(req.headers["x-file-name"] || "arquivo.pdf").replace(/\.pdf$/i, "")
    ) + ".pdf";

    if (!chamado) {
      return send(res, 400, { ok: false, error: "Chamado não informado." });
    }

    const pdf = await readRawBody(req);
    if (!pdf.length) {
      return send(res, 400, { ok: false, error: "PDF vazio." });
    }

    const token = await refreshAccessToken();
    const root = await resolveRootFolder(token);
    const folder = await ensureChamadoFolder(token, root.id, chamado);

    const uploadPath =
      "/me/drive/items/" +
      encodeURIComponent(folder.id) +
      ":/" +
      encodeURIComponent(fileName) +
      ":/content";

    const uploaded = await graph(token, uploadPath, {
      method: "PUT",
      headers: { "Content-Type": "application/pdf" },
      body: pdf
    });

    return send(res, 200, {
      ok: true,
      chamado,
      fileName,
      itemId: uploaded.id,
      webUrl: uploaded.webUrl || null
    });
  } catch (error) {
    return send(res, 500, {
      ok: false,
      error: error instanceof Error ? error.message : String(error)
    });
  }
}
