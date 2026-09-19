#!/usr/bin/env bash
set -euo pipefail
MODE="$1"; ROOT="$2"; rm -rf "$ROOT"
mkdir -p "$ROOT/core/ui/src/main/java/com/fush/market/core/ui" "$ROOT/core/ui/src/main/res/values" "$ROOT/apps/supplier/src/main/java/com/fush/market/supplier" "$ROOT/apps/supplier/src/main/res/values"
cat > "$ROOT/settings.gradle.kts" <<'E'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name="AndakSupplierGate0"; include(":core:ui",":apps:supplier")
E
cat > "$ROOT/build.gradle.kts" <<'E'
plugins { id("com.android.application") version "9.4.0" apply false; id("com.android.library") version "9.4.0" apply false; id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false }
E
cat > "$ROOT/gradle.properties" <<'E'
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
android.useAndroidX=true
E
cat > "$ROOT/core/ui/build.gradle.kts" <<'E'
plugins { id("com.android.library"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace="com.fush.market.core.ui"; compileSdk=36; defaultConfig { minSdk=23 }; buildFeatures { compose=true }; compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 } }
dependencies { implementation(platform("androidx.compose:compose-bom:2025.08.00")); implementation("androidx.compose.ui:ui"); implementation("androidx.compose.foundation:foundation"); implementation("androidx.compose.material3:material3") }
E
cat > "$ROOT/core/ui/src/main/res/values/styles.xml" <<'E'
<resources><style name="Theme.Andak" parent="android:style/Theme.Material.Light.NoActionBar"><item name="android:statusBarColor">#F8F7F2</item><item name="android:navigationBarColor">#FFFFFF</item></style></resources>
E
if [ "$MODE" = fixed ]; then VC=2; VN=0.1.1; else VC=1; VN=0.1.0; fi
cat > "$ROOT/apps/supplier/build.gradle.kts" <<E
plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace="com.fush.market.supplier"; compileSdk=36; defaultConfig { applicationId="com.fush.market.supplier"; minSdk=23; targetSdk=36; versionCode=$VC; versionName="$VN" }; buildFeatures { compose=true }; buildTypes { release { isMinifyEnabled=false } }; compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 } }
dependencies { implementation(project(":core:ui")); implementation(platform("androidx.compose:compose-bom:2025.08.00")); implementation("androidx.activity:activity-compose:1.11.0"); implementation("androidx.compose.ui:ui"); implementation("androidx.compose.material3:material3") }
E
cat > "$ROOT/apps/supplier/src/main/res/values/strings.xml" <<'E'
<resources><string name="app_name">عندك للمورد</string></resources>
E
cat > "$ROOT/apps/supplier/src/main/java/com/fush/market/supplier/MainActivity.kt" <<'E'
package com.fush.market.supplier
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fush.market.core.ui.AndakApp
class MainActivity:ComponentActivity(){override fun onCreate(s:Bundle?){super.onCreate(s);setContent{AndakApp("عندك للمورد","بوابة المورد")}}}
E
if [ "$MODE" = baseline ]; then
 mkdir -p "$ROOT/core/ui/src/main/res/drawable"; cp "$GITHUB_WORKSPACE/assets/andak_logo.jpg" "$ROOT/core/ui/src/main/res/drawable/fush_logo.jpg"
 cat > "$ROOT/apps/supplier/src/main/AndroidManifest.xml" <<'E'
<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:allowBackup="false" android:icon="@drawable/fush_logo" android:label="عندك للمورد" android:supportsRtl="true" android:theme="@style/Theme.Andak"><activity android:name=".MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application></manifest>
E
 cat > "$ROOT/core/ui/src/main/java/com/fush/market/core/ui/AndakApp.kt" <<'E'
package com.fush.market.core.ui
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
@Composable fun AndakApp(t:String,r:String){MaterialTheme{Column(Modifier.fillMaxSize().padding(20.dp)){Image(painterResource(R.drawable.fush_logo),"شعار عندك",Modifier.size(88.dp));Text(t);Text(r);Text("إدارة الطلبات والمنتجات والمخزون")}}}
E
else
 mkdir -p "$ROOT/core/ui/src/main/res/raw" "$ROOT/apps/supplier/src/main/res/drawable-nodpi" "$ROOT/apps/supplier/src/main/res/mipmap-anydpi-v26"
 : # Use the approved ANDAK marketplace app icon supplied by the project owner.
 cat > "$ROOT/apps/supplier/src/main/res/values/colors.xml" <<'E'
<resources><color name="andak_launcher_background">#006B57</color></resources>
E
 cat > "$ROOT/core/ui/src/main/res/raw/andak_supplier_logo.webp.b64" <<'E'
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
E
 base64 -d "$ROOT/core/ui/src/main/res/raw/andak_supplier_logo.webp.b64" > "$ROOT/core/ui/src/main/res/raw/andak_supplier_logo.webp"
 rm "$ROOT/core/ui/src/main/res/raw/andak_supplier_logo.webp.b64"
 python - "$ROOT" <<'PY'
from PIL import Image
from pathlib import Path
import sys
r=Path(sys.argv[1])
im=Image.open(r/'core/ui/src/main/res/raw/andak_supplier_logo.webp').convert('RGBA')
for d,n in {'mdpi':48,'hdpi':72,'xhdpi':96,'xxhdpi':144,'xxxhdpi':192}.items():
 p=r/f'apps/supplier/src/main/res/mipmap-{d}';p.mkdir(parents=True,exist_ok=True);x=im.resize((n,n),Image.Resampling.LANCZOS);x.save(p/'ic_launcher.png');x.save(p/'ic_launcher_round.png')
for d,n in {'mdpi':108,'hdpi':162,'xhdpi':216,'xxhdpi':324,'xxxhdpi':432}.items():
 p=r/f'apps/supplier/src/main/res/mipmap-{d}';im.resize((n,n),Image.Resampling.LANCZOS).save(p/'ic_launcher_foreground.png')
PY
 cat > "$ROOT/apps/supplier/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" <<'E'
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/andak_launcher_background"/><foreground android:drawable="@mipmap/ic_launcher_foreground"/></adaptive-icon>
E
 cp "$ROOT/apps/supplier/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" "$ROOT/apps/supplier/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"
 cat > "$ROOT/apps/supplier/src/main/AndroidManifest.xml" <<'E'
<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:allowBackup="false" android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round" android:label="@string/app_name" android:supportsRtl="true" android:theme="@style/Theme.Andak"><activity android:name=".MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application></manifest>
E
 cat > "$ROOT/core/ui/src/main/java/com/fush/market/core/ui/AndakApp.kt" <<'E'
package com.fush.market.core.ui
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
@Composable fun AndakApp(t:String,r:String){CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){MaterialTheme{Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){SafeLogo();Text(t,style=MaterialTheme.typography.headlineMedium);Text(r);Card{Column(Modifier.padding(18.dp)){Text("إدارة الطلبات");Text("المنتجات والمخزون");Text("العروض والتقارير")}};Text("Gate 0 • v0.1.1")}}}}
@Composable private fun SafeLogo(){val c=LocalContext.current;val b=remember{runCatching{c.resources.openRawResource(R.raw.andak_supplier_logo).use{BitmapFactory.decodeStream(it)?.asImageBitmap()}}.getOrNull()};if(b!=null)Image(b,"شعار عندك",Modifier.size(88.dp))else Box(Modifier.size(88.dp).background(Color(0xFF006B57),RoundedCornerShape(20.dp)),contentAlignment=Alignment.Center){Text("عندك",color=Color.White)}}
E
fi
