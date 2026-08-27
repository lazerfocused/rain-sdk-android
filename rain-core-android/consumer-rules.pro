# Consumer rules: merged into the host app's R8/ProGuard configuration. web3j resolves ABI
# return types by reflection — TypeReference reads its generic superclass (needs the Signature
# attribute) and TypeDecoder instantiates org.web3j.abi.datatypes.* by class name — so a host with
# minifyEnabled = true would otherwise crash on balance / decimals reads in release builds only.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class * extends org.web3j.abi.TypeReference
-keep class org.web3j.abi.datatypes.** { *; }
