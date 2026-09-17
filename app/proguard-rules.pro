# ============ DocReader R8 / ProGuard 规则 ============

# ---- 通用属性保留（避免反射相关崩溃）----
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# ---- Kotlin 元数据（协程/反射需要）----
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ---- 协程 ----
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ---- AndroidX / Compose ----
# Compose 运行时通过反射查找 @Composable，需保留
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.platform.** { *; }
-dontwarn androidx.compose.**

# AndroidX Lifecycle ViewModel 通过反射实例化
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# Activity（Manifest 中引用，R8 自动保留，这里显式保险）
-keep class com.dt.docreader.MainActivity { *; }
-keep class com.dt.docreader.DocReaderApp { *; }

# ---- shizuku / 其他（如未使用可移除）----
-dontwarn org.slf4j.**
-dontwarn org.apache.**

# ---- 数据模型（若后续用 Gson/序列化需保留；当前未用，但保险）----
-keep class com.dt.docreader.domain.model.** { *; }

# ---- 优化开关：激进裁剪 ----
-repackageclasses ''
-allowaccessmodification
