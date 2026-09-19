package com.fush.market.supplier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fush.market.core.ui.AndakApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndakApp(
                appTitle = "عندك للمورد",
                roleLabel = "بوابة المورد"
            )
        }
    }
}
