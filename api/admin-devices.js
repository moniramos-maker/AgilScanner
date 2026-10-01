import { loadRegistry, saveRegistry, cleanText, isExpired } from "../lib/access-store.js";

function authorized(req) {
  const expected = process.env.ADMIN_KEY || "";
  const supplied = String(req.headers["x-admin-key"] || "");
  return expected && supplied && supplied === expected;
}

export default async function handler(req, res) {
  if (!authorized(req)) return res.status(401).json({ ok: false, error: "Não autorizado." });
  try {
    const registry = await loadRegistry();
    if (req.method === "GET") {
      const devices = registry.devices
        .map(d => ({ ...d, effectiveStatus: isExpired(d) ? "expired" : d.status }))
        .sort((a,b) => String(b.createdAt).localeCompare(String(a.createdAt)));
      return res.status(200).json({ ok: true, devices });
    }
    if (req.method === "POST") {
      const deviceId = cleanText(req.body?.deviceId, 128);
      const action = cleanText(req.body?.action, 20);
      const expiresAt = cleanText(req.body?.expiresAt, 40) || null;
      const d = registry.devices.find(x => x.deviceId === deviceId);
      if (!d) return res.status(404).json({ ok: false, error: "Dispositivo não encontrado." });
      if (action === "approve") {
        d.status = "approved";
        d.approvedAt = new Date().toISOString();
        d.expiresAt = expiresAt;
      } else if (action === "block") d.status = "blocked";
      else if (action === "reject") d.status = "rejected";
      else if (action === "pending") d.status = "pending";
      else return res.status(400).json({ ok: false, error: "Ação inválida." });
      await saveRegistry(registry);
      return res.status(200).json({ ok: true, device: d });
    }
    return res.status(405).json({ ok: false, error: "Método não permitido." });
  } catch (e) {
    return res.status(500).json({ ok: false, error: e?.message || String(e) });
  }
}
