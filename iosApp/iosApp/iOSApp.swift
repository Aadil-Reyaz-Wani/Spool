import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        KoinIOSKt.startKoinIOS()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}