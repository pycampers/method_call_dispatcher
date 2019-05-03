import Flutter
import UIKit

public class SwiftMethodCallDispatcherPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "method_call_dispatcher", binaryMessenger: registrar.messenger())
    let instance = SwiftMethodCallDispatcherPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    result("iOS " + UIDevice.current.systemVersion)
  }
}
