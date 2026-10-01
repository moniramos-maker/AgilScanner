import { loadRegistry, isExpired, cleanText } from "../lib/access-store.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ ok: false, error: "Método não permitido." });
  try {
    const deviceId = cleanText(req.body?.deviceId, 128);
    if (!deviceId) return res.status(400).json({ ok: false, error: "deviceId ausente." });
    const registry = await loadRegistry();
    const device = registry.devices.find(d => d.deviceId === deviceId);
    if (!device) return res.status(200).json({ ok: true, status: "unregistered" });
    const status = isExpired(device) ? "expired" : device.status;
    return res.status(200).json({
      ok: true, status, name: device.name, region: device.region,
      project: device.project, expiresAt: device.expiresAt || null
    });
  } catch (e) {
    return res.status(500).json({ ok: false, error: e?.message || String(e) });
  }
}
