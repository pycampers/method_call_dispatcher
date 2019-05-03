#import "MethodCallDispatcherPlugin.h"
#import <method_call_dispatcher/method_call_dispatcher-Swift.h>

@implementation MethodCallDispatcherPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftMethodCallDispatcherPlugin registerWithRegistrar:registrar];
}
@end
