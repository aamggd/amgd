package com.fush.market.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Green = Color(0xFF006B57)
private val DeepGreen = Color(0xFF004D40)
private val Gold = Color(0xFFE6B83F)
private val Cream = Color(0xFFF8F7F2)

@Composable
fun AndakApp(
    appTitle: String,
    roleLabel: String,
    logoRes: Int = R.drawable.fush_logo,
    buildLabel: String = "Gate 0 • v0.1.0"
) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            secondary = Gold,
            background = Cream,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = DeepGreen
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(containerColor = Cream) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(logoRes),
                            contentDescription = "شعار عندك",
                            modifier = Modifier.size(74.dp),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(appTitle, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DeepGreen)
                            Text("كل السوق عندك", fontSize = 14.sp, color = Green)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "ابحث عن أي منتج، قسم أو علامة تجارية…",
                            modifier = Modifier.padding(18.dp),
                            color = Color(0xFF7A817E),
                            fontSize = 15.sp
                        )
                    }

                    Text(roleLabel, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DeepGreen)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickCard("الطلبات", "0", Modifier.weight(1f))
                        QuickCard("العروض", "جديد", Modifier.weight(1f))
                        QuickCard("الأقسام", "تصفح", Modifier.weight(1f))
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Green)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("المتجر المركزي متعدد الموردين", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "تجربة متجر واحدة للعميل، بينما يعمل الموردون خلف الكواليس.",
                                color = Color.White.copy(alpha = 0.90f),
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .background(Gold, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Text("الدفع عند الاستلام • جاهز للتطوير", color = Color(0xFF3F3100), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    Text(
                        buildLabel,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = Color(0xFF8A918E),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, color = Green, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(title, color = Color(0xFF49504D), fontSize = 13.sp)
        }
    }
}
