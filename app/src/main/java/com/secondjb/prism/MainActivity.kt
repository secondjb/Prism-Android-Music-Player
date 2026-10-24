package com.secondjb.prism

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondjb.prism.ui.theme.PrismDynamicThemeProvider
import com.secondjb.prism.ui.theme.PrismTheme

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			PrismDynamicThemeProvider {
				Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
					PrismExpressivePreview(
						modifier = Modifier.padding(innerPadding)
					)
				}
			}
		}
	}
}

@Composable
fun PrismExpressivePreview(modifier: Modifier = Modifier) {
	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(24.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		ElevatedCard(
			modifier = Modifier.padding(16.dp),
			shape = MaterialTheme.shapes.large
		) {
			Column(
				modifier = Modifier.padding(24.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Text(
					text = "Prism Music Player",
					style = MaterialTheme.typography.headlineMedium,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.primary
				)
				Spacer(Modifier.height(8.dp))
				Text(
					text = "Material 3 Expressive • Android Companion",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Spacer(Modifier.height(16.dp))
				FilledTonalButton(
					onClick = { },
					shape = MaterialTheme.shapes.medium
				) {
					Text("Ready for Phase 2")
				}
			}
		}
	}
}

@Preview(showBackground = true)
@Composable
fun PrismExpressivePreviewPreview() {
	PrismTheme {
		PrismExpressivePreview()
	}
}
