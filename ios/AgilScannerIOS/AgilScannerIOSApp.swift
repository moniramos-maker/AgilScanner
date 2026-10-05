import SwiftUI

@main
struct AgilScannerIOSApp: App {
    @StateObject private var access = AccessController()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(access)
                .task {
                    await access.bootstrap()
                }
        }
    }
}
