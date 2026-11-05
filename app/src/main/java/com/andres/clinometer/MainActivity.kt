package com.andres.clinometer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.andres.clinometer.ui.ClinometerScreen
import com.andres.clinometer.ui.theme.ClinometerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ClinometerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClinometerTheme {
                ClinometerScreen(viewModel)
            }
        }
    }
}
