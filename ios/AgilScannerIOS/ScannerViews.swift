import SwiftUI
import VisionKit
import PDFKit

enum ProjectScanMode: String {
    case newPrinter = "IMPRESSORA NOVA"
    case oldPrinter = "IMPRESSORA ANTIGA"
    case rat = "RAT"
}

struct ProjectScannerView: View {
    let mode: ProjectScanMode

    @State private var chamado = ""
    @State private var serial = ""
    @State private var showingScanner = false
    @State private var status = ""

    var body: some View {
        Form {
            Section(mode.rawValue) {
                TextField("Número do chamado", text: $chamado)
                    .keyboardType(.numberPad)

                if mode != .rat {
                    TextField("Serial", text: $serial)
                        .textInputAutocapitalization(.characters)
                }
            }

            Section {
                Button("DIGITALIZAR DOCUMENTO") {
                    showingScanner = true
                }
                .disabled(chamado.trimmingCharacters(in: .whitespaces).isEmpty)
            }

            if !status.isEmpty {
                Section("Status") {
                    Text(status)
                }
            }
        }
        .navigationTitle(mode.rawValue)
        .sheet(isPresented: $showingScanner) {
            DocumentScanner { images in
                showingScanner = false
                guard !images.isEmpty else { return }
                do {
                    let file = try PDFStore.saveProjectPDF(
                        images: images,
                        chamado: chamado,
                        serial: serial,
                        mode: mode
                    )
                    status = "Salvo: (file.lastPathComponent)"
                } catch {
                    status = "Erro ao salvar: (error.localizedDescription)"
                }
            } onCancel: {
                showingScanner = false
            }
        }
    }
}

struct LocalScannerView: View {
    @State private var fileName = ""
    @State private var showingScanner = false
    @State private var savedName = ""

    var body: some View {
        Form {
            Section("DIGITALIZAÇÃO LOCAL") {
                Text(
                    "Este modo salva o documento somente no iPhone. O arquivo nunca entra na fila de envio para a nuvem."
                )
                .font(.footnote)
                .foregroundStyle(.secondary)

                TextField("Nome do arquivo (opcional)", text: $fileName)
            }

            Section {
                Button("DIGITALIZAR DOCUMENTO") {
                    showingScanner = true
                }
            }

            if !savedName.isEmpty {
                Section("Salvo") {
                    Label(savedName, systemImage: "checkmark.circle.fill")
                }
            }
        }
        .navigationTitle("Digitalização Local")
        .sheet(isPresented: $showingScanner) {
            DocumentScanner { images in
                showingScanner = false
                guard !images.isEmpty else { return }
                do {
                    let file = try PDFStore.saveLocalPDF(images: images, label: fileName)
                    savedName = file.lastPathComponent
                } catch {
                    savedName = "Erro: (error.localizedDescription)"
                }
            } onCancel: {
                showingScanner = false
            }
        }
    }
}

struct DocumentScanner: UIViewControllerRepresentable {
    let onFinish: ([UIImage]) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onFinish: onFinish, onCancel: onCancel)
    }

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let controller = VNDocumentCameraViewController()
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: VNDocumentCameraViewController, context: Context) {}

    final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        let onFinish: ([UIImage]) -> Void
        let onCancel: () -> Void

        init(onFinish: @escaping ([UIImage]) -> Void, onCancel: @escaping () -> Void) {
            self.onFinish = onFinish
            self.onCancel = onCancel
        }

        func documentCameraViewController(
            _ controller: VNDocumentCameraViewController,
            didFinishWith scan: VNDocumentCameraScan
        ) {
            var pages: [UIImage] = []
            for index in 0..<scan.pageCount {
                pages.append(scan.imageOfPage(at: index))
            }
            onFinish(pages)
        }

        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
            onCancel()
        }

        func documentCameraViewController(
            _ controller: VNDocumentCameraViewController,
            didFailWithError error: Error
        ) {
            onCancel()
        }
    }
}
