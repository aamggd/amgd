package com.fush.market.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource
import com.fush.market.core.ui.AndakApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndakApp(
                appTitle = stringResource(R.string.app_name),
                roleLabel = stringResource(R.string.customer_role_label),
                logoRes = R.drawable.andak_logo,
                buildLabel = stringResource(R.string.gate0_build_label)
            )
        }
    }
}
