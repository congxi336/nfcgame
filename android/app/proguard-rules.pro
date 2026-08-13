# 保留 Retrofit/Gson 数据模型字段（release 混淆时避免被裁剪）
-keep class com.nfcgame.app.network.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
