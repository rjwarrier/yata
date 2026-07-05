package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Yard
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Shared icon-key -> vector mapping used by projects and lists alike. */
fun iconVectorFor(key: String): ImageVector = when (key) {
    "home" -> Icons.Default.Home
    "star" -> Icons.Default.Star
    "label" -> Icons.Default.Label
    "folder" -> Icons.Default.Folder
    "work" -> Icons.Default.BusinessCenter
    "school" -> Icons.Default.School
    "shopping" -> Icons.Default.ShoppingCart
    "bag" -> Icons.Default.ShoppingBag
    "flight" -> Icons.Default.Flight
    "fitness" -> Icons.Default.FitnessCenter
    "restaurant" -> Icons.Default.Restaurant
    "book" -> Icons.Default.Book
    "heart" -> Icons.Default.Favorite
    "idea" -> Icons.Default.Lightbulb
    "pets" -> Icons.Default.Pets
    "health" -> Icons.Default.LocalHospital
    "car" -> Icons.Default.DirectionsCar
    "music" -> Icons.Default.MusicNote
    "code" -> Icons.Default.Code
    "art" -> Icons.Default.Palette
    "gaming" -> Icons.Default.SportsEsports
    "celebration" -> Icons.Default.Celebration
    // Finance
    "money" -> Icons.Default.AttachMoney
    "bank" -> Icons.Default.AccountBalance
    "wallet" -> Icons.Default.AccountBalanceWallet
    "savings" -> Icons.Default.Savings
    "card" -> Icons.Default.CreditCard
    "receipt" -> Icons.Default.Receipt
    "trending" -> Icons.Default.TrendingUp
    "invoice" -> Icons.Default.RequestQuote
    "atm" -> Icons.Default.LocalAtm
    // Home / chores
    "build" -> Icons.Default.Build
    "garden" -> Icons.Default.Yard
    "cleaning" -> Icons.Default.CleaningServices
    // Tech
    "computer" -> Icons.Default.Computer
    "devices" -> Icons.Default.Devices
    "camera" -> Icons.Default.PhotoCamera
    // Entertainment
    "movie" -> Icons.Default.Movie
    "theater" -> Icons.Default.TheaterComedy
    "cake" -> Icons.Default.Cake
    // Family / social
    "family" -> Icons.Default.Groups
    "childcare" -> Icons.Default.ChildCare
    // Wellness
    "spa" -> Icons.Default.Spa
    "meditation" -> Icons.Default.SelfImprovement
    // Travel / transport
    "bike" -> Icons.Default.DirectionsBike
    "bus" -> Icons.Default.DirectionsBus
    "backpack" -> Icons.Default.Backpack
    "world" -> Icons.Default.Public
    // Eco / charity / utilities
    "eco" -> Icons.Default.Recycling
    "charity" -> Icons.Default.VolunteerActivism
    "energy" -> Icons.Default.Bolt
    else -> Icons.Default.Layers
}

/** Full curated set offered by the icon picker, in display order. */
val FOLDER_ICON_KEYS = listOf(
    "folder", "layers", "home", "star", "label", "work", "school",
    // Finance
    "bank", "wallet", "savings", "card", "receipt", "invoice", "money", "trending", "atm",
    // Shopping / food
    "shopping", "bag", "restaurant",
    // Home / chores
    "build", "garden", "cleaning",
    // Travel / transport
    "flight", "car", "bike", "bus", "backpack", "world",
    // Health / fitness / wellness
    "fitness", "health", "spa", "meditation",
    // Family / social
    "family", "childcare", "pets", "heart",
    // Tech / creative
    "computer", "devices", "camera", "code", "art", "music", "book", "idea",
    // Entertainment / misc
    "movie", "theater", "gaming", "cake", "celebration",
    // Eco / charity / utilities
    "eco", "charity", "energy"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(
    options: List<String>,
    selectedIconKey: String,
    accentColor: Color,
    onIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 172.dp)
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.forEach { key ->
            val isSelected = key == selectedIconKey
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) accentColor else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onIconSelected(key) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVectorFor(key),
                    contentDescription = key,
                    tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
