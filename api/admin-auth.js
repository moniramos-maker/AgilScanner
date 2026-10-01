export default function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({ ok: false, error: "Método não permitido." });
  }
  const expected = process.env.ADMIN_KEY || "";
  const supplied = String(req.headers["x-admin-key"] || "");
  if (!expected || !supplied || supplied !== expected) {
    return res.status(401).json({ ok: false, error: "Chave inválida." });
  }
  return res.status(200).json({ ok: true });
}
