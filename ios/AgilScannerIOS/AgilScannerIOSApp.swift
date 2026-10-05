import SwiftUI

@main
struct AgilScannerIOSApp: App {
    @StateObject private var access = AccessController()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(access)
                .task {
                    ProjectSyncController.shared.start()
                    await access.bootstrap()
                }
        }
    }
}
