import SwiftUI
import QuickLook

struct SavedFilesView: View {
    @State private var files: [URL] = PDFStore.allPDFs()
    @State private var previewURL: URL?
    @State private var shareURL: URL?

    var body: some View {
        List {
            if files.isEmpty {
                Text("Nenhum PDF salvo ainda.")
                    .foregroundStyle(.secondary)
            }

            ForEach(files, id: \.self) { file in
                VStack(alignment: .leading, spacing: 8) {
                    Text(file.lastPathComponent)
                        .font(.headline)
                        .lineLimit(2)

                    HStack {
                        Button("Visualizar") {
                            previewURL = file
                        }
                        .buttonStyle(.bordered)

                        Button("Compartilhar") {
                            shareURL = file
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
                .padding(.vertical, 6)
            }
            .onDelete { indexSet in
                for index in indexSet {
                    try? FileManager.default.removeItem(at: files[index])
                }
                files = PDFStore.allPDFs()
            }
        }
        .navigationTitle("Arquivos Salvos")
        .toolbar {
            Button {
                files = PDFStore.allPDFs()
            } label: {
                Image(systemName: "arrow.clockwise")
            }
        }
        .sheet(item: $previewURL) { url in
            QuickLookPreview(url: url)
        }
        .sheet(item: $shareURL) { url in
            ShareSheet(items: [url])
        }
    }
}

private struct QuickLookPreview: UIViewControllerRepresentable {
    let url: URL

    func makeCoordinator() -> Coordinator {
        Coordinator(url: url)
    }

    func makeUIViewController(context: Context) -> QLPreviewController {
        let controller = QLPreviewController()
        controller.dataSource = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: QLPreviewController, context: Context) {}

    final class Coordinator: NSObject, QLPreviewControllerDataSource {
        let url: URL
        init(url: URL) { self.url = url }

        func numberOfPreviewItems(in controller: QLPreviewController) -> Int { 1 }

        func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
            url as NSURL
        }
    }
}

private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

extension URL: Identifiable {
    public var id: String { absoluteString }
}
