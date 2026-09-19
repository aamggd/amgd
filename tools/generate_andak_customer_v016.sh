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
        versionCode = 7
        versionName = "0.1.6"
    }
    val andakSupabaseUrl = providers.gradleProperty("andakSupabaseUrl").orElse("").get()
    val andakSupabaseKey = providers.gradleProperty("andakSupabaseKey").orElse("").get()
    buildFeatures {
        compose = true
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "ANDAK_SUPABASE_URL", "\"\${andakSupabaseUrl}\"")
        buildConfigField("String", "ANDAK_SUPABASE_KEY", "\"\${andakSupabaseKey}\"")
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
    fun isConfigured(): Boolean =
        BuildConfig.ANDAK_SUPABASE_URL.isNotBlank() && BuildConfig.ANDAK_SUPABASE_KEY.isNotBlank()

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
    val requestId: String
)

object BackendOrderGateway {
    fun isConfigured(): Boolean = BackendCatalogGateway.isConfigured()

    suspend fun createCodOrder(
        customer: OrderCustomerInput,
        lines: List<OrderLineInput>,
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

            val endpoint = BuildConfig.ANDAK_SUPABASE_URL.trimEnd('/') +
                "/rest/v1/rpc/andak_create_order_v1"

            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", BuildConfig.ANDAK_SUPABASE_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.ANDAK_SUPABASE_KEY)
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
                    error("Order RPC HTTP " + code + " " + body)
                }

                val result = JSONObject(body)
                CreateOrderResult(
                    orderId = result.getString("order_id"),
                    orderNumber = result.getString("order_number"),
                    status = result.getString("status"),
                    subtotalYER = result.getLong("subtotal_yer"),
                    deliveryFeeYER = result.getLong("delivery_fee_yer"),
                    totalYER = result.getLong("total_yer"),
                    requestId = result.optString("request_id", requestId)
                )
            } finally {
                connection.disconnect()
            }
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

enum class CustomerScreen { HOME, CATALOG, PRODUCT, CART, CHECKOUT, CONFIRMATION, ORDERS, PROFILE }

@Composable
fun CustomerApp() {
    AndakTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            var screen by rememberSaveable { mutableStateOf(CustomerScreen.HOME) }
            var selectedProductId by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
            var searchQuery by rememberSaveable { mutableStateOf("") }
            val cart = remember { mutableStateListOf<CartLine>() }
            val catalogCategories = remember { mutableStateListOf<CatalogCategory>().apply { addAll(CatalogSeed.categories) } }
            val catalogProducts = remember { mutableStateListOf<CatalogProduct>().apply { addAll(CatalogSeed.products) } }
            var catalogSource by rememberSaveable { mutableStateOf("كتالوج محلي تجريبي") }
            var lastDraftRef by rememberSaveable { mutableStateOf<String?>(null) }
            var lastOrderWasServer by rememberSaveable { mutableStateOf(false) }

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

            val openProduct: (CatalogProduct) -> Unit = { product ->
                selectedProductId = product.id
                screen = CustomerScreen.PRODUCT
            }

            Scaffold(
                containerColor = AndakCream,
                bottomBar = {
                    if (screen != CustomerScreen.PRODUCT && screen != CustomerScreen.CHECKOUT && screen != CustomerScreen.CONFIRMATION) {
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
                                    onBack = { screen = CustomerScreen.CATALOG },
                                    onAddToCart = { variantId ->
                                        val index = cart.indexOfFirst { it.productId == product.id && it.variantId == variantId }
                                        if (index >= 0) {
                                            val current = cart[index]
                                            cart[index] = current.copy(quantity = current.quantity + 1)
                                        } else {
                                            cart.add(CartLine(product.id, variantId, 1))
                                        }
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
                                }
                            },
                            onCheckout = { screen = CustomerScreen.CHECKOUT }
                        )
                        CustomerScreen.CHECKOUT -> CheckoutScreen(
                            products = catalogProducts,
                            cart = cart,
                            onBack = { screen = CustomerScreen.CART },
                            onCompleted = { ref, serverCreated ->
                                lastDraftRef = ref
                                lastOrderWasServer = serverCreated
                                screen = CustomerScreen.CONFIRMATION
                            }
                        )
                        CustomerScreen.CONFIRMATION -> ConfirmationScreen(
                            reference = lastDraftRef.orEmpty(),
                            serverCreated = lastOrderWasServer,
                            onContinueShopping = {
                                cart.clear()
                                screen = CustomerScreen.HOME
                            },
                            onViewOrders = { screen = CustomerScreen.ORDERS }
                        )
                        CustomerScreen.ORDERS -> PlaceholderScreen(
                            "طلباتي",
                            "ستظهر هنا الطلبات النشطة والسابقة بعد ربط خدمة الطلبات."
                        )
                        CustomerScreen.PROFILE -> PlaceholderScreen(
                            "حسابي",
                            "العناوين، الإشعارات، اللغة والخصوصية ستكون هنا."
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
    onBack: () -> Unit,
    onCompleted: (String, Boolean) -> Unit
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
                        val suffix = (System.currentTimeMillis() % 1000000L).toString().padStart(6, '0')
                        onCompleted("DRAFT-" + suffix, false)
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
                                lines = orderLines
                            )
                            submitting = false
                            result.onSuccess { created ->
                                onCompleted(created.orderNumber, true)
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
    reference: String,
    serverCreated: Boolean,
    onContinueShopping: () -> Unit,
    onViewOrders: () -> Unit
) {
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
as $
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
$;

revoke all on function public.andak_create_order_v1(uuid,text,text,text,text,text,text,jsonb) from public;
grant execute on function public.andak_create_order_v1(uuid,text,text,text,text,text,text,jsonb)
to anon, authenticated;

-- Customers do not receive direct SELECT/INSERT/UPDATE grants on order or allocation tables.
-- The security-definer RPC is the only write path in this phase.
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
# ANDAK Customer v0.1.6 — Checkout Draft Step

Application ID: com.fush.market.customer
Version: 0.1.3 / versionCode 4

Implemented:
- Customer home priorities: search, offers, categories, selected products, reorder placeholder.
- Catalog category filters and price sorting.
- Incremental paging-style loading.
- Product detail with variants, units, price snapshots and availability wording.
- Local cart draft with quantity controls and estimated total.
- Checkout draft flow with customer name, phone, city, neighborhood and detailed address.
- COD-only payment option for the current phase.
- Local order-draft confirmation screen; no false server submission.
- Supplier identity and supplier cost are not exposed to the customer.

This build contains the customer catalog gateway plus an atomic/idempotent COD order gateway. When a dedicated ANDAK Supabase URL and publishable key are configured, checkout calls andak_create_order_v1. Without backend configuration the app saves a clearly labeled local draft only.
EOF
