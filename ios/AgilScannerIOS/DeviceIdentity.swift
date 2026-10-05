import Foundation
import UIKit
import CryptoKit

enum DeviceIdentity {
    static var value: String {
        let raw = UIDevice.current.identifierForVendor?.uuidString ?? "unknown"
        let source = raw + "|com.agil.scanner.ios"
        let digest = SHA256.hash(data: Data(source.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
