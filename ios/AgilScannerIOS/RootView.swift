import SwiftUI

struct RootView: View {
    @EnvironmentObject var access: AccessController

    var body: some View {
        Group {
            switch access.state {
            case .approved(let offlineOnly):
                MainMenuView(offlineOnly: offlineOnly)
            case .pending:
                PendingAccessView()
            case .blocked(let message):
                BlockedView(message: message)
            case .needsRegistration:
                RegistrationView()
            case .checking:
                ProgressView("Validando acesso...")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .error(let message):
                ErrorAccessView(message: message)
            }
        }
        .background(Color(.systemGroupedBackground))
    }
}

private struct RegistrationView: View {
    @EnvironmentObject var access: AccessController
    @State private var name = ""
    @State private var company = ""
    @State private var phone = ""
    @State private var region = ""
    @State private var project = "Banco do Brasil - SONDA"

    var body: some View {
        NavigationStack {
            Form {
                Section("Solicitar acesso") {
                    TextField("Nome do técnico", text: $name)
                    TextField("Empresa / Prestador", text: $company)
                    TextField("Telefone", text: $phone)
                        .keyboardType(.phonePad)
                    TextField("Estado / Região", text: $region)
                    TextField("Projeto", text: $project)
                }

                Section {
                    Button("ENVIAR PARA APROVAÇÃO") {
                        Task {
                            await access.register(
                                name: name,
                                company: company,
                                phone: phone,
                                region: region,
                                project: project
                            )
                        }
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty ||
                              region.trimmingCharacters(in: .whitespaces).isEmpty ||
                              project.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .navigationTitle("ÁGIL Scanner")
        }
    }
}

private struct PendingAccessView: View {
    @EnvironmentObject var access: AccessController

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "clock.badge.exclamationmark")
                .font(.system(size: 52))
            Text("Aguardando aprovação")
                .font(.title2.bold())
            Text("Seu cadastro foi enviado para a Ágil.")
                .foregroundStyle(.secondary)
            Button("VERIFICAR LIBERAÇÃO") {
                Task { await access.refresh() }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
    }
}

private struct BlockedView: View {
    let message: String

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "lock.fill")
                .font(.system(size: 52))
            Text("Acesso indisponível")
                .font(.title2.bold())
            Text(message)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}

private struct ErrorAccessView: View {
    @EnvironmentObject var access: AccessController
    let message: String

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "wifi.exclamationmark")
                .font(.system(size: 52))
            Text(message)
                .multilineTextAlignment(.center)
            Button("TENTAR NOVAMENTE") {
                Task { await access.refresh() }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
    }
}
