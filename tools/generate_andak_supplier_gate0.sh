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
 : # Generate a clean supplier identity asset instead of decoding the corrupted legacy logo file
 cat > "$ROOT/apps/supplier/src/main/res/values/colors.xml" <<'E'
<resources><color name="andak_launcher_background">#006B57</color></resources>
E
 python - "$ROOT" <<'PY'
from PIL import Image
from pathlib import Path
import sys
r=Path(sys.argv[1])
from PIL import ImageDraw
im=Image.new('RGBA',(512,512),(0,107,87,255))
d=ImageDraw.Draw(im)
gold=(230,184,63,255); dark=(0,77,64,255)
d.rounded_rectangle((76,126,436,430),radius=56,fill=dark,outline=gold,width=12)
d.arc((158,42,354,230),180,360,fill=gold,width=36)
d.line((158,137,158,103),fill=gold,width=36); d.line((354,137,354,103),fill=gold,width=36)
d.ellipse((196,185,316,305),fill=gold); d.ellipse((232,218,280,266),fill=dark)
d.polygon([(256,342),(205,270),(307,270)],fill=gold)
d.arc((80,210,445,480),18,164,fill=gold,width=24)
raw=r/'core/ui/src/main/res/raw/andak_supplier_logo.png'; raw.parent.mkdir(parents=True,exist_ok=True); im.save(raw)
src=r/'apps/supplier/src/main/res/drawable-nodpi/andak_launcher_source.png'; src.parent.mkdir(parents=True,exist_ok=True); im.save(src)
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
