import Foundation
import UIKit

@MainActor
final class AccessController: ObservableObject {
    enum State {
        case checking
        case needsRegistration
        case pending
        case approved(offlineOnly: Bool)
        case blocked(String)
        case error(String)
    }

    @Published var state: State = .checking

    private let baseURL = URL(string: "https://agilscanner.vercel.app")!
    private let defaults = UserDefaults.standard

    private var deviceId: String { DeviceIdentity.value }

    func bootstrap() async {
        if defaults.string(forKey: "profile.name") == nil {
            state = .needsRegistration
            return
        }
        await refresh()
    }

    func register(
        name: String,
        company: String,
        phone: String,
        region: String,
        project: String
    ) async {
        state = .checking

        let body: [String: Any] = [
            "deviceId": deviceId,
            "name": name.trimmingCharacters(in: .whitespacesAndNewlines),
            "company": company.trimmingCharacters(in: .whitespacesAndNewlines),
            "phone": phone.trimmingCharacters(in: .whitespacesAndNewlines),
            "region": region.trimmingCharacters(in: .whitespacesAndNewlines),
            "project": project.trimmingCharacters(in: .whitespacesAndNewlines),
            "deviceModel": "Apple " + UIDevice.current.model
        ]

        do {
            let json = try await post(path: "/api/device-register", body: body)
            guard (json["ok"] as? Bool) == true else {
                throw AccessError.message(json["error"] as? String ?? "Falha no cadastro.")
            }

            defaults.set(name, forKey: "profile.name")
            defaults.set(company, forKey: "profile.company")
            defaults.set(phone, forKey: "profile.phone")
            defaults.set(region, forKey: "profile.region")
            defaults.set(project, forKey: "profile.project")

            let status = json["status"] as? String ?? "pending"
            apply(status: status)
        } catch {
            state = .error(error.localizedDescription)
        }
    }

    func refresh() async {
        state = .checking
        do {
            let json = try await post(
                path: "/api/device-status",
                body: ["deviceId": deviceId]
            )

            guard (json["ok"] as? Bool) == true else {
                throw AccessError.message(json["error"] as? String ?? "Falha ao validar acesso.")
            }

            let status = json["status"] as? String ?? "unregistered"
            apply(status: status)
        } catch {
            if defaults.bool(forKey: "lastKnownApproved") {
                state = .approved(offlineOnly: true)
            } else {
                state = .error("Sem conexão. Conecte-se à internet para validar o primeiro acesso.")
            }
        }
    }

    private func apply(status: String) {
        switch status {
        case "approved":
            defaults.set(true, forKey: "lastKnownApproved")
            state = .approved(offlineOnly: false)
        case "pending":
            state = .pending
        case "blocked":
            defaults.set(false, forKey: "lastKnownApproved")
            state = .blocked("Este aparelho foi bloqueado pela Ágil.")
        case "rejected":
            defaults.set(false, forKey: "lastKnownApproved")
            state = .blocked("Cadastro não autorizado.")
        case "expired":
            defaults.set(false, forKey: "lastKnownApproved")
            state = .blocked("O acesso deste aparelho expirou.")
        default:
            state = .needsRegistration
        }
    }

    private func post(path: String, body: [String: Any]) async throws -> [String: Any] {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.timeoutInterval = 20
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw AccessError.message("Resposta inválida do servidor.")
        }

        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        guard (200...299).contains(http.statusCode) else {
            throw AccessError.message(json["error"] as? String ?? "Erro (http.statusCode).")
        }
        return json
    }
}

private enum AccessError: LocalizedError {
    case message(String)
    var errorDescription: String? {
        switch self {
        case .message(let text): text
        }
    }
}
