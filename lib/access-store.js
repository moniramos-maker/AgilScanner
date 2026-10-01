const GRAPH = "https://graph.microsoft.com/v1.0";
const REGISTRY_FILE = "_AGIL_ACCESS_REGISTRY.json";

function encodeSharingUrl(url) {
  return "u!" + Buffer.from(url, "utf8").toString("base64")
    .replace(/=+$/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function refreshAccessToken() {
  const body = new URLSearchParams({
    client_id: process.env.MS_CLIENT_ID || "",
    client_secret: process.env.MS_CLIENT_SECRET || "",
    refresh_token: process.env.MS_REFRESH_TOKEN || "",
    grant_type: "refresh_token",
    scope: "offline_access Files.ReadWrite"
  });
  const response = await fetch("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body
  });
  if (!response.ok) throw new Error("Falha ao autenticar no OneDrive.");
  return (await response.json()).access_token;
}

async function graph(token, path, options = {}) {
  return fetch(GRAPH + path, {
    ...options,
    headers: { Authorization: "Bearer " + token, ...(options.headers || {}) }
  });
}

async function rootInfo(token) {
  const sharedUrl = process.env.ONEDRIVE_SHARED_FOLDER_URL;
  if (!sharedUrl) throw new Error("ONEDRIVE_SHARED_FOLDER_URL não configurada.");
  const shareId = encodeSharingUrl(sharedUrl);
  const res = await graph(token, "/shares/" + shareId + "/driveItem?$select=id,name,parentReference");
  if (!res.ok) throw new Error("Não foi possível localizar a pasta raiz.");
  const root = await res.json();
  const driveId = root.parentReference?.driveId;
  if (!driveId) throw new Error("Drive ID não encontrado.");
  return { rootId: root.id, driveId };
}

export async function loadRegistry() {
  const token = await refreshAccessToken();
  const { rootId, driveId } = await rootInfo(token);
  const path = "/drives/" + encodeURIComponent(driveId) + "/items/" + encodeURIComponent(rootId) + ":/" + REGISTRY_FILE + ":/content";
  const res = await graph(token, path);
  if (res.status === 404) return { devices: [], updatedAt: new Date().toISOString() };
  if (!res.ok) throw new Error("Falha ao ler cadastro de acessos.");
  try {
    const parsed = JSON.parse(await res.text());
    return { devices: Array.isArray(parsed.devices) ? parsed.devices : [], updatedAt: parsed.updatedAt || null };
  } catch {
    return { devices: [], updatedAt: null };
  }
}

export async function saveRegistry(registry) {
  const token = await refreshAccessToken();
  const { rootId, driveId } = await rootInfo(token);
  const path = "/drives/" + encodeURIComponent(driveId) + "/items/" + encodeURIComponent(rootId) + ":/" + REGISTRY_FILE + ":/content";
  const body = JSON.stringify({ ...registry, updatedAt: new Date().toISOString() }, null, 2);
  const res = await graph(token, path, {
    method: "PUT",
    headers: { "Content-Type": "application/json; charset=utf-8" },
    body
  });
  if (!res.ok) throw new Error("Falha ao salvar cadastro de acessos.");
}

export function cleanText(value, max = 120) {
  return String(value || "").trim().replace(/[<>]/g, "").slice(0, max);
}

export function isExpired(device) {
  if (!device.expiresAt) return false;
  const t = Date.parse(device.expiresAt);
  return Number.isFinite(t) && t < Date.now();
}
