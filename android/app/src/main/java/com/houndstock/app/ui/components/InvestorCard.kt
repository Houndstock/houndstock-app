package com.houndstock.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.houndstock.app.ui.model.Investor

/**
 * Full-width card showing a single investor's headline info.
 * Sized to fill the parent's width minus the screen-level horizontal padding.
 */
@Composable
fun InvestorCard(
    investor: Investor,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = investor.name,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "${investor.firm}  •  ${investor.holdingCount} holdings",
                style = MaterialTheme.typography.bodyMedium
            )
            TickerRow(tickers = investor.topTickers)
        }
    }
}

@Composable
private fun TickerRow(tickers: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tickers.take(4).forEach { ticker ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Text(
                    text = ticker,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
