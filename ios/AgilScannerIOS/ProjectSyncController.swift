import Foundation
import Network

@MainActor
final class ProjectSyncController: ObservableObject {
    static let shared = ProjectSyncController()

    @Published private(set) var pendingCount: Int = 0
    @Published private(set) var lastStatus: String = ""

    private let monitor = NWPathMonitor()
    private let monitorQueue = DispatchQueue(label: "com.agil.scanner.ios.network")
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        refreshCount()

        monitor.pathUpdateHandler = { [weak self] path in
            guard path.status == .satisfied else { return }
            Task { @MainActor in
                await self?.syncNow()
            }
        }
        monitor.start(queue: monitorQueue)

        Task {
            await syncNow()
        }
    }

    func refreshCount() {
        pendingCount = PendingUploadStore.shared.load().count
    }

    func syncNow() async {
        let items = PendingUploadStore.shared.load()
        guard !items.isEmpty else {
            refreshCount()
            return
        }

        for item in items {
            let fileURL = URL(fileURLWithPath: item.path)

            guard FileManager.default.fileExists(atPath: fileURL.path) else {
                PendingUploadStore.shared.remove(item)
                continue
            }

            do {
                let ok = try await upload(fileURL: fileURL, chamado: item.chamado)

                if ok {
                    PendingUploadStore.shared.remove(item)
                    lastStatus = "Sincronizado ✓"
                } else {
                    lastStatus = "Pendente de sincronização"
                    refreshCount()
                    return
                }
            } catch {
                lastStatus = "Pendente de sincronização"
                refreshCount()
                return
            }
        }

        refreshCount()
    }

    private func upload(fileURL: URL, chamado: String) async throws -> Bool {
        guard let url = URL(string: "https://agilscanner.vercel.app/api/device-upload") else {
            return false
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 45
        request.setValue("application/pdf", forHTTPHeaderField: "Content-Type")
        request.setValue(DeviceIdentity.value, forHTTPHeaderField: "X-Device-Id")
        request.setValue(chamado, forHTTPHeaderField: "X-Chamado")
        request.setValue(fileURL.lastPathComponent, forHTTPHeaderField: "X-File-Name")
        request.httpBody = try Data(contentsOf: fileURL)

        let (_, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            return false
        }

        if http.statusCode == 403 {
            lastStatus = "Sincronização pausada: aparelho não autorizado."
            return false
        }

        return (200...299).contains(http.statusCode)
    }
}
