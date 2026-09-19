#!/usr/bin/env bash
set -euo pipefail

ROOT="$1"
rm -rf "$ROOT"
mkdir -p \
  "$ROOT/core/ui/src/main/java/com/fush/market/core/ui" \
  "$ROOT/core/ui/src/main/res/raw" \
  "$ROOT/core/ui/src/main/res/values" \
  "$ROOT/apps/customer/src/main/java/com/fush/market/customer" \
  "$ROOT/apps/customer/src/main/res/drawable" \
  "$ROOT/apps/customer/src/main/res/values" \
  "$ROOT/backend/supabase/migrations"

cp "$GITHUB_WORKSPACE/assets/andak_logo.png" "$ROOT/core/ui/src/main/res/raw/fush_logo.png"

cat > "$ROOT/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "AndakCustomer"
include(":core:ui", ":apps:customer")
EOF

cat > "$ROOT/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("com.android.library") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
EOF

cat > "$ROOT/gradle.properties" <<'EOF'
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
EOF

cat > "$ROOT/core/ui/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.fush.market.core.ui"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
EOF

cat > "$ROOT/core/ui/src/main/res/values/styles.xml" <<'EOF'
<resources>
    <style name="Theme.Andak" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:statusBarColor">#F8F7F2</item>
        <item name="android:navigationBarColor">#FFFFFF</item>
        <item name="android:windowActionModeOverlay">true</item>
    </style>
</resources>
EOF

cat > "$ROOT/core/ui/src/main/java/com/fush/market/core/ui/AndakTheme.kt" <<'EOF'
package com.fush.market.core.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AndakGreen = Color(0xFF006B57)
val AndakDeepGreen = Color(0xFF004D40)
val AndakGold = Color(0xFFE6B83F)
val AndakCream = Color(0xFFF8F7F2)
val AndakMuted = Color(0xFF68716D)

@Composable
fun AndakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AndakGreen,
            secondary = AndakGold,
            background = AndakCream,
            surface = Color.White,
            onPrimary = Color.White,
            onSecondary = Color(0xFF3F3100),
            onBackground = AndakDeepGreen,
            onSurface = Color(0xFF1E2623)
        ),
        content = content
    )
}

@Composable
fun AndakLogo(modifier: Modifier = Modifier, size: Dp = 54.dp) {
    val context = LocalContext.current
    val bitmap = remember {
        runCatching {
            context.resources.openRawResource(R.raw.fush_logo).use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "شعار عندك",
            modifier = modifier.size(size),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.size(size).background(AndakGreen, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("عندك", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
EOF

cat > "$ROOT/apps/customer/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.fush.market.customer"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.fush.market.customer"
        minSdk = 23
        targetSdk = 36
        versionCode = 15
        versionName = "0.1.14"
    }
    val andakSupabaseUrl = providers.gradleProperty("andakSupabaseUrl").orElse("").get()
    val andakSupabaseKey = providers.gradleProperty("andakSupabaseKey").orElse("").get()
    buildFeatures {
        compose = true
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "ANDAK_SUPABASE_URL", "\"${andakSupabaseUrl}\"")
        buildConfigField("String", "ANDAK_SUPABASE_KEY", "\"${andakSupabaseKey}\"")
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":core:ui"))
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
EOF

cat > "$ROOT/apps/customer/src/main/res/values/strings.xml" <<'EOF'
<resources>
    <string name="app_name">عندك</string>
</resources>
EOF

cat > "$ROOT/apps/customer/src/main/res/values/styles.xml" <<'EOF'
<resources>
    <style name="Theme.Andak.Customer" parent="@style/Theme.Andak" />
</resources>
EOF

cat > "$ROOT/apps/customer/src/main/res/drawable/ic_launcher.xml" <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#006B57" android:pathData="M54,0A54,54 0,1 0,54 108A54,54 0,1 0,54 0" />
    <path android:fillColor="#E6B83F" android:pathData="M31,34h46l-4,42h-38z" />
    <path android:fillColor="#FFFFFF" android:pathData="M39,34c0,-9 6,-16 15,-16s15,7 15,16h-7c0,-5 -3,-9 -8,-9s-8,4 -8,9z" />
</vector>
EOF

cat > "$ROOT/apps/customer/src/main/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:allowBackup="false"
        android:icon="@drawable/ic_launcher"
        android:roundIcon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Andak.Customer">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

cat > "$ROOT/apps/customer/src/main/java/com/fush/market/customer/CatalogModels.kt" <<'EOF'
package com.fush.market.customer

data class CatalogCategory(
    val id: String,
    val name: String,
    val emoji: String
)

data class ProductVariant(
    val id: String,
    val label: String,
    val unit: String,
    val priceYER: Long,
    val availability: Availability
)

enum class Availability { AVAILABLE, LIMITED, OUT }

data class CatalogProduct(
    val id: String,
    val categoryId: String,
    val brand: String,
    val name: String,
    val emoji: String,
    val description: String,
    val variants: List<ProductVariant>,
    val featured: Boolean = false
)

data class CartLine(
    val productId: String,
    val variantId: String,
    val quantity: Int
)
EOF

cat > "$ROOT/apps/customer/src/main/java/com/fush/market/customer/CatalogSeed.kt" <<'EOF'
package com.fush.market.customer

object CatalogSeed {
    val categories = listOf(
        CatalogCategory("groceries", "بقالة", "🛒"),
        CatalogCategory("drinks", "مشروبات", "🥤"),
        CatalogCategory("care", "عناية شخصية", "🧴"),
        CatalogCategory("home", "المنزل", "🏠"),
        CatalogCategory("electronics", "إلكترونيات", "🎧")
    )

    val products = listOf(
        CatalogProduct(
            "milk-1", "groceries", "نادك", "حليب كامل الدسم", "🥛",
            "حليب يومي مناسب للاستخدام المنزلي.",
            listOf(
                ProductVariant("milk-1-1l", "1 لتر", "علبة", 1200, Availability.AVAILABLE),
                ProductVariant("milk-1-200", "200 مل", "علبة", 450, Availability.AVAILABLE)
            ), true
        ),
        CatalogProduct(
            "rice-1", "groceries", "الروابي", "أرز بسمتي فاخر", "🍚",
            "أرز بسمتي طويل الحبة للاستخدام اليومي.",
            listOf(
                ProductVariant("rice-1-5kg", "5 كجم", "كيس", 9800, Availability.LIMITED),
                ProductVariant("rice-1-2kg", "2 كجم", "كيس", 4300, Availability.AVAILABLE)
            ), true
        ),
        CatalogProduct(
            "cola-1", "drinks", "كوكاكولا", "مشروب غازي", "🥤",
            "مشروب غازي بارد بعدة أحجام.",
            listOf(
                ProductVariant("cola-1-250", "250 مل", "علبة", 500, Availability.AVAILABLE),
                ProductVariant("cola-1-1l", "1 لتر", "قارورة", 1100, Availability.AVAILABLE)
            ), true
        ),
        CatalogProduct(
            "water-1", "drinks", "حده", "مياه معدنية", "💧",
            "مياه شرب معبأة للاستخدام اليومي.",
            listOf(
                ProductVariant("water-1-750", "750 مل", "قارورة", 350, Availability.AVAILABLE),
                ProductVariant("water-1-pack", "باكيت 12", "باكيت", 3900, Availability.LIMITED)
            )
        ),
        CatalogProduct(
            "shampoo-1", "care", "كلير", "شامبو للعناية بالشعر", "🧴",
            "شامبو للاستخدام اليومي مع خيارات حجم متعددة.",
            listOf(
                ProductVariant("shampoo-1-400", "400 مل", "عبوة", 4200, Availability.AVAILABLE),
                ProductVariant("shampoo-1-200", "200 مل", "عبوة", 2500, Availability.AVAILABLE)
            ), true
        ),
        CatalogProduct(
            "soap-1", "care", "لوكس", "صابون استحمام", "🧼",
            "صابون للاستخدام الشخصي اليومي.",
            listOf(ProductVariant("soap-1-single", "قطعة", "قطعة", 650, Availability.AVAILABLE))
        ),
        CatalogProduct(
            "tissues-1", "home", "فاين", "مناديل ورقية", "🧻",
            "مناديل ورقية ناعمة للاستخدام المنزلي.",
            listOf(
                ProductVariant("tissues-1-box", "علبة", "علبة", 1300, Availability.AVAILABLE),
                ProductVariant("tissues-1-pack", "باكيت 5", "باكيت", 5700, Availability.LIMITED)
            )
        ),
        CatalogProduct(
            "cleaner-1", "home", "ديتول", "منظف متعدد الاستخدام", "✨",
            "منظف للاستخدام المنزلي على الأسطح المناسبة.",
            listOf(ProductVariant("cleaner-1-500", "500 مل", "عبوة", 3800, Availability.AVAILABLE))
        ),
        CatalogProduct(
            "earbuds-1", "electronics", "Generic", "سماعات بلوتوث", "🎧",
            "سماعات لاسلكية للاستخدام اليومي.",
            listOf(ProductVariant("earbuds-1-black", "أسود", "قطعة", 12500, Availability.LIMITED)), true
        ),
        CatalogProduct(
            "charger-1", "electronics", "Generic", "شاحن سريع USB-C", "🔌",
            "شاحن USB-C للاستخدام مع الأجهزة المتوافقة.",
            listOf(ProductVariant("charger-1-20w", "20W", "قطعة", 7500, Availability.AVAILABLE))
        )
    )
}
EOF


cat > "$ROOT/apps/customer/src/main/java/com/fush/market/customer/BackendCatalog.kt" <<'EOF'
package com.fush.market.customer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class BackendCatalogSnapshot(
    val categories: List<CatalogCategory>,
    val products: List<CatalogProduct>
)

object BackendCatalogGateway {
    fun isConfigured(): Boolean {
        val url = BuildConfig.ANDAK_SUPABASE_URL.trim()
        val key = BuildConfig.ANDAK_SUPABASE_KEY.trim()
        val validProtocol = url.startsWith("https://") || url.startsWith("http://")
        return validProtocol &&
            key.isNotBlank() &&
            !url.contains("\${") &&
            !key.contains("\${")
    }

    suspend fun fetchCatalog(): Result<BackendCatalogSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            require(isConfigured()) { "Supabase endpoint is not configured" }
            val endpoint = BuildConfig.ANDAK_SUPABASE_URL.trimEnd('/') +
                "/rest/v1/andak_customer_catalog_v1?select=*&order=category_sort.asc,product_sort.asc,variant_sort.asc"

            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            connection.setRequestProperty("apikey", BuildConfig.ANDAK_SUPABASE_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.ANDAK_SUPABASE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    error("Catalog HTTP " + code + " " + message)
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseCatalog(JSONArray(body))
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseCatalog(rows: JSONArray): BackendCatalogSnapshot {
        val categories = linkedMapOf<String, CatalogCategory>()
        data class Row(
            val productId: String,
            val categoryId: String,
            val brand: String,
            val productName: String,
            val emoji: String,
            val description: String,
            val featured: Boolean,
            val variant: ProductVariant
        )
        val parsedRows = mutableListOf<Row>()

        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val categoryId = row.getString("category_id")
            categories.putIfAbsent(
                categoryId,
                CatalogCategory(
                    id = categoryId,
                    name = row.getString("category_name"),
                    emoji = row.optString("category_emoji", "🛍️")
                )
            )
            val availability = when (row.optString("availability").uppercase()) {
                "OUT" -> Availability.OUT
                "LIMITED" -> Availability.LIMITED
                else -> Availability.AVAILABLE
            }
            parsedRows += Row(
                productId = row.getString("product_id"),
                categoryId = categoryId,
                brand = row.optString("brand_name", ""),
                productName = row.getString("product_name"),
                emoji = row.optString("product_emoji", "📦"),
                description = row.optString("description", ""),
                featured = row.optBoolean("featured", false),
                variant = ProductVariant(
                    id = row.getString("variant_id"),
                    label = row.getString("variant_label"),
                    unit = row.getString("unit_name"),
                    priceYER = row.getLong("price_yer"),
                    availability = availability
                )
            )
        }

        val products = parsedRows.groupBy { it.productId }.map { (productId, group) ->
            val first = group.first()
            CatalogProduct(
                id = productId,
                categoryId = first.categoryId,
                brand = first.brand,
                name = first.productName,
                emoji = first.emoji,
                description = first.description,
                variants = group.map { it.variant },
                featured = first.featured
            )
        }

        return BackendCatalogSnapshot(categories.values.toList(), products)
    }
}
EOF


cat > "$ROOT/apps/customer/src/main/java/com/fush/market/customer/BackendOrder.kt" <<'EOF'
package com.fush.market.customer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class OrderCustomerInput(
    val fullName: String,
    val phone: String,
    val city: String,
    val neighborhood: String,
    val addressDetails: String,
    val note: String
)

data class OrderLineInput(
    val variantId: String,
    val quantity: Int
)

data class CreateOrderResult(
    val orderId: String,
    val orderNumber: String,
    val status: String,
    val subtotalYER: Long,
    val deliveryFeeYER: Long,
    val totalYER: Long,
    val requestId: String,
    val trackingToken: String
)

data class OrderStatusLine(
    val variantId: String,
    val productName: String,
    val variantLabel: String,
    val unitName: String,
    val quantity: Double,
    val unitPriceYER: Long,
    val lineTotalYER: Long
)

data class OrderStatusResult(
    val orderNumber: String,
    val status: String,
    val paymentMethod: String,
    val subtotalYER: Long,
    val deliveryFeeYER: Long,
    val totalYER: Long,
    val city: String,
    val neighborhood: String,
    val createdAt: String,
    val lines: List<OrderStatusLine>
)

object BackendOrderGateway {
    fun isConfigured(): Boolean = BackendCatalogGateway.isConfigured()

    suspend fun createCodOrder(
        customer: OrderCustomerInput,
        lines: List<OrderLineInput>,
        accessToken: String? = null,
        requestId: String = UUID.randomUUID().toString()
    ): Result<CreateOrderResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(isConfigured()) { "ANDAK backend is not configured" }
            require(lines.isNotEmpty()) { "Order lines are empty" }

            val lineArray = JSONArray()
            lines.forEach { line ->
                lineArray.put(
                    JSONObject()
                        .put("variant_id", line.variantId)
                        .put("quantity", line.quantity)
                )
            }

            val payload = JSONObject()
                .put("p_request_id", requestId)
                .put("p_customer_name", customer.fullName.trim())
                .put("p_phone", customer.phone.trim())
                .put("p_city", customer.city.trim())
                .put("p_neighborhood", customer.neighborhood.trim())
                .put("p_address_details", customer.addressDetails.trim())
                .put("p_note", customer.note.trim())
                .put("p_lines", lineArray)

            val body = postRpc("andak_create_order_v3", payload, accessToken)
            val result = JSONObject(body)
            CreateOrderResult(
                orderId = result.getString("order_id"),
                orderNumber = result.getString("order_number"),
                status = result.getString("status"),
                subtotalYER = result.getLong("subtotal_yer"),
                deliveryFeeYER = result.getLong("delivery_fee_yer"),
                totalYER = result.getLong("total_yer"),
                requestId = result.optString("request_id", requestId),
                trackingToken = result.getString("tracking_token")
            )
        }
    }

    suspend fun fetchOrderStatus(trackingToken: String): Result<OrderStatusResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isConfigured()) { "ANDAK backend is not configured" }
                require(trackingToken.isNotBlank()) { "Tracking token is required" }

                val payload = JSONObject().put("p_tracking_token", trackingToken)
                val body = postRpc("andak_get_order_status_v1", payload)
                val result = JSONObject(body)
                val rawLines = result.optJSONArray("lines") ?: JSONArray()
                val lines = buildList {
                    for (index in 0 until rawLines.length()) {
                        val row = rawLines.getJSONObject(index)
                        add(
                            OrderStatusLine(
                                variantId = row.optString("variant_id", ""),
                                productName = row.optString("product_name", ""),
                                variantLabel = row.optString("variant_label", ""),
                                unitName = row.optString("unit_name", ""),
                                quantity = row.optDouble("quantity", 0.0),
                                unitPriceYER = row.optLong("unit_price_yer", 0L),
                                lineTotalYER = row.optLong("line_total_yer", 0L)
                            )
                        )
                    }
                }

                OrderStatusResult(
                    orderNumber = result.getString("order_number"),
                    status = result.getString("status"),
                    paymentMethod = result.optString("payment_method", "COD"),
                    subtotalYER = result.optLong("subtotal_yer", 0L),
                    deliveryFeeYER = result.optLong("delivery_fee_yer", 0L),
                    totalYER = result.optLong("total_yer", 0L),
                    city = result.optString("city", ""),
                    neighborhood = result.optString("neighborhood", ""),
                    createdAt = result.optString("created_at", ""),
                    lines = lines
                )
            }
        }

    private fun postRpc(functionName: String, payload: JSONObject, bearer: String? = null): String {
        val endpoint = BuildConfig.ANDAK_SUPABASE_URL.trimEnd('/') +
            "/rest/v1/rpc/" + functionName

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.setRequestProperty("apikey", BuildConfig.ANDAK_SUPABASE_KEY)
        connection.setRequestProperty(
            "Authorization",
            "Bearer " + (bearer?.takeIf { it.isNotBlank() } ?: BuildConfig.ANDAK_SUPABASE_KEY)
        )
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")

        try {
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (code !in 200..299) {
                error("RPC " + functionName + " HTTP " + code + " " + body)
            }
            return body
        } finally {
            connection.disconnect()
        }
    }
}
EOF

cat > "$ROOT/apps/customer/src/main/java/com/fush/market/customer/OrderReceiptStore.kt" <<'EOF'
package com.fush.market.customer

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.andakOrderDataStore by preferencesDataStore(name = "andak_order_receipts")

data class OrderReceipt(
    val reference: String,
    val serverCreated: Boolean,
    val trackingToken: String,
    val status: String,
    val totalYER: Long,
    val createdAtMillis: Long
)

object OrderReceiptStore {
    private val receiptsKey = stringPreferencesKey("receipts_json")

    fun observe(context: Context): Flow<List<OrderReceipt>> =
        context.andakOrderDataStore.data.map { prefs ->
            decode(prefs[receiptsKey].orEmpty())
        }

    suspend fun upsert(context: Context, receipt: OrderReceipt) {
        context.andakOrderDataStore.edit { prefs ->
            val current = decode(prefs[receiptsKey].orEmpty()).toMutableList()
            val index = current.indexOfFirst { it.reference == receipt.reference }
            if (index >= 0) current[index] = receipt else current.add(0, receipt)
            prefs[receiptsKey] = encode(current.take(30))
        }
    }

    private fun encode(receipts: List<OrderReceipt>): String {
        val array = JSONArray()
        receipts.forEach { receipt ->
            array.put(
                JSONObject()
                    .put("reference", receipt.reference)
                    .put("serverCreated", receipt.serverCreated)
                    .put("trackingToken", receipt.trackingToken)
                    .put("status", receipt.status)
                    .put("totalYER", receipt.totalYER)
                    .put("createdAtMillis", receipt.createdAtMillis)
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<OrderReceipt> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val row = array.getJSONObject(index)
                    add(
                        OrderReceipt(
                            reference = row.optString("reference", ""),
                            serverCreated = row.optBoolean("serverCreated", false),
                            trackingToken = row.optString("trackingToken", ""),
                            status = row.optString("status", if (row.optBoolean("serverCreated", false)) "PENDING_CONFIRMATION" else "DRAFT_LOCAL"),
                            totalYER = row.optLong("totalYER", 0L),
                            createdAtMillis = row.optLong("createdAtMillis", 0L)
                        )
                    )
                }
            }.filter { it.reference.isNotBlank() }
        }.getOrDefault(emptyList())
    }
}
EOF

cat > "$ROOT/apps/customer/src/main/java/com/fush/market/customer/AddressStore.kt" <<'EOF'
package com.fush.market.customer

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.andakAddressDataStore by preferencesDataStore(name = "andak_saved_addresses")

data class SavedAddress(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val fullName: String,
    val phone: String,
    val city: String,
    val neighborhood: String,
    val details: String,
    val isDefault: Boolean = false
)

object AddressStore {
    private val addressesKey = stringPreferencesKey("addresses_json")

    fun observe(context: Context): Flow<List<SavedAddress>> =
        context.andakAddressDataStore.data.map { prefs ->
            decode(prefs[addressesKey].orEmpty())
        }

    suspend fun upsert(context: Context, address: SavedAddress) {
        context.andakAddressDataStore.edit { prefs ->
            val current = decode(prefs[addressesKey].orEmpty()).toMutableList()
            val normalized = if (address.isDefault || current.isEmpty()) {
                current.map { it.copy(isDefault = false) }.toMutableList().also { list ->
                    val index = list.indexOfFirst { it.id == address.id }
                    val value = address.copy(isDefault = true)
                    if (index >= 0) list[index] = value else list.add(0, value)
                }
            } else {
                current.also { list ->
                    val index = list.indexOfFirst { it.id == address.id }
                    if (index >= 0) list[index] = address else list.add(0, address)
                }
            }
            prefs[addressesKey] = encode(normalized.take(10))
        }
    }

    suspend fun setDefault(context: Context, id: String) {
        context.andakAddressDataStore.edit { prefs ->
            val current = decode(prefs[addressesKey].orEmpty())
            prefs[addressesKey] = encode(
                current.map { it.copy(isDefault = it.id == id) }
            )
        }
    }

    suspend fun delete(context: Context, id: String) {
        context.andakAddressDataStore.edit { prefs ->
            val remaining = decode(prefs[addressesKey].orEmpty())
                .filterNot { it.id == id }
                .toMutableList()
            if (remaining.isNotEmpty() && remaining.none { it.isDefault }) {
                remaining[0] = remaining[0].copy(isDefault = true)
            }
            prefs[addressesKey] = encode(remaining)
        }
    }

    suspend fun replaceAll(context: Context, addresses: List<SavedAddress>) {
        context.andakAddressDataStore.edit { prefs ->
            prefs[addressesKey] = encode(addresses.take(10))
        }
    }

    private fun encode(addresses: List<SavedAddress>): String {
        val array = JSONArray()
        addresses.forEach { address ->
            array.put(
                JSONObject()
                    .put("id", address.id)
                    .put("label", address.label)
                    .put("fullName", address.fullName)
                    .put("phone", address.phone)
                    .put("city", address.city)
                    .put("neighborhood", address.neighborhood)
                    .put("details", address.details)
                    .put("isDefault", address.isDefault)
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<SavedAddress> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val row = array.getJSONObject(index)
                    add(
                        SavedAddress(
                            id = row.optString("id", UUID.randomUUID().toString()),
                            label = row.optString("label", "عنوان"),
                            fullName = row.optString("fullName", ""),
                            phone = row.optString("phone", ""),
                            city = row.optString("city", ""),
                            neighborhood = row.optString("neighborhood", ""),
                            details = row.optString("details", ""),
                            isDefault = row.optBoolean("isDefault", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
EOF

cat > "$ROOT/apps/customer/src/main/java/com/fush/market/customer/LocalCommerceStore.kt" <<'EOF'
package com.fush.market.customer

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.andakCommerceDataStore by preferencesDataStore(name = "andak_local_commerce")

object LocalCommerceStore {
    private val cartKey = stringPreferencesKey("cart_json")
    private val favoritesKey = stringPreferencesKey("favorites_json")

    suspend fun loadCart(context: Context): List<CartLine> {
        val raw = context.andakCommerceDataStore.data.first()[cartKey].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val row = array.getJSONObject(index)
                    val quantity = row.optInt("quantity", 0)
                    if (quantity > 0) {
                        add(
                            CartLine(
                                productId = row.optString("productId", ""),
                                variantId = row.optString("variantId", ""),
                                quantity = quantity
                            )
                        )
                    }
                }
            }.filter { it.productId.isNotBlank() && it.variantId.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    suspend fun saveCart(context: Context, cart: List<CartLine>) {
        val array = JSONArray()
        cart.filter { it.quantity > 0 }.forEach { line ->
            array.put(
                JSONObject()
                    .put("productId", line.productId)
                    .put("variantId", line.variantId)
                    .put("quantity", line.quantity)
            )
        }
        context.andakCommerceDataStore.edit { prefs ->
            prefs[cartKey] = array.toString()
        }
    }

    suspend fun loadFavorites(context: Context): List<String> {
        val raw = context.andakCommerceDataStore.data.first()[favoritesKey].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val id = array.optString(index, "")
                    if (id.isNotBlank()) add(id)
                }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    suspend fun saveFavorites(context: Context, productIds: Collection<String>) {
        val array = JSONArray()
        productIds.filter { it.isNotBlank() }.distinct().forEach(array::put)
        context.andakCommerceDataStore.edit { prefs ->
            prefs[favoritesKey] = array.toString()
        }
    }
}
EOF

cat > "$ROOT/apps/customer/src/main/java/com/fush/market/customer/SupportPreferencesStore.kt" <<'EOF'
package com.fush.market.customer

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.andakSupportDataStore by preferencesDataStore(name = "andak_support_preferences")

data class CustomerPreferences(
    val orderUpdates: Boolean = true,
    val offers: Boolean = false
)

object SupportPreferencesStore {
    private val orderUpdatesKey = booleanPreferencesKey("order_updates")
    private val offersKey = booleanPreferencesKey("offers")
    private val supportDraftKey = stringPreferencesKey("support_draft")

    fun observePreferences(context: Context): Flow<CustomerPreferences> =
        context.andakSupportDataStore.data.map { prefs ->
            CustomerPreferences(
                orderUpdates = prefs[orderUpdatesKey] ?: true,
                offers = prefs[offersKey] ?: false
            )
        }

    suspend fun setOrderUpdates(context: Context, enabled: Boolean) {
        context.andakSupportDataStore.edit { it[orderUpdatesKey] = enabled }
    }

    suspend fun setOffers(context: Context, enabled: Boolean) {
        context.andakSupportDataStore.edit { it[offersKey] = enabled }
    }

    fun observeSupportDraft(context: Context): Flow<String> =
        context.andakSupportDataStore.data.map { it[supportDraftKey].orEmpty() }

    suspend fun saveSupportDraft(context: Context, value: String) {
        context.andakSupportDataStore.edit { it[supportDraftKey] = value.take(1000) }
    }
}
EOF

cat > "$ROOT/apps/customer/src/main/java/com/fush/market/customer/AuthGateway.kt" <<'EOF'
package com.fush.market.customer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long
)

data class AuthActionResult(
    val session: AuthSession?,
    val message: String
)

object BackendAuthGateway {
    fun isConfigured(): Boolean = BackendCatalogGateway.isConfigured()

    suspend fun signIn(email: String, password: String): Result<AuthSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isConfigured()) { "ANDAK backend is not configured" }
                require(email.isNotBlank() && password.isNotBlank()) { "Email and password are required" }
                val body = JSONObject()
                    .put("email", email.trim())
                    .put("password", password)
                parseSession(
                    postJson(
                        path = "/auth/v1/token?grant_type=password",
                        payload = body,
                        bearer = null
                    )
                )
            }
        }

    suspend fun signUp(email: String, password: String): Result<AuthActionResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isConfigured()) { "ANDAK backend is not configured" }
                require(email.isNotBlank() && password.length >= 8) { "Use a valid email and password of at least 8 characters" }
                val json = postJson(
                    path = "/auth/v1/signup",
                    payload = JSONObject()
                        .put("email", email.trim())
                        .put("password", password),
                    bearer = null
                )
                val accessToken = json.optString("access_token", "")
                if (accessToken.isBlank()) {
                    AuthActionResult(
                        session = null,
                        message = "تم إنشاء الحساب. تحقق من البريد الإلكتروني إذا كان تأكيد البريد مفعلاً."
                    )
                } else {
                    AuthActionResult(
                        session = parseSession(json),
                        message = "تم إنشاء الحساب وتسجيل الدخول."
                    )
                }
            }
        }

    suspend fun refresh(refreshToken: String): Result<AuthSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isConfigured()) { "ANDAK backend is not configured" }
                require(refreshToken.isNotBlank()) { "Refresh token is missing" }
                parseSession(
                    postJson(
                        path = "/auth/v1/token?grant_type=refresh_token",
                        payload = JSONObject().put("refresh_token", refreshToken),
                        bearer = null
                    )
                )
            }
        }

    suspend fun signOut(accessToken: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (isConfigured() && accessToken.isNotBlank()) {
                postJson(
                    path = "/auth/v1/logout",
                    payload = JSONObject(),
                    bearer = accessToken
                )
            }
        }
    }

    private fun parseSession(json: JSONObject): AuthSession {
        val accessToken = json.getString("access_token")
        val refreshToken = json.optString("refresh_token", "")
        val expiresIn = json.optLong("expires_in", 3600L)
        val user = json.optJSONObject("user") ?: JSONObject()
        return AuthSession(
            userId = user.optString("id", ""),
            email = user.optString("email", ""),
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = (System.currentTimeMillis() / 1000L) + expiresIn
        )
    }

    private fun postJson(path: String, payload: JSONObject, bearer: String?): JSONObject {
        val endpoint = BuildConfig.ANDAK_SUPABASE_URL.trimEnd('/') + path
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.setRequestProperty("apikey", BuildConfig.ANDAK_SUPABASE_KEY)
        if (!bearer.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearer)
        }
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")

        try {
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val raw = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(raw).optString("msg") }.getOrDefault("")
                error(if (detail.isNotBlank()) detail else "Auth HTTP " + code)
            }
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }
}
EOF

cat > "$ROOT/apps/customer/src/main/java/com/fush/market/customer/SecureSessionStore.kt" <<'EOF'
package com.fush.market.customer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureSessionStore {
    private const val PREFS = "andak_secure_session"
    private const val KEY_ALIAS = "andak_customer_session_key"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

    fun save(context: Context, session: AuthSession) {
        val json = JSONObject()
            .put("userId", session.userId)
            .put("email", session.email)
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("expiresAt", session.expiresAtEpochSeconds)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(context: Context): AuthSession? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ivRaw = prefs.getString("iv", null) ?: return null
        val payloadRaw = prefs.getString("payload", null) ?: return null

        return runCatching {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val iv = Base64.decode(ivRaw, Base64.NO_WRAP)
            val payload = Base64.decode(payloadRaw, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(payload), Charsets.UTF_8))
            AuthSession(
                userId = json.optString("userId", ""),
                email = json.optString("email", ""),
                accessToken = json.optString("accessToken", ""),
                refreshToken = json.optString("refreshToken", ""),
                expiresAtEpochSeconds = json.optLong("expiresAt", 0L)
            )
        }.getOrElse {
            clear(context)
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
EOF

cat > "$ROOT/apps/customer/src/main/java/com/fush/market/customer/AccountSyncGateway.kt" <<'EOF'
package com.fush.market.customer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AccountSnapshot(
    val addresses: List<SavedAddress>,
    val preferences: CustomerPreferences,
    val orders: List<OrderReceipt>,
    val cart: List<CartLine>,
    val favorites: List<String>,
    val supportTickets: List<SupportTicketSummary>,
    val notifications: List<CustomerNotification>
)

data class SupportTicketSummary(
    val ticketNumber: String,
    val category: String,
    val status: String,
    val message: String,
    val createdAt: String
)

data class CustomerNotification(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val createdAt: String
)

data class SupportTicketResult(
    val ticketId: String,
    val ticketNumber: String,
    val status: String
)

object AccountSyncGateway {
    fun isConfigured(): Boolean = BackendAuthGateway.isConfigured()

    suspend fun pushAccount(
        session: AuthSession,
        addresses: List<SavedAddress>,
        preferences: CustomerPreferences,
        cart: List<CartLine>,
        favorites: Collection<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(session.accessToken.isNotBlank()) { "Access token is required" }

            val addressArray = JSONArray()
            addresses.take(10).forEach { address ->
                addressArray.put(
                    JSONObject()
                        .put("client_id", address.id)
                        .put("label", address.label)
                        .put("full_name", address.fullName)
                        .put("phone", address.phone)
                        .put("city", address.city)
                        .put("neighborhood", address.neighborhood)
                        .put("details", address.details)
                        .put("is_default", address.isDefault)
                )
            }

            val cartArray = JSONArray()
            cart.filter { it.quantity > 0 }.forEach { line ->
                cartArray.put(
                    JSONObject()
                        .put("product_id", line.productId)
                        .put("variant_id", line.variantId)
                        .put("quantity", line.quantity)
                )
            }

            val favoriteArray = JSONArray()
            favorites.filter { it.isNotBlank() }.distinct().forEach(favoriteArray::put)

            postRpc(
                "andak_customer_sync_v2",
                JSONObject()
                    .put("p_addresses", addressArray)
                    .put("p_order_updates", preferences.orderUpdates)
                    .put("p_offers", preferences.offers)
                    .put("p_cart", cartArray)
                    .put("p_favorites", favoriteArray),
                session.accessToken
            )
            Unit
        }
    }

    suspend fun fetchSnapshot(session: AuthSession): Result<AccountSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(session.accessToken.isNotBlank()) { "Access token is required" }
                val result = postRpc(
                    "andak_customer_account_snapshot_v3",
                    JSONObject(),
                    session.accessToken
                )

                val addressRows = result.optJSONArray("addresses") ?: JSONArray()
                val addresses = buildList {
                    for (index in 0 until addressRows.length()) {
                        val row = addressRows.getJSONObject(index)
                        add(
                            SavedAddress(
                                id = row.optString("client_id", row.optString("id", "")),
                                label = row.optString("label", "عنوان"),
                                fullName = row.optString("full_name", ""),
                                phone = row.optString("phone", ""),
                                city = row.optString("city", ""),
                                neighborhood = row.optString("neighborhood", ""),
                                details = row.optString("details", ""),
                                isDefault = row.optBoolean("is_default", false)
                            )
                        )
                    }
                }.filter { it.id.isNotBlank() }

                val prefRow = result.optJSONObject("preferences") ?: JSONObject()
                val preferences = CustomerPreferences(
                    orderUpdates = prefRow.optBoolean("order_updates", true),
                    offers = prefRow.optBoolean("offers", false)
                )

                val orderRows = result.optJSONArray("orders") ?: JSONArray()
                val orders = buildList {
                    for (index in 0 until orderRows.length()) {
                        val row = orderRows.getJSONObject(index)
                        add(
                            OrderReceipt(
                                reference = row.optString("order_number", ""),
                                serverCreated = true,
                                trackingToken = row.optString("tracking_token", ""),
                                status = row.optString("status", "PENDING_CONFIRMATION"),
                                totalYER = row.optLong("total_yer", 0L),
                                createdAtMillis = 0L
                            )
                        )
                    }
                }.filter { it.reference.isNotBlank() }

                val cartRows = result.optJSONArray("cart") ?: JSONArray()
                val cart = buildList {
                    for (index in 0 until cartRows.length()) {
                        val row = cartRows.getJSONObject(index)
                        val quantity = row.optInt("quantity", 0)
                        if (quantity > 0) {
                            add(
                                CartLine(
                                    productId = row.optString("product_id", ""),
                                    variantId = row.optString("variant_id", ""),
                                    quantity = quantity
                                )
                            )
                        }
                    }
                }.filter { it.productId.isNotBlank() && it.variantId.isNotBlank() }

                val favoriteRows = result.optJSONArray("favorites") ?: JSONArray()
                val favorites = buildList {
                    for (index in 0 until favoriteRows.length()) {
                        val productId = favoriteRows.optString(index, "")
                        if (productId.isNotBlank()) add(productId)
                    }
                }.distinct()

                val ticketRows = result.optJSONArray("support_tickets") ?: JSONArray()
                val supportTickets = buildList {
                    for (index in 0 until ticketRows.length()) {
                        val row = ticketRows.getJSONObject(index)
                        add(
                            SupportTicketSummary(
                                ticketNumber = row.optString("ticket_number", ""),
                                category = row.optString("category", ""),
                                status = row.optString("status", "OPEN"),
                                message = row.optString("message", ""),
                                createdAt = row.optString("created_at", "")
                            )
                        )
                    }
                }.filter { it.ticketNumber.isNotBlank() }

                val notificationRows = result.optJSONArray("notifications") ?: JSONArray()
                val notifications = buildList {
                    for (index in 0 until notificationRows.length()) {
                        val row = notificationRows.getJSONObject(index)
                        add(
                            CustomerNotification(
                                id = row.optString("id", ""),
                                kind = row.optString("kind", "INFO"),
                                title = row.optString("title", "تحديث"),
                                body = row.optString("body", ""),
                                createdAt = row.optString("created_at", "")
                            )
                        )
                    }
                }.filter { it.id.isNotBlank() }

                AccountSnapshot(
                    addresses = addresses,
                    preferences = preferences,
                    orders = orders,
                    cart = cart,
                    favorites = favorites,
                    supportTickets = supportTickets,
                    notifications = notifications
                )
            }
        }

    suspend fun createSupportTicket(
        session: AuthSession,
        category: String,
        message: String
    ): Result<SupportTicketResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(session.accessToken.isNotBlank()) { "Access token is required" }
            require(message.trim().length >= 10) { "Support message is too short" }

            val result = postRpc(
                "andak_customer_support_ticket_v1",
                JSONObject()
                    .put("p_category", category.trim())
                    .put("p_message", message.trim()),
                session.accessToken
            )
            SupportTicketResult(
                ticketId = result.getString("ticket_id"),
                ticketNumber = result.getString("ticket_number"),
                status = result.optString("status", "OPEN")
            )
        }
    }

    private fun postRpc(functionName: String, payload: JSONObject, accessToken: String): JSONObject {
        require(isConfigured()) { "ANDAK backend is not configured" }
        val endpoint = BuildConfig.ANDAK_SUPABASE_URL.trimEnd('/') + "/rest/v1/rpc/" + functionName

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.setRequestProperty("apikey", BuildConfig.ANDAK_SUPABASE_KEY)
        connection.setRequestProperty("Authorization", "Bearer " + accessToken)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")

        try {
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val raw = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (code !in 200..299) {
                error("Account RPC " + functionName + " HTTP " + code + " " + raw)
            }
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }
}
EOF

cat > "$ROOT/apps/customer/src/main/java/com/fush/market/customer/MainActivity.kt" <<'EOF'
package com.fush.market.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CustomerApp() }
    }
}
EOF

cat > "$ROOT/apps/customer/src/main/java/com/fush/market/customer/CustomerApp.kt" <<'EOF'
package com.fush.market.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fush.market.core.ui.*
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

enum class CustomerScreen { HOME, CATALOG, PRODUCT, CART, CHECKOUT, CONFIRMATION, ORDERS, ORDER_DETAIL, PROFILE, SUPPORT, NOTIFICATIONS, AUTH }

@Composable
fun CustomerApp() {
    AndakTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            var screen by rememberSaveable { mutableStateOf(CustomerScreen.HOME) }
            var selectedProductId by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
            var searchQuery by rememberSaveable { mutableStateOf("") }
            val context = LocalContext.current
            val appScope = rememberCoroutineScope()
            val cart = remember { mutableStateListOf<CartLine>() }
            val favoriteProductIds = remember { mutableStateListOf<String>() }
            var localCommerceLoaded by remember { mutableStateOf(false) }
            val catalogCategories = remember { mutableStateListOf<CatalogCategory>().apply { addAll(CatalogSeed.categories) } }
            val catalogProducts = remember { mutableStateListOf<CatalogProduct>().apply { addAll(CatalogSeed.products) } }
            var catalogSource by rememberSaveable { mutableStateOf("كتالوج محلي تجريبي") }
            val orderReceipts = remember { mutableStateListOf<OrderReceipt>() }
            val savedAddresses = remember { mutableStateListOf<SavedAddress>() }
            var customerPreferences by remember { mutableStateOf(CustomerPreferences()) }
            var supportDraft by rememberSaveable { mutableStateOf("") }
            var authSession by remember { mutableStateOf<AuthSession?>(null) }
            var authReady by remember { mutableStateOf(false) }
            var accountSyncBusy by remember { mutableStateOf(false) }
            var accountSyncMessage by rememberSaveable { mutableStateOf<String?>(null) }
            var lastReceipt by remember { mutableStateOf<OrderReceipt?>(null) }
            var selectedReceipt by remember { mutableStateOf<OrderReceipt?>(null) }
            var selectedOrderStatus by remember { mutableStateOf<OrderStatusResult?>(null) }
            var orderDetailLoading by remember { mutableStateOf(false) }
            var orderDetailError by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(context) {
                val saved = SecureSessionStore.load(context)
                if (saved != null && BackendAuthGateway.isConfigured()) {
                    val now = System.currentTimeMillis() / 1000L
                    if (saved.expiresAtEpochSeconds <= now + 60L && saved.refreshToken.isNotBlank()) {
                        BackendAuthGateway.refresh(saved.refreshToken)
                            .onSuccess {
                                SecureSessionStore.save(context, it)
                                authSession = it
                            }
                            .onFailure {
                                SecureSessionStore.clear(context)
                                authSession = null
                            }
                    } else {
                        authSession = saved
                    }
                } else {
                    authSession = saved
                }
                authReady = true
            }

            LaunchedEffect(context) {
                val savedCart = LocalCommerceStore.loadCart(context)
                val savedFavorites = LocalCommerceStore.loadFavorites(context)
                cart.clear()
                cart.addAll(savedCart)
                favoriteProductIds.clear()
                favoriteProductIds.addAll(savedFavorites)
                localCommerceLoaded = true
            }

            LaunchedEffect(context) {
                OrderReceiptStore.observe(context).collect { saved ->
                    orderReceipts.clear()
                    orderReceipts.addAll(saved)
                }
            }

            LaunchedEffect(context) {
                AddressStore.observe(context).collect { saved ->
                    savedAddresses.clear()
                    savedAddresses.addAll(saved)
                }
            }

            LaunchedEffect(context) {
                SupportPreferencesStore.observePreferences(context).collect { prefs ->
                    customerPreferences = prefs
                }
            }

            LaunchedEffect(context) {
                SupportPreferencesStore.observeSupportDraft(context).collect { draft ->
                    if (supportDraft.isBlank()) supportDraft = draft
                }
            }

            LaunchedEffect(Unit) {
                if (BackendCatalogGateway.isConfigured()) {
                    BackendCatalogGateway.fetchCatalog().getOrNull()?.let { snapshot ->
                        if (snapshot.categories.isNotEmpty() && snapshot.products.isNotEmpty()) {
                            catalogCategories.clear()
                            catalogCategories.addAll(snapshot.categories)
                            catalogProducts.clear()
                            catalogProducts.addAll(snapshot.products)
                            catalogSource = "متصل بالكتالوج المركزي"
                        }
                    }
                }
            }

            val syncAuthenticatedAccount: () -> Unit = sync@{
                val session = authSession ?: return@sync
                if (!AccountSyncGateway.isConfigured() || accountSyncBusy) return@sync
                accountSyncBusy = true
                accountSyncMessage = null

                val localAddresses = savedAddresses.toList()
                val localPreferences = customerPreferences
                val localCart = cart.toList()
                val localFavorites = favoriteProductIds.toList()
                val hasMeaningfulLocalData =
                    localAddresses.isNotEmpty() || localCart.isNotEmpty() || localFavorites.isNotEmpty()

                appScope.launch {
                    AccountSyncGateway.fetchSnapshot(session)
                        .onSuccess { cloud ->
                            val mergedAddresses = linkedMapOf<String, SavedAddress>()
                            cloud.addresses.forEach { mergedAddresses[it.id] = it }
                            localAddresses.forEach { mergedAddresses[it.id] = it }

                            val mergedCartByVariant = linkedMapOf<String, CartLine>()
                            (cloud.cart + localCart).forEach { line ->
                                val current = mergedCartByVariant[line.variantId]
                                if (current == null || line.quantity > current.quantity) {
                                    mergedCartByVariant[line.variantId] = line
                                }
                            }

                            val mergedFavorites = (cloud.favorites + localFavorites).distinct()
                            val mergedPreferences =
                                if (hasMeaningfulLocalData) localPreferences else cloud.preferences

                            AccountSyncGateway.pushAccount(
                                session = session,
                                addresses = mergedAddresses.values.toList(),
                                preferences = mergedPreferences,
                                cart = mergedCartByVariant.values.toList(),
                                favorites = mergedFavorites
                            ).onSuccess {
                                val mergedCart = mergedCartByVariant.values.toList()
                                val mergedAddressList = mergedAddresses.values.toList()

                                AddressStore.replaceAll(context, mergedAddressList)
                                SupportPreferencesStore.setOrderUpdates(context, mergedPreferences.orderUpdates)
                                SupportPreferencesStore.setOffers(context, mergedPreferences.offers)
                                LocalCommerceStore.saveCart(context, mergedCart)
                                LocalCommerceStore.saveFavorites(context, mergedFavorites)

                                cart.clear()
                                cart.addAll(mergedCart)
                                favoriteProductIds.clear()
                                favoriteProductIds.addAll(mergedFavorites)
                                cloud.orders.forEach { OrderReceiptStore.upsert(context, it) }
                                accountSyncMessage = "تمت مزامنة السلة والمفضلة والحساب بين الأجهزة"
                            }.onFailure { error ->
                                accountSyncMessage = "تعذر رفع البيانات المدمجة: " + (error.message ?: "خطأ")
                            }
                        }
                        .onFailure { error ->
                            accountSyncMessage = "تعذر جلب بيانات الحساب: " + (error.message ?: "خطأ")
                        }
                    accountSyncBusy = false
                }
            }

            LaunchedEffect(authSession?.userId, localCommerceLoaded) {
                if (authSession != null && localCommerceLoaded && AccountSyncGateway.isConfigured()) {
                    syncAuthenticatedAccount()
                }
            }

            val persistCart: () -> Unit = {
                if (localCommerceLoaded) {
                    val snapshot = cart.toList()
                    appScope.launch { LocalCommerceStore.saveCart(context, snapshot) }
                }
            }

            val toggleFavorite: (String) -> Unit = { productId ->
                if (favoriteProductIds.contains(productId)) {
                    favoriteProductIds.remove(productId)
                } else {
                    favoriteProductIds.add(productId)
                }
                val snapshot = favoriteProductIds.toList()
                appScope.launch { LocalCommerceStore.saveFavorites(context, snapshot) }
            }

            val openProduct: (CatalogProduct) -> Unit = { product ->
                selectedProductId = product.id
                screen = CustomerScreen.PRODUCT
            }

            Scaffold(
                containerColor = AndakCream,
                bottomBar = {
                    if (screen != CustomerScreen.PRODUCT && screen != CustomerScreen.CHECKOUT && screen != CustomerScreen.CONFIRMATION && screen != CustomerScreen.ORDER_DETAIL && screen != CustomerScreen.SUPPORT && screen != CustomerScreen.NOTIFICATIONS && screen != CustomerScreen.AUTH) {
                        CustomerBottomBar(screen = screen, onNavigate = { screen = it })
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (screen) {
                        CustomerScreen.HOME -> HomeScreen(
                            cartCount = cart.sumOf { it.quantity },
                            categories = catalogCategories,
                            products = catalogProducts,
                            favoriteProductIds = favoriteProductIds.toSet(),
                            catalogSource = catalogSource,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            onSearchSubmit = { screen = CustomerScreen.CATALOG },
                            onCategory = {
                                selectedCategoryId = it
                                searchQuery = ""
                                screen = CustomerScreen.CATALOG
                            },
                            onProduct = openProduct,
                            onCart = { screen = CustomerScreen.CART }
                        )
                        CustomerScreen.CATALOG -> CatalogScreen(
                            categories = catalogCategories,
                            products = catalogProducts,
                            initialCategoryId = selectedCategoryId,
                            initialQuery = searchQuery,
                            onBackHome = {
                                selectedCategoryId = null
                                searchQuery = ""
                                screen = CustomerScreen.HOME
                            },
                            onProduct = openProduct
                        )
                        CustomerScreen.PRODUCT -> {
                            val product = catalogProducts.firstOrNull { it.id == selectedProductId }
                            if (product == null) {
                                screen = CustomerScreen.HOME
                            } else {
                                ProductScreen(
                                    product = product,
                                    cartCount = cart.sumOf { it.quantity },
                                    isFavorite = favoriteProductIds.contains(product.id),
                                    onToggleFavorite = { toggleFavorite(product.id) },
                                    onBack = { screen = CustomerScreen.CATALOG },
                                    onAddToCart = { variantId ->
                                        val index = cart.indexOfFirst { it.productId == product.id && it.variantId == variantId }
                                        if (index >= 0) {
                                            val current = cart[index]
                                            cart[index] = current.copy(quantity = current.quantity + 1)
                                        } else {
                                            cart.add(CartLine(product.id, variantId, 1))
                                        }
                                        persistCart()
                                    },
                                    onCart = { screen = CustomerScreen.CART }
                                )
                            }
                        }
                        CustomerScreen.CART -> CartScreen(
                            products = catalogProducts,
                            cart = cart,
                            onContinueShopping = { screen = CustomerScreen.CATALOG },
                            onQuantity = { line, delta ->
                                val index = cart.indexOf(line)
                                if (index >= 0) {
                                    val next = line.quantity + delta
                                    if (next <= 0) cart.removeAt(index) else cart[index] = line.copy(quantity = next)
                                    persistCart()
                                }
                            },
                            onCheckout = { screen = CustomerScreen.CHECKOUT }
                        )
                        CustomerScreen.CHECKOUT -> CheckoutScreen(
                            products = catalogProducts,
                            cart = cart,
                            savedAddresses = savedAddresses,
                            authSession = authSession,
                            onBack = { screen = CustomerScreen.CART },
                            onCompleted = { receipt ->
                                lastReceipt = receipt
                                appScope.launch {
                                    OrderReceiptStore.upsert(context, receipt)
                                }
                                screen = CustomerScreen.CONFIRMATION
                            }
                        )
                        CustomerScreen.CONFIRMATION -> ConfirmationScreen(
                            receipt = lastReceipt,
                            onContinueShopping = {
                                cart.clear()
                                persistCart()
                                screen = CustomerScreen.HOME
                            },
                            onViewOrders = { screen = CustomerScreen.ORDERS }
                        )
                        CustomerScreen.ORDERS -> OrdersScreen(
                            receipts = orderReceipts,
                            onOpen = { receipt ->
                                selectedReceipt = receipt
                                selectedOrderStatus = null
                                orderDetailError = null
                                screen = CustomerScreen.ORDER_DETAIL

                                if (receipt.serverCreated && receipt.trackingToken.isNotBlank() && BackendOrderGateway.isConfigured()) {
                                    orderDetailLoading = true
                                    appScope.launch {
                                        BackendOrderGateway.fetchOrderStatus(receipt.trackingToken)
                                            .onSuccess { status ->
                                                selectedOrderStatus = status
                                                OrderReceiptStore.upsert(
                                                    context,
                                                    receipt.copy(
                                                        status = status.status,
                                                        totalYER = status.totalYER
                                                    )
                                                )
                                            }
                                            .onFailure { error ->
                                                orderDetailError = error.message ?: "تعذر تحديث الطلب"
                                            }
                                        orderDetailLoading = false
                                    }
                                }
                            },
                            onRefresh = { receipt ->
                                if (receipt.serverCreated && receipt.trackingToken.isNotBlank() && BackendOrderGateway.isConfigured()) {
                                    appScope.launch {
                                        BackendOrderGateway.fetchOrderStatus(receipt.trackingToken)
                                            .onSuccess { status ->
                                                OrderReceiptStore.upsert(
                                                    context,
                                                    receipt.copy(
                                                        status = status.status,
                                                        totalYER = status.totalYER
                                                    )
                                                )
                                            }
                                    }
                                }
                            }
                        )
                        CustomerScreen.ORDER_DETAIL -> OrderDetailScreen(
                            receipt = selectedReceipt,
                            status = selectedOrderStatus,
                            loading = orderDetailLoading,
                            error = orderDetailError,
                            onBack = { screen = CustomerScreen.ORDERS },
                            onRefresh = {
                                val receipt = selectedReceipt
                                if (receipt != null && receipt.serverCreated && receipt.trackingToken.isNotBlank() && BackendOrderGateway.isConfigured()) {
                                    orderDetailLoading = true
                                    orderDetailError = null
                                    appScope.launch {
                                        BackendOrderGateway.fetchOrderStatus(receipt.trackingToken)
                                            .onSuccess { status ->
                                                selectedOrderStatus = status
                                                OrderReceiptStore.upsert(
                                                    context,
                                                    receipt.copy(
                                                        status = status.status,
                                                        totalYER = status.totalYER
                                                    )
                                                )
                                            }
                                            .onFailure { error ->
                                                orderDetailError = error.message ?: "تعذر تحديث الطلب"
                                            }
                                        orderDetailLoading = false
                                    }
                                }
                            },
                            onReorder = {
                                val status = selectedOrderStatus
                                if (status != null) {
                                    status.lines.forEach { line ->
                                        if (line.variantId.isNotBlank()) {
                                            val product = catalogProducts.firstOrNull { product ->
                                                product.variants.any { it.id == line.variantId }
                                            }
                                            val variant = product?.variants?.firstOrNull { it.id == line.variantId }
                                            if (product != null && variant != null && variant.availability != Availability.OUT) {
                                                val qty = line.quantity.toInt().coerceAtLeast(1)
                                                val index = cart.indexOfFirst { it.productId == product.id && it.variantId == variant.id }
                                                if (index >= 0) {
                                                    val current = cart[index]
                                                    cart[index] = current.copy(quantity = current.quantity + qty)
                                                } else {
                                                    cart.add(CartLine(product.id, variant.id, qty))
                                                }
                                            }
                                        }
                                    }
                                    persistCart()
                                    screen = CustomerScreen.CART
                                }
                            }
                        )
                        CustomerScreen.PROFILE -> ProfileScreen(
                            addresses = savedAddresses,
                            authSession = authSession,
                            authReady = authReady,
                            syncBusy = accountSyncBusy,
                            syncMessage = accountSyncMessage,
                            onSaveAddress = { address ->
                                appScope.launch { AddressStore.upsert(context, address) }
                            },
                            onSetDefault = { id ->
                                appScope.launch { AddressStore.setDefault(context, id) }
                            },
                            onDeleteAddress = { id ->
                                appScope.launch { AddressStore.delete(context, id) }
                            },
                            onSupport = { screen = CustomerScreen.SUPPORT },
                            onNotifications = { screen = CustomerScreen.NOTIFICATIONS },
                            onAuth = { screen = CustomerScreen.AUTH },
                            onSync = syncAuthenticatedAccount,
                            onLogout = {
                                val token = authSession?.accessToken.orEmpty()
                                authSession = null
                                SecureSessionStore.clear(context)
                                appScope.launch { BackendAuthGateway.signOut(token) }
                            }
                        )
                        CustomerScreen.SUPPORT -> SupportScreen(
                            draft = supportDraft,
                            authSession = authSession,
                            onDraftChange = { supportDraft = it },
                            onSaveDraft = {
                                val snapshot = supportDraft
                                appScope.launch { SupportPreferencesStore.saveSupportDraft(context, snapshot) }
                            },
                            onBack = { screen = CustomerScreen.PROFILE }
                        )
                        CustomerScreen.NOTIFICATIONS -> NotificationsScreen(
                            receipts = orderReceipts,
                            preferences = customerPreferences,
                            onOrderUpdatesChange = { enabled ->
                                appScope.launch { SupportPreferencesStore.setOrderUpdates(context, enabled) }
                            },
                            onOffersChange = { enabled ->
                                appScope.launch { SupportPreferencesStore.setOffers(context, enabled) }
                            },
                            onBack = { screen = CustomerScreen.PROFILE }
                        )
                        CustomerScreen.AUTH -> AuthScreen(
                            configured = BackendAuthGateway.isConfigured(),
                            onBack = { screen = CustomerScreen.PROFILE },
                            onAuthenticated = { session ->
                                SecureSessionStore.save(context, session)
                                authSession = session
                                accountSyncMessage = "تم تسجيل الدخول — يمكنك مزامنة حسابك الآن"
                                screen = CustomerScreen.PROFILE
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    cartCount: Int,
    categories: List<CatalogCategory>,
    products: List<CatalogProduct>,
    favoriteProductIds: Set<String>,
    catalogSource: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onCategory: (String) -> Unit,
    onProduct: (CatalogProduct) -> Unit,
    onCart: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { CustomerHeader(cartCount = cartCount, onCart = onCart) }
        item {
            Surface(color = if (catalogSource.startsWith("متصل")) AndakGreen.copy(alpha = 0.10f) else Color(0xFFFFF4D6), shape = RoundedCornerShape(12.dp)) {
                Text(catalogSource, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = if (catalogSource.startsWith("متصل")) AndakGreen else Color(0xFF7A5200), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        item {
            SearchBox(
                value = searchQuery,
                onValueChange = onSearchChange,
                onSubmit = onSearchSubmit
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("عروض اليوم", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PromoCard("توصيل أسرع", "منتجاتك اليومية في مكان واحد", "🚚", Modifier.weight(1f))
                    PromoCard("خيارات أكثر", "تصفح أصناف متنوعة بسهولة", "✨", Modifier.weight(1f))
                }
            }
        }
        item {
            SectionTitle("الأقسام", "عرض الكل") { onCategory("") }
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(categories) { category ->
                    CategoryCard(category = category) { onCategory(category.id) }
                }
            }
        }
        item {
            SectionTitle("منتجات مختارة", "تصفح الكتالوج") { onSearchSubmit() }
            Spacer(Modifier.height(10.dp))
            ProductGridStatic(products.filter { it.featured }.take(6), onProduct)
        }
        if (favoriteProductIds.isNotEmpty()) {
            item {
                SectionTitle("المفضلة", "تصفح الكتالوج") { onSearchSubmit() }
                Spacer(Modifier.height(10.dp))
                ProductGridStatic(
                    products.filter { favoriteProductIds.contains(it.id) }.take(4),
                    onProduct
                )
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("إعادة الطلب", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "بعد أول طلب ناجح سنعرض هنا المنتجات التي تشتريها غالباً.",
                        color = AndakMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerHeader(cartCount: Int, onCart: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AndakLogo(size = 58.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("عندك", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AndakDeepGreen)
            Text("كل السوق عندك", fontSize = 13.sp, color = AndakGreen)
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            modifier = Modifier.clickable(onClick = onCart)
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🛒", fontSize = 19.sp)
                if (cartCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(cartCount.toString(), fontWeight = FontWeight.Bold, color = AndakGreen)
                }
            }
        }
    }
}

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("ابحث عن منتج أو علامة تجارية") },
        leadingIcon = { Text("🔎") },
        trailingIcon = {
            if (value.isNotBlank()) {
                TextButton(onClick = onSubmit) { Text("بحث") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun PromoCard(title: String, subtitle: String, emoji: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = AndakGreen, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(emoji, fontSize = 28.sp)
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AndakDeepGreen
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun CategoryCard(category: CatalogCategory, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(104.dp).clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(category.emoji, fontSize = 31.sp)
            Spacer(Modifier.height(7.dp))
            Text(category.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun ProductGridStatic(products: List<CatalogProduct>, onProduct: (CatalogProduct) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        products.chunked(2).forEach { rowProducts ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowProducts.forEach { product ->
                    ProductCard(product, Modifier.weight(1f), onProduct)
                }
                if (rowProducts.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: CatalogProduct,
    modifier: Modifier = Modifier,
    onClick: (CatalogProduct) -> Unit
) {
    val variant = product.variants.first()
    Surface(
        modifier = modifier.clickable { onClick(product) },
        color = Color.White,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .background(AndakCream, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(product.emoji, fontSize = 52.sp)
            }
            Spacer(Modifier.height(9.dp))
            Text(product.brand, fontSize = 11.sp, color = AndakMuted, maxLines = 1)
            Text(
                product.name,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(formatYer(variant.priceYER), color = AndakGreen, fontWeight = FontWeight.Bold)
            Text(
                availabilityText(variant.availability),
                color = availabilityColor(variant.availability),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CatalogScreen(
    categories: List<CatalogCategory>,
    products: List<CatalogProduct>,
    initialCategoryId: String?,
    initialQuery: String,
    onBackHome: () -> Unit,
    onProduct: (CatalogProduct) -> Unit
) {
    var selectedCategory by rememberSaveable(initialCategoryId) { mutableStateOf(initialCategoryId.orEmpty()) }
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var sort by rememberSaveable { mutableStateOf("default") }
    var visibleCount by rememberSaveable { mutableIntStateOf(6) }

    val filtered = remember(selectedCategory, query, sort) {
        val base = products.filter { product ->
            (selectedCategory.isBlank() || product.categoryId == selectedCategory) &&
                (query.isBlank() || product.name.contains(query, true) || product.brand.contains(query, true))
        }
        when (sort) {
            "low" -> base.sortedBy { it.variants.first().priceYER }
            "high" -> base.sortedByDescending { it.variants.first().priceYER }
            else -> base
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBackHome) { Text("‹ الرئيسية") }
            Text(
                "الكتالوج",
                modifier = Modifier.weight(1f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AndakDeepGreen
            )
        }
        SearchBox(query, { query = it; visibleCount = 6 }, {})
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterButton("الكل", selectedCategory.isBlank()) {
                    selectedCategory = ""
                    visibleCount = 6
                }
            }
            items(categories) { category ->
                FilterButton(category.name, selectedCategory == category.id) {
                    selectedCategory = category.id
                    visibleCount = 6
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterButton("الأكثر ملاءمة", sort == "default") { sort = "default" }
            FilterButton("السعر الأقل", sort == "low") { sort = "low" }
            FilterButton("السعر الأعلى", sort == "high") { sort = "high" }
        }
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            EmptyCatalog(query = query, onClear = { query = ""; selectedCategory = "" })
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 14.dp)
            ) {
                items(filtered.take(visibleCount), key = { it.id }) { product ->
                    ProductCard(product, onClick = onProduct)
                }
                if (visibleCount < filtered.size) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Button(
                            onClick = {
                                visibleCount = (visibleCount + 6).coerceAtMost(filtered.size)
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Text("تحميل المزيد")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterButton(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(text, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(text, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyCatalog(query: String, onClear: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔎", fontSize = 48.sp)
        Spacer(Modifier.height(10.dp))
        val message = if (query.isNotBlank()) "لا توجد نتائج لـ «" + query + "»" else "لا توجد نتائج"
        Text(message, fontWeight = FontWeight.Bold)
        Text("جرّب كلمة أخرى أو اعرض كل الأقسام.", color = AndakMuted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onClear) { Text("عرض كل المنتجات") }
    }
}

@Composable
private fun ProductScreen(
    product: CatalogProduct,
    cartCount: Int,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onAddToCart: (String) -> Unit,
    onCart: () -> Unit
) {
    var selectedVariantId by rememberSaveable(product.id) { mutableStateOf(product.variants.first().id) }
    val variant = product.variants.first { it.id == selectedVariantId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ رجوع") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onToggleFavorite) {
                    Text(if (isFavorite) "♥ محفوظ" else "♡ مفضلة")
                }
                TextButton(onClick = onCart) {
                    Text("🛒 " + if (cartCount > 0) cartCount.toString() else "")
                }
            }
        }
        item {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1.35f)
                    .background(Color.White, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(product.emoji, fontSize = 90.sp)
            }
        }
        item {
            Text(product.brand, color = AndakMuted, fontSize = 13.sp)
            Text(product.name, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = AndakDeepGreen)
            Spacer(Modifier.height(6.dp))
            Text(formatYer(variant.priceYER), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AndakGreen)
            Text(
                availabilityText(variant.availability),
                color = availabilityColor(variant.availability),
                fontWeight = FontWeight.Medium
            )
        }
        item {
            Text("اختر الحجم / الوحدة", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(product.variants) { item ->
                    FilterButton(
                        item.label + " • " + item.unit,
                        selectedVariantId == item.id
                    ) {
                        selectedVariantId = item.id
                    }
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("الوصف", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                    Spacer(Modifier.height(5.dp))
                    Text(product.description, color = AndakMuted)
                }
            }
        }
        item {
            Button(
                onClick = { onAddToCart(variant.id) },
                enabled = variant.availability != Availability.OUT,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    if (variant.availability == Availability.OUT) "غير متوفر حالياً" else "إضافة للسلة",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CartScreen(
    products: List<CatalogProduct>,
    cart: List<CartLine>,
    onContinueShopping: () -> Unit,
    onQuantity: (CartLine, Int) -> Unit,
    onCheckout: () -> Unit
) {
    val expanded = cart.mapNotNull { line ->
        val product = products.firstOrNull { it.id == line.productId } ?: return@mapNotNull null
        val variant = product.variants.firstOrNull { it.id == line.variantId } ?: return@mapNotNull null
        Triple(line, product, variant)
    }
    val total = expanded.sumOf { triple ->
        triple.third.priceYER * triple.first.quantity
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("السلة", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AndakDeepGreen)
        Spacer(Modifier.height(12.dp))
        if (expanded.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🛒", fontSize = 54.sp)
                Text("السلة فارغة", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onContinueShopping) { Text("ابدأ التسوق") }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    expanded,
                    key = { triple -> triple.first.productId + triple.first.variantId }
                ) { triple ->
                    val line = triple.first
                    val product = triple.second
                    val variant = triple.third
                    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(62.dp).background(AndakCream, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(product.emoji, fontSize = 32.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(
                                    variant.label + " • " + variant.unit,
                                    fontSize = 12.sp,
                                    color = AndakMuted
                                )
                                Text(
                                    formatYer(variant.priceYER),
                                    color = AndakGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SmallQtyButton("−") { onQuantity(line, -1) }
                                Text(
                                    line.quantity.toString(),
                                    modifier = Modifier.padding(horizontal = 9.dp),
                                    fontWeight = FontWeight.Bold
                                )
                                SmallQtyButton("+") { onQuantity(line, 1) }
                            }
                        }
                    }
                }
            }
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("الإجمالي التقديري", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(formatYer(total), fontWeight = FontWeight.Bold, color = AndakGreen)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "يُعاد التحقق من السعر والتوفر عند إتمام الطلب.",
                        color = AndakMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onCheckout,
                        enabled = expanded.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("متابعة لإتمام الطلب")
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutScreen(
    products: List<CatalogProduct>,
    cart: List<CartLine>,
    savedAddresses: List<SavedAddress>,
    authSession: AuthSession?,
    onBack: () -> Unit,
    onCompleted: (OrderReceipt) -> Unit
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var neighborhood by rememberSaveable { mutableStateOf("") }
    var addressDetails by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var submitting by rememberSaveable { mutableStateOf(false) }
    var submitError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(savedAddresses) {
        if (fullName.isBlank() && phone.isBlank() && city.isBlank() && neighborhood.isBlank() && addressDetails.isBlank()) {
            savedAddresses.firstOrNull { it.isDefault }?.let { address ->
                fullName = address.fullName
                phone = address.phone
                city = address.city
                neighborhood = address.neighborhood
                addressDetails = address.details
            }
        }
    }

    val expanded = cart.mapNotNull { line ->
        val product = products.firstOrNull { it.id == line.productId } ?: return@mapNotNull null
        val variant = product.variants.firstOrNull { it.id == line.variantId } ?: return@mapNotNull null
        Triple(line, product, variant)
    }
    val subtotal = expanded.sumOf { it.third.priceYER * it.first.quantity }
    val deliveryFee = if (subtotal > 0) 1500L else 0L
    val total = subtotal + deliveryFee
    val valid = fullName.isNotBlank() &&
        phone.length >= 7 &&
        city.isNotBlank() &&
        neighborhood.isNotBlank() &&
        addressDetails.isNotBlank() &&
        expanded.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ السلة") }
                Text(
                    "إتمام الطلب",
                    modifier = Modifier.weight(1f),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = AndakDeepGreen
                )
            }
        }
        if (savedAddresses.isNotEmpty()) {
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("العناوين المحفوظة", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                        savedAddresses.forEach { address ->
                            OutlinedButton(
                                onClick = {
                                    fullName = address.fullName
                                    phone = address.phone
                                    city = address.city
                                    neighborhood = address.neighborhood
                                    addressDetails = address.details
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (address.isDefault) "★ " else "📍 ")
                                    Text(address.label, modifier = Modifier.weight(1f))
                                    Text(address.city + " — " + address.neighborhood, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("بيانات الاستلام", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("الاسم الكامل") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filter(Char::isDigit).take(15) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("رقم الهاتف") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("المدينة") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = neighborhood,
                        onValueChange = { neighborhood = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("الحي / المنطقة") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = addressDetails,
                        onValueChange = { addressDetails = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("تفاصيل العنوان") },
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(200) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ملاحظة للمندوب — اختياري") },
                        minLines = 2
                    )
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("طريقة الدفع", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = true, onClick = null)
                        Column {
                            Text("الدفع عند الاستلام", fontWeight = FontWeight.Medium)
                            Text("طريقة الدفع المتاحة في هذه المرحلة", color = AndakMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ملخص الطلب", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                    expanded.forEach { triple ->
                        val line = triple.first
                        val product = triple.second
                        val variant = triple.third
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                product.name + " × " + line.quantity,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp
                            )
                            Text(formatYer(variant.priceYER * line.quantity), fontSize = 13.sp)
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth()) {
                        Text("المجموع الفرعي", modifier = Modifier.weight(1f))
                        Text(formatYer(subtotal))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("رسوم توصيل تقديرية", modifier = Modifier.weight(1f))
                        Text(formatYer(deliveryFee))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("الإجمالي التقديري", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(formatYer(total), fontWeight = FontWeight.Bold, color = AndakGreen)
                    }
                    Text(
                        "السعر والتوفر ورسوم التوصيل تُراجع من الخادم قبل إنشاء الطلب الحقيقي.",
                        color = AndakMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    submitError = null
                    if (!BackendOrderGateway.isConfigured()) {
                        val now = System.currentTimeMillis()
                        val suffix = (now % 1000000L).toString().padStart(6, '0')
                        onCompleted(
                            OrderReceipt(
                                reference = "DRAFT-" + suffix,
                                serverCreated = false,
                                trackingToken = "",
                                status = "DRAFT_LOCAL",
                                totalYER = total,
                                createdAtMillis = now
                            )
                        )
                    } else {
                        submitting = true
                        val orderLines = expanded.map { triple ->
                            OrderLineInput(
                                variantId = triple.third.id,
                                quantity = triple.first.quantity
                            )
                        }
                        scope.launch {
                            val result = BackendOrderGateway.createCodOrder(
                                customer = OrderCustomerInput(
                                    fullName = fullName,
                                    phone = phone,
                                    city = city,
                                    neighborhood = neighborhood,
                                    addressDetails = addressDetails,
                                    note = note
                                ),
                                lines = orderLines,
                                accessToken = authSession?.accessToken
                            )
                            submitting = false
                            result.onSuccess { created ->
                                onCompleted(
                                    OrderReceipt(
                                        reference = created.orderNumber,
                                        serverCreated = true,
                                        trackingToken = created.trackingToken,
                                        status = created.status,
                                        totalYER = created.totalYER,
                                        createdAtMillis = System.currentTimeMillis()
                                    )
                                )
                            }.onFailure { error ->
                                submitError = "تعذر إنشاء الطلب: " + (error.message ?: "خطأ غير معروف")
                            }
                        }
                    }
                },
                enabled = valid && !submitting,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    when {
                        submitting -> "جارٍ التحقق وإنشاء الطلب…"
                        BackendOrderGateway.isConfigured() -> "تأكيد الطلب والدفع عند الاستلام"
                        else -> "حفظ مسودة الطلب"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            if (submitError != null) {
                Text(submitError.orEmpty(), color = Color(0xFFB3261E), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                if (BackendOrderGateway.isConfigured())
                    "سيعيد الخادم التحقق من السعر والمخزون ويحجز الكمية بشكل ذري قبل إنشاء الطلب."
                else
                    "Backend غير مهيأ في هذه النسخة؛ سيتم حفظ مسودة محلية فقط.",
                color = AndakMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ConfirmationScreen(
    receipt: OrderReceipt?,
    onContinueShopping: () -> Unit,
    onViewOrders: () -> Unit
) {
    val serverCreated = receipt?.serverCreated == true
    val reference = receipt?.reference.orEmpty()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(color = AndakGreen.copy(alpha = 0.10f), shape = CircleShape) {
            Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                Text("✓", fontSize = 48.sp, color = AndakGreen, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(if (serverCreated) "تم إنشاء الطلب" else "تم حفظ المسودة", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = AndakDeepGreen)
        Spacer(Modifier.height(7.dp))
        Text(
            (if (serverCreated) "رقم الطلب: " else "رقم المسودة: ") + reference,
            color = AndakGreen,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (serverCreated) "تم التحقق من السعر والمخزون وحجز الكمية من الخادم قبل إنشاء الطلب." else "لم يتم إرسال طلب حقيقي لأن Backend غير مهيأ في هذه النسخة.",
            color = AndakMuted
        )
        Spacer(Modifier.height(22.dp))
        Button(onClick = onContinueShopping, modifier = Modifier.fillMaxWidth()) {
            Text("متابعة التسوق")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onViewOrders, modifier = Modifier.fillMaxWidth()) {
            Text("الذهاب إلى طلباتي")
        }
    }
}

@Composable
private fun SmallQtyButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(34.dp).clickable(onClick = onClick),
        color = AndakCream,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OrdersScreen(
    receipts: List<OrderReceipt>,
    onOpen: (OrderReceipt) -> Unit,
    onRefresh: (OrderReceipt) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            "طلباتي",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = AndakDeepGreen
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "تُحفظ مراجع الطلبات على الجهاز، ويمكن تحديث حالة الطلبات المرتبطة بالخادم.",
            color = AndakMuted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))

        if (receipts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 70.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🧾", fontSize = 52.sp)
                Spacer(Modifier.height(8.dp))
                Text("لا توجد طلبات بعد", fontWeight = FontWeight.Bold)
                Text("عند إنشاء أول طلب سيظهر هنا.", color = AndakMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(receipts, key = { it.reference }) { receipt ->
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(receipt) }
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (receipt.serverCreated) "طلب " + receipt.reference else "مسودة " + receipt.reference,
                                        fontWeight = FontWeight.Bold,
                                        color = AndakDeepGreen
                                    )
                                    Text(
                                        orderStatusArabic(receipt.status),
                                        color = orderStatusColor(receipt.status),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (receipt.totalYER > 0) {
                                    Text(
                                        formatYer(receipt.totalYER),
                                        color = AndakGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (receipt.serverCreated) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "الدفع عند الاستلام",
                                        modifier = Modifier.weight(1f),
                                        color = AndakMuted,
                                        fontSize = 12.sp
                                    )
                                    OutlinedButton(
                                        onClick = { onRefresh(receipt) },
                                        enabled = receipt.trackingToken.isNotBlank() && BackendOrderGateway.isConfigured(),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("تحديث الحالة", fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Text(
                                    "مسودة محلية لم تُرسل إلى الخادم.",
                                    color = Color(0xFF7A5200),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderDetailScreen(
    receipt: OrderReceipt?,
    status: OrderStatusResult?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onReorder: () -> Unit
) {
    val effectiveStatus = status?.status ?: receipt?.status.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ طلباتي") }
                Text(
                    "تفاصيل الطلب",
                    modifier = Modifier.weight(1f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AndakDeepGreen
                )
            }
        }

        if (receipt == null) {
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Text("لم يتم العثور على الطلب.", modifier = Modifier.padding(16.dp))
                }
            }
            return@LazyColumn
        }

        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        if (receipt.serverCreated) "طلب " + receipt.reference else "مسودة " + receipt.reference,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AndakDeepGreen
                    )
                    Text(
                        orderStatusArabic(effectiveStatus),
                        color = orderStatusColor(effectiveStatus),
                        fontWeight = FontWeight.Bold
                    )
                    val total = status?.totalYER ?: receipt.totalYER
                    if (total > 0) {
                        Text("الإجمالي: " + formatYer(total), color = AndakGreen, fontWeight = FontWeight.Bold)
                    }
                    if (status != null) {
                        Text("الدفع: عند الاستلام", color = AndakMuted, fontSize = 12.sp)
                        Text("العنوان: " + status.city + " — " + status.neighborhood, color = AndakMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Text("حالة الطلب", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AndakDeepGreen)
            Spacer(Modifier.height(8.dp))
            OrderTimeline(currentStatus = effectiveStatus)
        }

        if (loading) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("جارٍ تحديث تفاصيل الطلب…", color = AndakMuted)
                }
            }
        }

        if (!error.isNullOrBlank()) {
            item {
                Surface(color = Color(0xFFFFEDEA), shape = RoundedCornerShape(14.dp)) {
                    Text(error, modifier = Modifier.padding(12.dp), color = Color(0xFFB3261E), fontSize = 12.sp)
                }
            }
        }

        if (status != null && status.lines.isNotEmpty()) {
            item {
                Text("الأصناف", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AndakDeepGreen)
            }
            items(status.lines) { line ->
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(line.productName, fontWeight = FontWeight.Bold)
                            Text(
                                line.variantLabel + " • " + line.unitName + " • × " + line.quantity.toInt(),
                                color = AndakMuted,
                                fontSize = 12.sp
                            )
                        }
                        Text(formatYer(line.lineTotalYER), color = AndakGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (receipt.serverCreated) {
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !loading && receipt.trackingToken.isNotBlank() && BackendOrderGateway.isConfigured(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تحديث حالة الطلب")
                    }
                }

                Button(
                    onClick = onReorder,
                    enabled = status?.lines?.any { it.variantId.isNotBlank() } == true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إعادة إضافة الأصناف للسلة")
                }

                if (!receipt.serverCreated) {
                    Text(
                        "هذه مسودة محلية؛ لا توجد تفاصيل خادم أو تتبع حي.",
                        color = Color(0xFF7A5200),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderTimeline(currentStatus: String) {
    val stages = listOf(
        "PENDING_CONFIRMATION" to "بانتظار التأكيد",
        "CONFIRMED" to "تم التأكيد",
        "PREPARING" to "قيد التجهيز",
        "READY_FOR_PICKUP" to "جاهز للاستلام",
        "OUT_FOR_DELIVERY" to "خرج للتوصيل",
        "DELIVERED" to "تم التسليم"
    )
    val currentIndex = stages.indexOfFirst { it.first == currentStatus.uppercase() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (currentStatus.uppercase() == "CANCELLED") {
            Surface(color = Color(0xFFFFEDEA), shape = RoundedCornerShape(14.dp)) {
                Text(
                    "تم إلغاء الطلب",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = Color(0xFFB3261E),
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (currentStatus.uppercase() == "DRAFT_LOCAL") {
            Surface(color = Color(0xFFFFF4D6), shape = RoundedCornerShape(14.dp)) {
                Text(
                    "مسودة محلية",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = Color(0xFF7A5200),
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            stages.forEachIndexed { index, stage ->
                val reached = currentIndex >= index
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (reached) AndakGreen else Color(0xFFE1E5E3),
                        shape = CircleShape
                    ) {
                        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                            Text(if (reached) "✓" else (index + 1).toString(), color = if (reached) Color.White else AndakMuted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stage.second,
                        color = if (reached) AndakDeepGreen else AndakMuted,
                        fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    addresses: List<SavedAddress>,
    authSession: AuthSession?,
    authReady: Boolean,
    syncBusy: Boolean,
    syncMessage: String?,
    onSaveAddress: (SavedAddress) -> Unit,
    onSetDefault: (String) -> Unit,
    onDeleteAddress: (String) -> Unit,
    onSupport: () -> Unit,
    onNotifications: () -> Unit,
    onAuth: () -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit
) {
    var showForm by rememberSaveable { mutableStateOf(false) }
    var label by rememberSaveable { mutableStateOf("المنزل") }
    var fullName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var neighborhood by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }

    val valid = label.isNotBlank() &&
        fullName.isNotBlank() &&
        phone.length >= 7 &&
        city.isNotBlank() &&
        neighborhood.isNotBlank() &&
        details.isNotBlank()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("حسابي", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = AndakDeepGreen)
            Text("العناوين وإعدادات الحساب المحلية", color = AndakMuted, fontSize = 12.sp)
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("حساب العميل", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                    if (!authReady) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("جارٍ فحص الجلسة…", color = AndakMuted, fontSize = 12.sp)
                        }
                    } else if (authSession == null) {
                        Text("أنت تتصفح كضيف.", color = AndakMuted, fontSize = 13.sp)
                        Button(
                            onClick = onAuth,
                            enabled = BackendAuthGateway.isConfigured(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تسجيل الدخول / إنشاء حساب")
                        }
                        if (!BackendAuthGateway.isConfigured()) {
                            Text(
                                "تسجيل الدخول سيُفعّل عند ربط Backend الرسمي لتطبيق «عندك».",
                                color = Color(0xFF7A5200),
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        Text(authSession.email.ifBlank { "حساب عميل" }, fontWeight = FontWeight.Bold)
                        Text("جلسة محفوظة بتشفير Android Keystore.", color = AndakMuted, fontSize = 11.sp)
                        Button(
                            onClick = onSync,
                            enabled = !syncBusy && AccountSyncGateway.isConfigured(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (syncBusy) "جارٍ المزامنة…" else "مزامنة الحساب الآن")
                        }
                        if (!syncMessage.isNullOrBlank()) {
                            Text(
                                syncMessage,
                                color = if (syncMessage.startsWith("تم")) AndakGreen else Color(0xFF7A5200),
                                fontSize = 11.sp
                            )
                        }
                        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                            Text("تسجيل الخروج")
                        }
                    }
                }
            }
        }

        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("العناوين المحفوظة", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                    if (addresses.isEmpty()) {
                        Text("لا توجد عناوين محفوظة بعد.", color = AndakMuted, fontSize = 13.sp)
                    } else {
                        addresses.forEach { address ->
                            Surface(
                                color = AndakCream,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (address.isDefault) "★ " + address.label else "📍 " + address.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                        if (!address.isDefault) {
                                            TextButton(onClick = { onSetDefault(address.id) }) { Text("افتراضي") }
                                        }
                                    }
                                    Text(address.fullName + " • " + address.phone, fontSize = 12.sp)
                                    Text(address.city + " — " + address.neighborhood, color = AndakMuted, fontSize = 12.sp)
                                    Text(address.details, color = AndakMuted, fontSize = 12.sp)
                                    TextButton(onClick = { onDeleteAddress(address.id) }) {
                                        Text("حذف", color = Color(0xFFB3261E))
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = { showForm = !showForm }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showForm) "إغلاق إضافة العنوان" else "إضافة عنوان جديد")
                    }
                }
            }
        }

        if (showForm) {
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("عنوان جديد", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                        OutlinedTextField(label = { Text("اسم العنوان") }, value = label, onValueChange = { label = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(label = { Text("الاسم الكامل") }, value = fullName, onValueChange = { fullName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(label = { Text("رقم الهاتف") }, value = phone, onValueChange = { phone = it.filter(Char::isDigit).take(15) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(label = { Text("المدينة") }, value = city, onValueChange = { city = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(label = { Text("الحي / المنطقة") }, value = neighborhood, onValueChange = { neighborhood = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(label = { Text("تفاصيل العنوان") }, value = details, onValueChange = { details = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Button(
                            onClick = {
                                onSaveAddress(
                                    SavedAddress(
                                        label = label.trim(),
                                        fullName = fullName.trim(),
                                        phone = phone.trim(),
                                        city = city.trim(),
                                        neighborhood = neighborhood.trim(),
                                        details = details.trim(),
                                        isDefault = addresses.isEmpty()
                                    )
                                )
                                label = "المنزل"
                                fullName = ""
                                phone = ""
                                city = ""
                                neighborhood = ""
                                details = ""
                                showForm = false
                            },
                            enabled = valid,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("حفظ العنوان")
                        }
                    }
                }
            }
        }

        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المساعدة والتنبيهات", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                    OutlinedButton(onClick = onNotifications, modifier = Modifier.fillMaxWidth()) {
                        Text("🔔 مركز التنبيهات")
                    }
                    OutlinedButton(onClick = onSupport, modifier = Modifier.fillMaxWidth()) {
                        Text("💬 المساعدة والدعم")
                    }
                }
            }
        }

        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("الخصوصية والأمان", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                    Text("العناوين في هذا الإصدار محفوظة محليًا على الجهاز فقط.", color = AndakMuted, fontSize = 12.sp)
                    Text("لن يتم رفعها إلى Backend حتى تفعيل حساب العميل وسياسة RLS الخاصة به.", color = AndakMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(
    configured: Boolean,
    onBack: () -> Unit,
    onAuthenticated: (AuthSession) -> Unit
) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val validEmail = email.contains("@") && email.contains(".")
    val valid = validEmail && password.length >= 8 && configured && !busy

    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ حسابي") }
            Text(
                if (registerMode) "إنشاء حساب" else "تسجيل الدخول",
                modifier = Modifier.weight(1f),
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = AndakDeepGreen
            )
        }

        Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AndakLogo(size = 64.dp)
                Text(
                    if (registerMode) "أنشئ حسابك لحفظ جلسة العميل بأمان." else "ادخل إلى حسابك في «عندك».",
                    color = AndakMuted
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim(); error = null; notice = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("البريد الإلكتروني") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null; notice = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("كلمة المرور") },
                    singleLine = true
                )
                if (!configured) {
                    Surface(color = Color(0xFFFFF4D6), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            "Backend الرسمي غير مهيأ في هذا البناء، لذلك لن نرسل بيانات تسجيل الدخول إلى عنوان غير صالح.",
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFF7A5200),
                            fontSize = 12.sp
                        )
                    }
                }
                if (!error.isNullOrBlank()) {
                    Text(error.orEmpty(), color = Color(0xFFB3261E), fontSize = 12.sp)
                }
                if (!notice.isNullOrBlank()) {
                    Text(notice.orEmpty(), color = AndakGreen, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        busy = true
                        error = null
                        notice = null
                        scope.launch {
                            if (registerMode) {
                                BackendAuthGateway.signUp(email, password)
                                    .onSuccess { result ->
                                        busy = false
                                        if (result.session != null) {
                                            onAuthenticated(result.session)
                                        } else {
                                            notice = result.message
                                            registerMode = false
                                        }
                                    }
                                    .onFailure {
                                        busy = false
                                        error = it.message ?: "تعذر إنشاء الحساب"
                                    }
                            } else {
                                BackendAuthGateway.signIn(email, password)
                                    .onSuccess {
                                        busy = false
                                        onAuthenticated(it)
                                    }
                                    .onFailure {
                                        busy = false
                                        error = it.message ?: "تعذر تسجيل الدخول"
                                    }
                            }
                        }
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (busy) "جارٍ التنفيذ…" else if (registerMode) "إنشاء الحساب" else "تسجيل الدخول")
                }
                TextButton(
                    onClick = {
                        registerMode = !registerMode
                        error = null
                        notice = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (registerMode) "لدي حساب بالفعل" else "إنشاء حساب جديد")
                }
            }
        }

        Text(
            "لن يتم تضمين أي مفتاح إداري حساس داخل التطبيق. يستخدم العميل Publishable/Anon key فقط، والجلسة المحلية مشفرة بمفتاح Android Keystore.",
            color = AndakMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun NotificationsScreen(
    receipts: List<OrderReceipt>,
    preferences: CustomerPreferences,
    onOrderUpdatesChange: (Boolean) -> Unit,
    onOffersChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ حسابي") }
                Text(
                    "مركز التنبيهات",
                    modifier = Modifier.weight(1f),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = AndakDeepGreen
                )
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تفضيلات التنبيه", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("تحديثات الطلب")
                            Text("تغيّر حالة الطلب والتوصيل", color = AndakMuted, fontSize = 12.sp)
                        }
                        Switch(checked = preferences.orderUpdates, onCheckedChange = onOrderUpdatesChange)
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("العروض")
                            Text("إشعارات العروض والتخفيضات", color = AndakMuted, fontSize = 12.sp)
                        }
                        Switch(checked = preferences.offers, onCheckedChange = onOffersChange)
                    }
                }
            }
        }
        item {
            Text("آخر التحديثات", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AndakDeepGreen)
        }
        if (receipts.isEmpty()) {
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Text(
                        "لا توجد تحديثات طلبات حتى الآن.",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        color = AndakMuted
                    )
                }
            }
        } else {
            items(receipts.take(10), key = { it.reference }) { receipt ->
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            if (receipt.serverCreated) "طلب " + receipt.reference else "مسودة " + receipt.reference,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            orderStatusArabic(receipt.status),
                            color = orderStatusColor(receipt.status),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportScreen(
    draft: String,
    authSession: AuthSession?,
    onDraftChange: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onBack: () -> Unit
) {
    var category by rememberSaveable { mutableStateOf("الطلبات") }
    var savedNotice by rememberSaveable { mutableStateOf(false) }
    var sending by rememberSaveable { mutableStateOf(false) }
    var ticketNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ حسابي") }
                Text(
                    "المساعدة والدعم",
                    modifier = Modifier.weight(1f),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = AndakDeepGreen
                )
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أسئلة شائعة", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                    Text("• كيف أعرف حالة طلبي؟ من شاشة «طلباتي» افتح الطلب واضغط تحديث الحالة.", fontSize = 13.sp)
                    Text("• هل الدفع الإلكتروني متاح؟ حاليًا طريقة الدفع المعتمدة في هذا التدفق هي الدفع عند الاستلام.", fontSize = 13.sp)
                    Text("• لماذا تظهر مسودة محلية؟ عندما لا يكون Backend مهيأ لا يرسل التطبيق طلبًا وهميًا للخادم.", fontSize = 13.sp)
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("مسودة رسالة للدعم", fontWeight = FontWeight.Bold, color = AndakDeepGreen)
                    Text("الفئة", fontSize = 12.sp, color = AndakMuted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("الطلبات", "التوصيل", "الحساب", "أخرى")) { item ->
                            FilterButton(item, category == item) { category = item }
                        }
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            savedNotice = false
                            onDraftChange(it.take(1000))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("اكتب تفاصيل المشكلة") },
                        minLines = 5
                    )
                    Button(
                        onClick = {
                            onSaveDraft()
                            savedNotice = true
                        },
                        enabled = draft.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("حفظ المسودة")
                    }
                    if (authSession != null && AccountSyncGateway.isConfigured()) {
                        OutlinedButton(
                            onClick = {
                                sending = true
                                ticketNotice = null
                                scope.launch {
                                    AccountSyncGateway.createSupportTicket(authSession, category, draft)
                                        .onSuccess { ticket ->
                                            ticketNotice = "تم إرسال الطلب للدعم: " + ticket.ticketNumber
                                            savedNotice = false
                                        }
                                        .onFailure { error ->
                                            ticketNotice = "تعذر الإرسال: " + (error.message ?: "خطأ")
                                        }
                                    sending = false
                                }
                            },
                            enabled = draft.trim().length >= 10 && !sending,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (sending) "جارٍ الإرسال…" else "إرسال للدعم")
                        }
                    }
                    if (savedNotice) {
                        Text("تم حفظ المسودة محليًا على الجهاز.", color = AndakGreen, fontSize = 12.sp)
                    }
                    if (!ticketNotice.isNullOrBlank()) {
                        Text(
                            ticketNotice.orEmpty(),
                            color = if (ticketNotice.orEmpty().startsWith("تم")) AndakGreen else Color(0xFFB3261E),
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        if (authSession != null && AccountSyncGateway.isConfigured())
                            "يمكن إرسال الرسالة الآن إلى Backend الحساب باستخدام جلسة العميل."
                        else
                            "سجّل الدخول واربط Backend الرسمي لإرسال رسالة الدعم؛ ويمكنك حفظها محليًا الآن.",
                        color = AndakMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, text: String) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AndakDeepGreen)
        Spacer(Modifier.height(8.dp))
        Text(text, color = AndakMuted)
    }
}

@Composable
private fun CustomerBottomBar(screen: CustomerScreen, onNavigate: (CustomerScreen) -> Unit) {
    NavigationBar(containerColor = Color.White) {
        BottomItem("🏠", "الرئيسية", screen == CustomerScreen.HOME) { onNavigate(CustomerScreen.HOME) }
        BottomItem("▦", "الأقسام", screen == CustomerScreen.CATALOG) { onNavigate(CustomerScreen.CATALOG) }
        BottomItem("🧾", "طلباتي", screen == CustomerScreen.ORDERS) { onNavigate(CustomerScreen.ORDERS) }
        BottomItem("👤", "حسابي", screen == CustomerScreen.PROFILE) { onNavigate(CustomerScreen.PROFILE) }
    }
}

@Composable
private fun RowScope.BottomItem(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Text(icon, fontSize = 19.sp) },
        label = { Text(label, fontSize = 11.sp) }
    )
}

private fun formatYer(value: Long): String {
    val formatted = NumberFormat.getNumberInstance(Locale.US).format(value)
    return formatted + " ر.ي"
}

private fun orderStatusArabic(status: String): String = when (status.uppercase()) {
    "DRAFT_LOCAL" -> "مسودة محلية"
    "PENDING_CONFIRMATION" -> "بانتظار التأكيد"
    "CONFIRMED" -> "تم التأكيد"
    "PREPARING" -> "قيد التجهيز"
    "READY_FOR_PICKUP" -> "جاهز للاستلام"
    "OUT_FOR_DELIVERY" -> "خرج للتوصيل"
    "DELIVERED" -> "تم التسليم"
    "CANCELLED" -> "ملغي"
    else -> status
}

private fun orderStatusColor(status: String): Color = when (status.uppercase()) {
    "DELIVERED" -> AndakGreen
    "CANCELLED" -> Color(0xFFB3261E)
    "DRAFT_LOCAL" -> Color(0xFF7A5200)
    else -> Color(0xFF315D7A)
}

private fun availabilityText(value: Availability): String = when (value) {
    Availability.AVAILABLE -> "متوفر الآن"
    Availability.LIMITED -> "كمية محدودة"
    Availability.OUT -> "غير متوفر"
}

private fun availabilityColor(value: Availability): Color = when (value) {
    Availability.AVAILABLE -> AndakGreen
    Availability.LIMITED -> Color(0xFFB26A00)
    Availability.OUT -> Color(0xFFB3261E)
}
EOF


cat > "$ROOT/backend/supabase/migrations/001_andak_customer_catalog_contract.sql" <<'EOF'
create table if not exists public.andak_categories (
    id uuid primary key default gen_random_uuid(),
    name_ar text not null,
    emoji text,
    sort_order integer not null default 0,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.andak_brands (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.andak_products (
    id uuid primary key default gen_random_uuid(),
    category_id uuid not null references public.andak_categories(id),
    brand_id uuid references public.andak_brands(id),
    name_ar text not null,
    description_ar text,
    emoji text,
    featured boolean not null default false,
    sort_order integer not null default 0,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.andak_product_variants (
    id uuid primary key default gen_random_uuid(),
    product_id uuid not null references public.andak_products(id) on delete cascade,
    label_ar text not null,
    unit_name_ar text not null,
    barcode text,
    retail_price_yer bigint not null check (retail_price_yer >= 0),
    sort_order integer not null default 0,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.andak_supplier_inventory (
    id uuid primary key default gen_random_uuid(),
    supplier_id uuid not null,
    variant_id uuid not null references public.andak_product_variants(id) on delete cascade,
    available_qty numeric(18,3) not null default 0 check (available_qty >= 0),
    supplier_cost_yer bigint not null check (supplier_cost_yer >= 0),
    updated_at timestamptz not null default now(),
    unique (supplier_id, variant_id)
);

alter table public.andak_categories enable row level security;
alter table public.andak_brands enable row level security;
alter table public.andak_products enable row level security;
alter table public.andak_product_variants enable row level security;
alter table public.andak_supplier_inventory enable row level security;

create or replace view public.andak_customer_catalog_v1
with (security_invoker = true)
as
select
    c.id::text as category_id,
    c.name_ar as category_name,
    coalesce(c.emoji, '🛍️') as category_emoji,
    c.sort_order as category_sort,
    p.id::text as product_id,
    coalesce(b.name, '') as brand_name,
    p.name_ar as product_name,
    coalesce(p.emoji, '📦') as product_emoji,
    coalesce(p.description_ar, '') as description,
    p.featured,
    p.sort_order as product_sort,
    v.id::text as variant_id,
    v.label_ar as variant_label,
    v.unit_name_ar as unit_name,
    v.retail_price_yer as price_yer,
    v.sort_order as variant_sort,
    case
        when coalesce(sum(si.available_qty), 0) <= 0 then 'OUT'
        when coalesce(sum(si.available_qty), 0) < 5 then 'LIMITED'
        else 'AVAILABLE'
    end as availability
from public.andak_categories c
join public.andak_products p on p.category_id = c.id and p.is_active = true
left join public.andak_brands b on b.id = p.brand_id and b.is_active = true
join public.andak_product_variants v on v.product_id = p.id and v.is_active = true
left join public.andak_supplier_inventory si on si.variant_id = v.id
where c.is_active = true
group by
    c.id, c.name_ar, c.emoji, c.sort_order,
    p.id, b.name, p.name_ar, p.emoji, p.description_ar, p.featured, p.sort_order,
    v.id, v.label_ar, v.unit_name_ar, v.retail_price_yer, v.sort_order;

grant select on public.andak_customer_catalog_v1 to anon, authenticated;

drop policy if exists andak_categories_customer_read on public.andak_categories;
create policy andak_categories_customer_read on public.andak_categories
for select to anon, authenticated using (is_active = true);

drop policy if exists andak_brands_customer_read on public.andak_brands;
create policy andak_brands_customer_read on public.andak_brands
for select to anon, authenticated using (is_active = true);

drop policy if exists andak_products_customer_read on public.andak_products;
create policy andak_products_customer_read on public.andak_products
for select to anon, authenticated using (is_active = true);

drop policy if exists andak_variants_customer_read on public.andak_product_variants;
create policy andak_variants_customer_read on public.andak_product_variants
for select to anon, authenticated using (is_active = true);

-- No direct customer SELECT policy on supplier inventory.
-- The public view exposes aggregate availability only and omits supplier IDs/cost.
EOF


cat > "$ROOT/backend/supabase/migrations/002_andak_atomic_order_rpc.sql" <<'EOF'
create sequence if not exists public.andak_order_number_seq start 1;

create table if not exists public.andak_orders (
    id uuid primary key default gen_random_uuid(),
    request_id uuid not null unique,
    order_number text not null unique,
    customer_name text not null,
    phone text not null,
    city text not null,
    neighborhood text not null,
    address_details text not null,
    note text,
    payment_method text not null default 'COD' check (payment_method = 'COD'),
    status text not null default 'PENDING_CONFIRMATION',
    subtotal_yer bigint not null default 0 check (subtotal_yer >= 0),
    delivery_fee_yer bigint not null default 0 check (delivery_fee_yer >= 0),
    total_yer bigint not null default 0 check (total_yer >= 0),
    created_at timestamptz not null default now()
);

create table if not exists public.andak_order_lines (
    id uuid primary key default gen_random_uuid(),
    order_id uuid not null references public.andak_orders(id) on delete cascade,
    variant_id uuid not null references public.andak_product_variants(id),
    quantity numeric(18,3) not null check (quantity > 0),
    unit_price_yer bigint not null check (unit_price_yer >= 0),
    line_total_yer bigint not null check (line_total_yer >= 0)
);

create table if not exists public.andak_order_allocations (
    id uuid primary key default gen_random_uuid(),
    order_line_id uuid not null references public.andak_order_lines(id) on delete cascade,
    supplier_inventory_id uuid not null references public.andak_supplier_inventory(id),
    supplier_id uuid not null,
    allocated_qty numeric(18,3) not null check (allocated_qty > 0),
    supplier_cost_yer bigint not null check (supplier_cost_yer >= 0),
    created_at timestamptz not null default now()
);

alter table public.andak_orders enable row level security;
alter table public.andak_order_lines enable row level security;
alter table public.andak_order_allocations enable row level security;

create or replace function public.andak_create_order_v1(
    p_request_id uuid,
    p_customer_name text,
    p_phone text,
    p_city text,
    p_neighborhood text,
    p_address_details text,
    p_note text,
    p_lines jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_existing public.andak_orders%rowtype;
    v_order_id uuid;
    v_order_number text;
    v_subtotal bigint := 0;
    v_delivery_fee bigint := 1500;
    v_total bigint := 0;
    v_line jsonb;
    v_variant_id uuid;
    v_qty numeric(18,3);
    v_price bigint;
    v_available numeric(18,3);
    v_line_id uuid;
    v_remaining numeric(18,3);
    v_allocate numeric(18,3);
    v_inv record;
begin
    if p_request_id is null then
        raise exception 'request_id_required';
    end if;

    if nullif(trim(p_customer_name), '') is null
       or nullif(trim(p_phone), '') is null
       or nullif(trim(p_city), '') is null
       or nullif(trim(p_neighborhood), '') is null
       or nullif(trim(p_address_details), '') is null then
        raise exception 'customer_delivery_fields_required';
    end if;

    if p_lines is null or jsonb_typeof(p_lines) <> 'array' or jsonb_array_length(p_lines) = 0 then
        raise exception 'order_lines_required';
    end if;

    select * into v_existing
    from public.andak_orders
    where request_id = p_request_id;

    if found then
        return jsonb_build_object(
            'order_id', v_existing.id,
            'order_number', v_existing.order_number,
            'status', v_existing.status,
            'subtotal_yer', v_existing.subtotal_yer,
            'delivery_fee_yer', v_existing.delivery_fee_yer,
            'total_yer', v_existing.total_yer,
            'request_id', v_existing.request_id
        );
    end if;

    perform pg_advisory_xact_lock(hashtext(p_request_id::text));

    select * into v_existing
    from public.andak_orders
    where request_id = p_request_id;

    if found then
        return jsonb_build_object(
            'order_id', v_existing.id,
            'order_number', v_existing.order_number,
            'status', v_existing.status,
            'subtotal_yer', v_existing.subtotal_yer,
            'delivery_fee_yer', v_existing.delivery_fee_yer,
            'total_yer', v_existing.total_yer,
            'request_id', v_existing.request_id
        );
    end if;

    v_order_number :=
        'AND-' || to_char(clock_timestamp(), 'YYMMDD') || '-' ||
        lpad(nextval('public.andak_order_number_seq')::text, 6, '0');

    insert into public.andak_orders (
        request_id, order_number, customer_name, phone, city, neighborhood,
        address_details, note, payment_method, status
    )
    values (
        p_request_id, v_order_number, trim(p_customer_name), trim(p_phone),
        trim(p_city), trim(p_neighborhood), trim(p_address_details),
        nullif(trim(coalesce(p_note, '')), ''), 'COD', 'PENDING_CONFIRMATION'
    )
    returning id into v_order_id;

    for v_line in select value from jsonb_array_elements(p_lines)
    loop
        v_variant_id := (v_line ->> 'variant_id')::uuid;
        v_qty := (v_line ->> 'quantity')::numeric;

        if v_variant_id is null or v_qty is null or v_qty <= 0 then
            raise exception 'invalid_order_line';
        end if;

        select retail_price_yer
        into v_price
        from public.andak_product_variants
        where id = v_variant_id
          and is_active = true
        for share;

        if not found then
            raise exception 'variant_unavailable:%', v_variant_id;
        end if;

        perform 1
        from public.andak_supplier_inventory
        where variant_id = v_variant_id
          and available_qty > 0
        order by available_qty desc, id
        for update;

        select coalesce(sum(available_qty), 0)
        into v_available
        from public.andak_supplier_inventory
        where variant_id = v_variant_id
          and available_qty > 0;

        if v_available < v_qty then
            raise exception 'insufficient_stock:%', v_variant_id;
        end if;

        insert into public.andak_order_lines (
            order_id, variant_id, quantity, unit_price_yer, line_total_yer
        )
        values (
            v_order_id,
            v_variant_id,
            v_qty,
            v_price,
            round(v_price * v_qty)::bigint
        )
        returning id into v_line_id;

        v_remaining := v_qty;

        for v_inv in
            select id, supplier_id, available_qty, supplier_cost_yer
            from public.andak_supplier_inventory
            where variant_id = v_variant_id
              and available_qty > 0
            order by available_qty desc, id
            for update
        loop
            exit when v_remaining <= 0;

            v_allocate := least(v_remaining, v_inv.available_qty);

            update public.andak_supplier_inventory
            set available_qty = available_qty - v_allocate,
                updated_at = now()
            where id = v_inv.id;

            insert into public.andak_order_allocations (
                order_line_id, supplier_inventory_id, supplier_id,
                allocated_qty, supplier_cost_yer
            )
            values (
                v_line_id, v_inv.id, v_inv.supplier_id,
                v_allocate, v_inv.supplier_cost_yer
            );

            v_remaining := v_remaining - v_allocate;
        end loop;

        if v_remaining > 0 then
            raise exception 'allocation_failed:%', v_variant_id;
        end if;

        v_subtotal := v_subtotal + round(v_price * v_qty)::bigint;
    end loop;

    if v_subtotal <= 0 then
        raise exception 'invalid_order_total';
    end if;

    v_total := v_subtotal + v_delivery_fee;

    update public.andak_orders
    set subtotal_yer = v_subtotal,
        delivery_fee_yer = v_delivery_fee,
        total_yer = v_total
    where id = v_order_id;

    return jsonb_build_object(
        'order_id', v_order_id,
        'order_number', v_order_number,
        'status', 'PENDING_CONFIRMATION',
        'subtotal_yer', v_subtotal,
        'delivery_fee_yer', v_delivery_fee,
        'total_yer', v_total,
        'request_id', p_request_id
    );
end;
$fn$;

revoke all on function public.andak_create_order_v1(uuid,text,text,text,text,text,text,jsonb) from public;
grant execute on function public.andak_create_order_v1(uuid,text,text,text,text,text,text,jsonb)
to anon, authenticated;

-- Customers do not receive direct SELECT/INSERT/UPDATE grants on order or allocation tables.
-- The security-definer RPC is the only write path in this phase.
EOF

cat > "$ROOT/backend/supabase/migrations/003_andak_order_tracking_rpc.sql" <<'EOF'
alter table public.andak_orders
    add column if not exists tracking_token uuid not null default gen_random_uuid();

create unique index if not exists ux_andak_orders_tracking_token
    on public.andak_orders(tracking_token);

create or replace function public.andak_create_order_v2(
    p_request_id uuid,
    p_customer_name text,
    p_phone text,
    p_city text,
    p_neighborhood text,
    p_address_details text,
    p_note text,
    p_lines jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_result jsonb;
    v_tracking_token uuid;
begin
    v_result := public.andak_create_order_v1(
        p_request_id,
        p_customer_name,
        p_phone,
        p_city,
        p_neighborhood,
        p_address_details,
        p_note,
        p_lines
    );

    select tracking_token
    into v_tracking_token
    from public.andak_orders
    where id = (v_result ->> 'order_id')::uuid;

    return v_result || jsonb_build_object(
        'tracking_token', v_tracking_token
    );
end;
$fn$;

revoke all on function public.andak_create_order_v2(uuid,text,text,text,text,text,text,jsonb) from public;
grant execute on function public.andak_create_order_v2(uuid,text,text,text,text,text,text,jsonb)
to anon, authenticated;

create or replace function public.andak_get_order_status_v1(
    p_tracking_token uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_order public.andak_orders%rowtype;
    v_lines jsonb;
begin
    if p_tracking_token is null then
        raise exception 'tracking_token_required';
    end if;

    select *
    into v_order
    from public.andak_orders
    where tracking_token = p_tracking_token;

    if not found then
        raise exception 'order_not_found';
    end if;

    select coalesce(
        jsonb_agg(
            jsonb_build_object(
                'variant_id', v.id::text,
                'product_name', p.name_ar,
                'variant_label', v.label_ar,
                'unit_name', v.unit_name_ar,
                'quantity', ol.quantity,
                'unit_price_yer', ol.unit_price_yer,
                'line_total_yer', ol.line_total_yer
            )
            order by ol.id
        ),
        '[]'::jsonb
    )
    into v_lines
    from public.andak_order_lines ol
    join public.andak_product_variants v on v.id = ol.variant_id
    join public.andak_products p on p.id = v.product_id
    where ol.order_id = v_order.id;

    return jsonb_build_object(
        'order_number', v_order.order_number,
        'status', v_order.status,
        'payment_method', v_order.payment_method,
        'subtotal_yer', v_order.subtotal_yer,
        'delivery_fee_yer', v_order.delivery_fee_yer,
        'total_yer', v_order.total_yer,
        'city', v_order.city,
        'neighborhood', v_order.neighborhood,
        'created_at', v_order.created_at,
        'lines', v_lines
    );
end;
$fn$;

revoke all on function public.andak_get_order_status_v1(uuid) from public;
grant execute on function public.andak_get_order_status_v1(uuid)
to anon, authenticated;

-- Tracking uses a high-entropy UUID possession token.
-- No supplier identity, supplier cost, allocation, or internal ledger data is returned.
EOF

cat > "$ROOT/backend/supabase/migrations/004_andak_customer_auth_profile.sql" <<'EOF'
create table if not exists public.andak_customer_profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    display_name text,
    phone text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.andak_customer_profiles enable row level security;

drop policy if exists andak_customer_profile_select_own on public.andak_customer_profiles;
create policy andak_customer_profile_select_own
on public.andak_customer_profiles
for select to authenticated
using (user_id = auth.uid());

drop policy if exists andak_customer_profile_insert_own on public.andak_customer_profiles;
create policy andak_customer_profile_insert_own
on public.andak_customer_profiles
for insert to authenticated
with check (user_id = auth.uid());

drop policy if exists andak_customer_profile_update_own on public.andak_customer_profiles;
create policy andak_customer_profile_update_own
on public.andak_customer_profiles
for update to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

alter table public.andak_orders
    add column if not exists customer_user_id uuid references auth.users(id);

create index if not exists ix_andak_orders_customer_user
    on public.andak_orders(customer_user_id, created_at desc);

create or replace function public.andak_create_order_v3(
    p_request_id uuid,
    p_customer_name text,
    p_phone text,
    p_city text,
    p_neighborhood text,
    p_address_details text,
    p_note text,
    p_lines jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_result jsonb;
    v_user_id uuid;
begin
    v_user_id := auth.uid();

    v_result := public.andak_create_order_v2(
        p_request_id,
        p_customer_name,
        p_phone,
        p_city,
        p_neighborhood,
        p_address_details,
        p_note,
        p_lines
    );

    if v_user_id is not null then
        update public.andak_orders
        set customer_user_id = v_user_id
        where id = (v_result ->> 'order_id')::uuid
          and (customer_user_id is null or customer_user_id = v_user_id);

        insert into public.andak_customer_profiles(user_id, display_name, phone)
        values (v_user_id, trim(p_customer_name), trim(p_phone))
        on conflict (user_id) do update
        set display_name = excluded.display_name,
            phone = excluded.phone,
            updated_at = now();
    end if;

    return v_result || jsonb_build_object(
        'customer_user_id', v_user_id
    );
end;
$fn$;

revoke all on function public.andak_create_order_v3(uuid,text,text,text,text,text,text,jsonb) from public;
grant execute on function public.andak_create_order_v3(uuid,text,text,text,text,text,text,jsonb)
to anon, authenticated;

-- Authenticated customers can read only their own master orders.
drop policy if exists andak_orders_customer_read_own on public.andak_orders;
create policy andak_orders_customer_read_own
on public.andak_orders
for select to authenticated
using (customer_user_id = auth.uid());

-- Direct writes remain blocked. Order creation still goes through the server RPC.
EOF

cat > "$ROOT/backend/supabase/migrations/005_andak_customer_account_sync.sql" <<'EOF'
create table if not exists public.andak_customer_addresses (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    client_id uuid not null,
    label text not null,
    full_name text not null,
    phone text not null,
    city text not null,
    neighborhood text not null,
    details text not null,
    is_default boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, client_id)
);

create table if not exists public.andak_customer_preferences (
    user_id uuid primary key references auth.users(id) on delete cascade,
    order_updates boolean not null default true,
    offers boolean not null default false,
    updated_at timestamptz not null default now()
);

create sequence if not exists public.andak_support_ticket_seq start 1;

create table if not exists public.andak_support_tickets (
    id uuid primary key default gen_random_uuid(),
    ticket_number text not null unique,
    user_id uuid not null references auth.users(id) on delete cascade,
    category text not null,
    message text not null,
    status text not null default 'OPEN',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.andak_customer_addresses enable row level security;
alter table public.andak_customer_preferences enable row level security;
alter table public.andak_support_tickets enable row level security;

drop policy if exists andak_customer_addresses_own on public.andak_customer_addresses;
create policy andak_customer_addresses_own
on public.andak_customer_addresses
for all to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists andak_customer_preferences_own on public.andak_customer_preferences;
create policy andak_customer_preferences_own
on public.andak_customer_preferences
for all to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists andak_support_tickets_own on public.andak_support_tickets;
create policy andak_support_tickets_own
on public.andak_support_tickets
for select to authenticated
using (user_id = auth.uid());

create or replace function public.andak_customer_sync_v1(
    p_addresses jsonb,
    p_order_updates boolean,
    p_offers boolean
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_user_id uuid;
    v_row jsonb;
    v_client_id uuid;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        raise exception 'authentication_required';
    end if;

    insert into public.andak_customer_preferences(user_id, order_updates, offers, updated_at)
    values (
        v_user_id,
        coalesce(p_order_updates, true),
        coalesce(p_offers, false),
        now()
    )
    on conflict (user_id) do update
    set order_updates = excluded.order_updates,
        offers = excluded.offers,
        updated_at = now();

    if p_addresses is null or jsonb_typeof(p_addresses) <> 'array' then
        p_addresses := '[]'::jsonb;
    end if;

    for v_row in select value from jsonb_array_elements(p_addresses)
    loop
        v_client_id := nullif(v_row ->> 'client_id', '')::uuid;
        if v_client_id is null then
            continue;
        end if;

        insert into public.andak_customer_addresses(
            user_id, client_id, label, full_name, phone, city,
            neighborhood, details, is_default, updated_at
        )
        values (
            v_user_id,
            v_client_id,
            coalesce(nullif(trim(v_row ->> 'label'), ''), 'عنوان'),
            coalesce(v_row ->> 'full_name', ''),
            coalesce(v_row ->> 'phone', ''),
            coalesce(v_row ->> 'city', ''),
            coalesce(v_row ->> 'neighborhood', ''),
            coalesce(v_row ->> 'details', ''),
            coalesce((v_row ->> 'is_default')::boolean, false),
            now()
        )
        on conflict (user_id, client_id) do update
        set label = excluded.label,
            full_name = excluded.full_name,
            phone = excluded.phone,
            city = excluded.city,
            neighborhood = excluded.neighborhood,
            details = excluded.details,
            is_default = excluded.is_default,
            updated_at = now();
    end loop;

    delete from public.andak_customer_addresses a
    where a.user_id = v_user_id
      and not exists (
          select 1
          from jsonb_array_elements(p_addresses) x
          where nullif(x ->> 'client_id', '') is not null
            and (x ->> 'client_id')::uuid = a.client_id
      );

    if exists (
        select 1 from public.andak_customer_addresses
        where user_id = v_user_id and is_default = true
    ) then
        update public.andak_customer_addresses a
        set is_default = false,
            updated_at = now()
        where a.user_id = v_user_id
          and a.is_default = true
          and a.id <> (
              select id
              from public.andak_customer_addresses
              where user_id = v_user_id and is_default = true
              order by updated_at desc, id
              limit 1
          );
    end if;

    return jsonb_build_object(
        'status', 'SYNCED',
        'address_count', (
            select count(*) from public.andak_customer_addresses where user_id = v_user_id
        )
    );
end;
$fn$;

revoke all on function public.andak_customer_sync_v1(jsonb,boolean,boolean) from public;
grant execute on function public.andak_customer_sync_v1(jsonb,boolean,boolean)
to authenticated;

create or replace function public.andak_customer_account_snapshot_v1()
returns jsonb
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_user_id uuid;
    v_addresses jsonb;
    v_preferences jsonb;
    v_orders jsonb;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        raise exception 'authentication_required';
    end if;

    select coalesce(
        jsonb_agg(
            jsonb_build_object(
                'id', a.id,
                'client_id', a.client_id,
                'label', a.label,
                'full_name', a.full_name,
                'phone', a.phone,
                'city', a.city,
                'neighborhood', a.neighborhood,
                'details', a.details,
                'is_default', a.is_default
            )
            order by a.is_default desc, a.updated_at desc
        ),
        '[]'::jsonb
    )
    into v_addresses
    from public.andak_customer_addresses a
    where a.user_id = v_user_id;

    select jsonb_build_object(
        'order_updates', coalesce(p.order_updates, true),
        'offers', coalesce(p.offers, false)
    )
    into v_preferences
    from public.andak_customer_preferences p
    where p.user_id = v_user_id;

    if v_preferences is null then
        v_preferences := jsonb_build_object('order_updates', true, 'offers', false);
    end if;

    select coalesce(
        jsonb_agg(
            jsonb_build_object(
                'order_number', o.order_number,
                'tracking_token', o.tracking_token,
                'status', o.status,
                'total_yer', o.total_yer,
                'created_at', o.created_at
            )
            order by o.created_at desc
        ),
        '[]'::jsonb
    )
    into v_orders
    from (
        select *
        from public.andak_orders
        where customer_user_id = v_user_id
        order by created_at desc
        limit 30
    ) o;

    return jsonb_build_object(
        'addresses', v_addresses,
        'preferences', v_preferences,
        'orders', v_orders
    );
end;
$fn$;

revoke all on function public.andak_customer_account_snapshot_v1() from public;
grant execute on function public.andak_customer_account_snapshot_v1()
to authenticated;

create or replace function public.andak_customer_support_ticket_v1(
    p_category text,
    p_message text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_user_id uuid;
    v_ticket_id uuid;
    v_ticket_number text;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        raise exception 'authentication_required';
    end if;

    if length(trim(coalesce(p_message, ''))) < 10 then
        raise exception 'support_message_too_short';
    end if;

    v_ticket_number :=
        'SUP-' || to_char(clock_timestamp(), 'YYMMDD') || '-' ||
        lpad(nextval('public.andak_support_ticket_seq')::text, 6, '0');

    insert into public.andak_support_tickets(
        ticket_number, user_id, category, message, status
    )
    values (
        v_ticket_number,
        v_user_id,
        coalesce(nullif(trim(p_category), ''), 'أخرى'),
        trim(p_message),
        'OPEN'
    )
    returning id into v_ticket_id;

    return jsonb_build_object(
        'ticket_id', v_ticket_id,
        'ticket_number', v_ticket_number,
        'status', 'OPEN'
    );
end;
$fn$;

revoke all on function public.andak_customer_support_ticket_v1(text,text) from public;
grant execute on function public.andak_customer_support_ticket_v1(text,text)
to authenticated;
EOF

cat > "$ROOT/backend/supabase/migrations/006_andak_customer_cloud_cart_favorites.sql" <<'EOF'
create table if not exists public.andak_customer_cart (
    user_id uuid not null references auth.users(id) on delete cascade,
    variant_id uuid not null references public.andak_product_variants(id) on delete cascade,
    product_id uuid not null references public.andak_products(id) on delete cascade,
    quantity integer not null check (quantity > 0),
    updated_at timestamptz not null default now(),
    primary key (user_id, variant_id)
);

create table if not exists public.andak_customer_favorites (
    user_id uuid not null references auth.users(id) on delete cascade,
    product_id uuid not null references public.andak_products(id) on delete cascade,
    updated_at timestamptz not null default now(),
    primary key (user_id, product_id)
);

alter table public.andak_customer_cart enable row level security;
alter table public.andak_customer_favorites enable row level security;

drop policy if exists andak_customer_cart_own on public.andak_customer_cart;
create policy andak_customer_cart_own
on public.andak_customer_cart
for all to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists andak_customer_favorites_own on public.andak_customer_favorites;
create policy andak_customer_favorites_own
on public.andak_customer_favorites
for all to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

create or replace function public.andak_customer_sync_v2(
    p_addresses jsonb,
    p_order_updates boolean,
    p_offers boolean,
    p_cart jsonb,
    p_favorites jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_user_id uuid;
    v_result jsonb;
    v_row jsonb;
    v_variant_id uuid;
    v_product_id uuid;
    v_quantity integer;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        raise exception 'authentication_required';
    end if;

    v_result := public.andak_customer_sync_v1(
        p_addresses,
        p_order_updates,
        p_offers
    );

    if p_cart is null or jsonb_typeof(p_cart) <> 'array' then
        p_cart := '[]'::jsonb;
    end if;

    for v_row in select value from jsonb_array_elements(p_cart)
    loop
        v_variant_id := nullif(v_row ->> 'variant_id', '')::uuid;
        v_product_id := nullif(v_row ->> 'product_id', '')::uuid;
        v_quantity := greatest(coalesce((v_row ->> 'quantity')::integer, 0), 0);

        if v_variant_id is null or v_product_id is null or v_quantity <= 0 then
            continue;
        end if;

        if not exists (
            select 1
            from public.andak_product_variants v
            where v.id = v_variant_id
              and v.product_id = v_product_id
              and v.is_active = true
        ) then
            continue;
        end if;

        insert into public.andak_customer_cart(
            user_id, variant_id, product_id, quantity, updated_at
        )
        values (
            v_user_id, v_variant_id, v_product_id, v_quantity, now()
        )
        on conflict (user_id, variant_id) do update
        set product_id = excluded.product_id,
            quantity = excluded.quantity,
            updated_at = now();
    end loop;

    delete from public.andak_customer_cart c
    where c.user_id = v_user_id
      and not exists (
          select 1
          from jsonb_array_elements(p_cart) x
          where nullif(x ->> 'variant_id', '') is not null
            and (x ->> 'variant_id')::uuid = c.variant_id
      );

    if p_favorites is null or jsonb_typeof(p_favorites) <> 'array' then
        p_favorites := '[]'::jsonb;
    end if;

    for v_row in select value from jsonb_array_elements(p_favorites)
    loop
        v_product_id := nullif(v_row #>> '{}', '')::uuid;
        if v_product_id is null then
            continue;
        end if;

        if not exists (
            select 1
            from public.andak_products p
            where p.id = v_product_id
              and p.is_active = true
        ) then
            continue;
        end if;

        insert into public.andak_customer_favorites(
            user_id, product_id, updated_at
        )
        values (
            v_user_id, v_product_id, now()
        )
        on conflict (user_id, product_id) do update
        set updated_at = now();
    end loop;

    delete from public.andak_customer_favorites f
    where f.user_id = v_user_id
      and not exists (
          select 1
          from jsonb_array_elements(p_favorites) x
          where nullif(x #>> '{}', '') is not null
            and (x #>> '{}')::uuid = f.product_id
      );

    return v_result || jsonb_build_object(
        'cart_count', (
            select count(*) from public.andak_customer_cart where user_id = v_user_id
        ),
        'favorite_count', (
            select count(*) from public.andak_customer_favorites where user_id = v_user_id
        )
    );
end;
$fn$;

revoke all on function public.andak_customer_sync_v2(jsonb,boolean,boolean,jsonb,jsonb) from public;
grant execute on function public.andak_customer_sync_v2(jsonb,boolean,boolean,jsonb,jsonb)
to authenticated;

create or replace function public.andak_customer_account_snapshot_v2()
returns jsonb
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_user_id uuid;
    v_base jsonb;
    v_cart jsonb;
    v_favorites jsonb;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        raise exception 'authentication_required';
    end if;

    v_base := public.andak_customer_account_snapshot_v1();

    select coalesce(
        jsonb_agg(
            jsonb_build_object(
                'product_id', c.product_id,
                'variant_id', c.variant_id,
                'quantity', c.quantity
            )
            order by c.updated_at desc
        ),
        '[]'::jsonb
    )
    into v_cart
    from public.andak_customer_cart c
    where c.user_id = v_user_id;

    select coalesce(
        jsonb_agg(f.product_id::text order by f.updated_at desc),
        '[]'::jsonb
    )
    into v_favorites
    from public.andak_customer_favorites f
    where f.user_id = v_user_id;

    return v_base || jsonb_build_object(
        'cart', v_cart,
        'favorites', v_favorites
    );
end;
$fn$;

revoke all on function public.andak_customer_account_snapshot_v2() from public;
grant execute on function public.andak_customer_account_snapshot_v2()
to authenticated;
EOF

cat > "$ROOT/backend/README.md" <<'EOF'
# ANDAK backend handoff

The Android customer app now contains a Supabase catalog gateway.

This package does not automatically modify the currently connected FUSH-ERP production database.
That database already contains ERP production tables, so the ANDAK marketplace schema should be
applied only to a dedicated ANDAK Supabase project or an explicitly approved development branch.

Android Gradle properties:
- andakSupabaseUrl
- andakSupabaseKey

Only a publishable or anon key may be embedded in Android. Never embed a service-role key.

When configured, the app reads public.andak_customer_catalog_v1.
If the endpoint is unavailable or not configured, the local demo catalog remains active and
the app labels the source as local.
EOF

cat > "$ROOT/README.md" <<'EOF'
# ANDAK Customer v0.1.14 — Cloud Orders, Support & Notifications

Application ID: com.fush.market.customer
Version: 0.1.13 / versionCode 14

Implemented:
- Customer home priorities: search, offers, categories, selected products, reorder placeholder.
- Catalog category filters and price sorting.
- Incremental paging-style loading.
- Product detail with variants, units, price snapshots and availability wording.
- Local cart draft with quantity controls and estimated total.
- Checkout draft flow with customer name, phone, city, neighborhood and detailed address.
- COD-only payment option for the current phase.
- Local order-draft confirmation screen; no false server submission.
- Order detail screen with delivery timeline and server refresh.
- Reorder action that maps server order variants back into the current cart when available.
- Local customer profile with saved addresses, default-address selection, and delete.
- Checkout can select/prefill a saved address.
- Cart persists across app restarts using DataStore.
- Favorites persist locally and appear as a dedicated home section.
- Notification center with order-update and offers preferences.
- Help center with FAQ and a locally persisted support-message draft.
- Supabase Auth email/password sign-in and sign-up gateway, enabled only when the ANDAK backend is configured.
- Access/refresh session persistence encrypted with Android Keystore (AES-GCM).
- Guest browsing remains available when authentication/backend is unavailable.
- Authenticated cloud sync for saved addresses, notification preferences, and recent order references.
- Cross-device cart restoration and favorites synchronization for authenticated customers.
- Merge-first sync prevents an empty new device from silently wiping cloud cart/favorites.
- Authenticated support-ticket submission with a server-issued ticket number.
- Supplier identity and supplier cost are not exposed to the customer.

This build contains the customer catalog gateway plus an atomic/idempotent COD order gateway. When a dedicated ANDAK Supabase URL and publishable key are configured, checkout calls andak_create_order_v1. Without backend configuration the app saves a clearly labeled local draft only.
EOF
