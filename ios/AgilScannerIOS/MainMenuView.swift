import SwiftUI

struct MainMenuView: View {
    let offlineOnly: Bool

    var body: some View {
        NavigationStack {
            List {
                if offlineOnly {
                    Section {
                        Label(
                            "Sem internet. Os arquivos do projeto serão enviados quando a conexão voltar.",
                            systemImage: "wifi.slash"
                        )
                    }
                }

                Section("Projeto") {
                    NavigationLink {
                        ProjectScannerView(mode: .newPrinter)
                    } label: {
                        menuRow("IMPRESSORA NOVA", "Equipamento que será instalado", "printer.fill")
                    }

                    NavigationLink {
                        ProjectScannerView(mode: .oldPrinter)
                    } label: {
                        menuRow("IMPRESSORA ANTIGA", "Equipamento que será desinstalado", "printer")
                    }

                    NavigationLink {
                        ProjectScannerView(mode: .rat)
                    } label: {
                        menuRow("RAT DE ATENDIMENTO", "Digitalizar RAT pelo número do chamado", "doc.text.fill")
                    }
                }

                Section("Arquivos") {
                    NavigationLink {
                        SavedFilesView()
                    } label: {
                        menuRow("ARQUIVOS SALVOS", "Visualizar e compartilhar PDFs", "folder.fill")
                    }

                    NavigationLink {
                        LocalScannerView()
                    } label: {
                        menuRow(
                            "DIGITALIZAÇÃO LOCAL",
                            "Salvar somente no iPhone, sem enviar para a nuvem",
                            "iphone"
                        )
                    }
                }
            }
            .navigationTitle("ÁGIL Scanner")
        }
    }

    @ViewBuilder
    private func menuRow(_ title: String, _ subtitle: String, _ icon: String) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.title2)
                .frame(width: 34)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.headline)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 5)
    }
}
