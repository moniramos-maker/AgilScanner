import { recoverRegistryHistory, isExpired } from "../lib/access-store.js";

function authorized(req) {
  const expected = process.env.ADMIN_KEY || "";
  const supplied = String(req.headers["x-admin-key"] || "");
  return expected && supplied && supplied === expected;
}

export default async function handler(req, res) {
  if (!authorized(req)) {
    return res.status(401).json({ ok: false, error: "Não autorizado." });
  }

  if (req.method !== "POST") {
    return res.status(405).json({ ok: false, error: "Método não permitido." });
  }

  try {
    const registry = await recoverRegistryHistory();
    const devices = registry.devices
      .map(d => ({ ...d, effectiveStatus: isExpired(d) ? "expired" : d.status }))
      .sort((a,b) => String(b.createdAt).localeCompare(String(a.createdAt)));

    return res.status(200).json({
      ok: true,
      devices,
      recoveredFromVersions: registry.recoveredFromVersions || 0
    });
  } catch (e) {
    return res.status(500).json({ ok: false, error: e?.message || String(e) });
  }
}
