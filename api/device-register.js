import { loadRegistry, saveRegistry, cleanText } from "../lib/access-store.js";

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ ok: false, error: "Método não permitido." });
  try {
    const body = typeof req.body === "object" ? req.body : {};
    const deviceId = cleanText(body.deviceId, 128);
    const name = cleanText(body.name, 100);
    const company = cleanText(body.company, 100);
    const phone = cleanText(body.phone, 40);
    const region = cleanText(body.region, 80);
    const project = cleanText(body.project, 80);
    const deviceModel = cleanText(body.deviceModel, 120);
    if (!deviceId || !name || !region || !project) {
      return res.status(400).json({ ok: false, error: "Preencha nome, região e projeto." });
    }
    const registry = await loadRegistry();
    const now = new Date().toISOString();
    const index = registry.devices.findIndex(d => d.deviceId === deviceId);
    if (index >= 0) {
      registry.devices[index] = {
        ...registry.devices[index], name, company, phone, region, project, deviceModel, lastSeenAt: now
      };
    } else {
      registry.devices.push({
        deviceId, name, company, phone, region, project, deviceModel,
        status: "pending", createdAt: now, lastSeenAt: now, approvedAt: null, expiresAt: null
      });
    }
    await saveRegistry(registry);
    const device = registry.devices.find(d => d.deviceId === deviceId);
    return res.status(200).json({ ok: true, status: device.status, expiresAt: device.expiresAt || null });
  } catch (e) {
    return res.status(500).json({ ok: false, error: e?.message || String(e) });
  }
}
