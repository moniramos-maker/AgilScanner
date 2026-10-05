import Foundation
import UIKit
import PDFKit

enum PDFStore {
    static var documentsDirectory: URL {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let dir = base.appendingPathComponent("AgilScanner", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func saveProjectPDF(
        images: [UIImage],
        chamado: String,
        serial: String,
        mode: ProjectScanMode
    ) throws -> URL {
        let normalizedChamado = clean(chamado)
        let normalizedSerial = clean(serial)

        let name: String
        switch mode {
        case .rat:
            name = "(normalizedChamado)_RAT.pdf"
        case .newPrinter:
            name = "(normalizedChamado)_(normalizedSerial.isEmpty ? "SEM_SERIAL" : normalizedSerial)_INSTALADA.pdf"
        case .oldPrinter:
            name = "(normalizedChamado)_(normalizedSerial.isEmpty ? "SEM_SERIAL" : normalizedSerial)_RETIRADA.pdf"
        }

        let url = documentsDirectory.appendingPathComponent(name)
        try createPDF(images: images, at: url)

        PendingUploadStore.shared.enqueue(
            fileURL: url,
            chamado: normalizedChamado
        )

        return url
    }

    static func saveLocalPDF(images: [UIImage], label: String) throws -> URL {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        let stamp = formatter.string(from: Date())

        let cleanLabel = clean(label)
        let name = cleanLabel.isEmpty
            ? "LOCAL_(stamp).pdf"
            : "LOCAL_(cleanLabel)_(stamp).pdf"

        let url = documentsDirectory.appendingPathComponent(name)
        try createPDF(images: images, at: url)
        return url
    }

    static func allPDFs() -> [URL] {
        let files = (try? FileManager.default.contentsOfDirectory(
            at: documentsDirectory,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: [.skipsHiddenFiles]
        )) ?? []

        return files
            .filter { $0.pathExtension.lowercased() == "pdf" }
            .sorted {
                let a = (try? $0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                let b = (try? $1.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                return a > b
            }
    }

    private static func createPDF(images: [UIImage], at url: URL) throws {
        let document = PDFDocument()
        for (index, image) in images.enumerated() {
            if let page = PDFPage(image: image) {
                document.insert(page, at: index)
            }
        }
        guard document.write(to: url) else {
            throw NSError(
                domain: "AgilScanner",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Não foi possível gerar o PDF."]
            )
        }
    }

    private static func clean(_ value: String) -> String {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "_-"))
        return value
            .uppercased()
            .replacingOccurrences(of: " ", with: "_")
            .unicodeScalars
            .map { allowed.contains($0) ? String($0) : "_" }
            .joined()
            .trimmingCharacters(in: CharacterSet(charactersIn: "_"))
    }
}

final class PendingUploadStore {
    static let shared = PendingUploadStore()

    struct Item: Codable, Hashable {
        let path: String
        let chamado: String
    }

    private let key = "ios.pending.project.uploads"

    func enqueue(fileURL: URL, chamado: String) {
        var items = load()
        let item = Item(path: fileURL.path, chamado: chamado)
        if !items.contains(item) {
            items.append(item)
            save(items)
        }
    }

    func load() -> [Item] {
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([Item].self, from: data)) ?? []
    }

    func remove(_ item: Item) {
        save(load().filter { $0 != item })
    }

    private func save(_ items: [Item]) {
        let data = try? JSONEncoder().encode(items)
        UserDefaults.standard.set(data, forKey: key)
    }
}
