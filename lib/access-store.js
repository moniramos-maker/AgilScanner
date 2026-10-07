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

function normalizeRegistry(parsed) {
  return {
    devices: Array.isArray(parsed?.devices) ? parsed.devices : [],
    updatedAt: parsed?.updatedAt || null
  };
}

function mergeDevices(baseDevices, incomingDevices) {
  const map = new Map();
  for (const d of baseDevices || []) {
    if (d?.deviceId) map.set(d.deviceId, d);
  }
  for (const d of incomingDevices || []) {
    if (d?.deviceId) map.set(d.deviceId, { ...(map.get(d.deviceId) || {}), ...d });
  }
  return Array.from(map.values());
}

async function getRegistryMeta(token, driveId, rootId) {
  const path =
    "/drives/" + encodeURIComponent(driveId) +
    "/items/" + encodeURIComponent(rootId) +
    ":/" + REGISTRY_FILE + "?$select=id,eTag,name";

  const res = await graph(token, path);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error("Falha ao localizar cadastro de acessos.");
  return res.json();
}

async function readRegistryByItemId(token, driveId, itemId) {
  const res = await graph(
    token,
    "/drives/" + encodeURIComponent(driveId) +
      "/items/" + encodeURIComponent(itemId) + "/content"
  );
  if (!res.ok) throw new Error("Falha ao ler cadastro de acessos.");
  try {
    return normalizeRegistry(JSON.parse(await res.text()));
  } catch {
    return normalizeRegistry({});
  }
}

async function writeRegistry(token, driveId, rootId, registry, eTag = null) {
  const path =
    "/drives/" + encodeURIComponent(driveId) +
    "/items/" + encodeURIComponent(rootId) +
    ":/" + REGISTRY_FILE + ":/content";

  const headers = { "Content-Type": "application/json; charset=utf-8" };
  if (eTag) headers["If-Match"] = eTag;

  const body = JSON.stringify(
    { ...registry, updatedAt: new Date().toISOString() },
    null,
    2
  );

  return graph(token, path, {
    method: "PUT",
    headers,
    body
  });
}

async function tryRecoverFromVersions(token, driveId, meta, current) {
  if (!meta?.id || current.devices.length > 1) return current;

  const versionsRes = await graph(
    token,
    "/drives/" + encodeURIComponent(driveId) +
      "/items/" + encodeURIComponent(meta.id) +
      "/versions?$top=25"
  );

  if (!versionsRes.ok) return current;

  const versions = await versionsRes.json();
  let best = current;

  for (const version of versions.value || []) {
    if (!version?.id) continue;

    const contentRes = await graph(
      token,
      "/drives/" + encodeURIComponent(driveId) +
        "/items/" + encodeURIComponent(meta.id) +
        "/versions/" + encodeURIComponent(version.id) +
        "/content"
    );

    if (!contentRes.ok) continue;

    try {
      const candidate = normalizeRegistry(JSON.parse(await contentRes.text()));
      if (candidate.devices.length > best.devices.length) {
        best = candidate;
      }
    } catch {
      // ignora versões inválidas
    }
  }

  if (best.devices.length > current.devices.length) {
    const healed = {
      devices: mergeDevices(best.devices, current.devices),
      updatedAt: new Date().toISOString()
    };

    const refreshedMeta = await getRegistryMeta(token, driveId, (await rootInfo(token)).rootId);
    const saveRes = await writeRegistry(
      token,
      driveId,
      (await rootInfo(token)).rootId,
      healed,
      refreshedMeta?.eTag || null
    );

    if (saveRes.ok) return healed;
  }

  return current;
}

export async function loadRegistry() {
  const token = await refreshAccessToken();
  const { rootId, driveId } = await rootInfo(token);
  const meta = await getRegistryMeta(token, driveId, rootId);

  if (!meta) {
    return { devices: [], updatedAt: new Date().toISOString() };
  }

  const current = await readRegistryByItemId(token, driveId, meta.id);
  return tryRecoverFromVersions(token, driveId, meta, current);
}

export async function updateRegistry(mutator) {
  const token = await refreshAccessToken();
  const { rootId, driveId } = await rootInfo(token);

  for (let attempt = 0; attempt < 5; attempt++) {
    const meta = await getRegistryMeta(token, driveId, rootId);
    let registry = meta
      ? await readRegistryByItemId(token, driveId, meta.id)
      : { devices: [], updatedAt: null };

    registry = normalizeRegistry(registry);
    const next = await mutator({
      devices: registry.devices.map(d => ({ ...d })),
      updatedAt: registry.updatedAt
    });

    const target = normalizeRegistry(next || registry);
    const res = await writeRegistry(token, driveId, rootId, target, meta?.eTag || null);

    if (res.ok) return target;
    if (res.status !== 412) {
      throw new Error("Falha ao salvar cadastro de acessos.");
    }
  }

  throw new Error("Cadastro foi alterado ao mesmo tempo por outro processo. Tente novamente.");
}

export async function saveRegistry(registry) {
  return updateRegistry(current => ({
    devices: mergeDevices(current.devices, registry.devices),
    updatedAt: registry.updatedAt || current.updatedAt
  }));
}

export function cleanText(value, max = 120) {
  return String(value || "").trim().replace(/[<>]/g, "").slice(0, max);
}

export function isExpired(device) {
  if (!device.expiresAt) return false;
  const t = Date.parse(device.expiresAt);
  return Number.isFinite(t) && t < Date.now();
}
