package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalPhone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Extension
// Round 3 new icons
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Diamond
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
    "layers" -> Icons.Default.Layers
    "home" -> Icons.Default.Home
    "star" -> Icons.Default.Star
    "label" -> Icons.AutoMirrored.Filled.Label
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
    "trending" -> Icons.AutoMirrored.Filled.TrendingUp
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
    "bike" -> Icons.AutoMirrored.Filled.DirectionsBike
    "bus" -> Icons.Default.DirectionsBus
    "backpack" -> Icons.Default.Backpack
    "world" -> Icons.Default.Public
    // Eco / charity / utilities
    "eco" -> Icons.Default.Recycling
    "charity" -> Icons.Default.VolunteerActivism
    "energy" -> Icons.Default.Bolt
    // New icons
    "inbox" -> Icons.Default.Inbox
    "flag" -> Icons.Default.Flag
    "lock" -> Icons.Default.Lock
    "hourglass" -> Icons.Default.HourglassEmpty
    "timeline" -> Icons.Default.Timeline
    "taskalt" -> Icons.Default.TaskAlt
    "cloud" -> Icons.Default.Cloud
    "run" -> Icons.AutoMirrored.Filled.DirectionsRun
    "event" -> Icons.Default.Event
    "pin" -> Icons.Default.PushPin
    "gavel" -> Icons.Default.Gavel
    "shipping" -> Icons.Default.LocalShipping
    "phone" -> Icons.Default.LocalPhone
    "email" -> Icons.Default.Email
    "alarm" -> Icons.Default.NotificationsActive
    // Round 2 new icons
    "settings" -> Icons.Default.Settings
    "chart" -> Icons.AutoMirrored.Filled.ShowChart
    "key" -> Icons.Default.Key
    "construction" -> Icons.Default.Construction
    "location" -> Icons.Default.LocationOn
    "brush" -> Icons.Default.Brush
    "bookmark" -> Icons.Default.Bookmark
    "forum" -> Icons.Default.Forum
    "bug" -> Icons.Default.BugReport
    "storage" -> Icons.Default.Storage
    "extension" -> Icons.Default.Extension
    // Round 3 — work / business
    "handshake" -> Icons.Default.Handshake
    "campaign" -> Icons.Default.Campaign
    "support" -> Icons.Default.SupportAgent
    "analytics" -> Icons.Default.Analytics
    "inventory" -> Icons.Default.Inventory2
    "store" -> Icons.Default.Storefront
    "apartment" -> Icons.Default.Apartment
    "assignment" -> Icons.AutoMirrored.Filled.Assignment
    "document" -> Icons.Default.Description
    "badge" -> Icons.Default.Badge
    // Tech
    "terminal" -> Icons.Default.Terminal
    "data" -> Icons.Default.DataObject
    "memory" -> Icons.Default.Memory
    "rocket" -> Icons.Default.RocketLaunch
    // Home / chores
    "bed" -> Icons.Default.Bed
    "kitchen" -> Icons.Default.Kitchen
    "furniture" -> Icons.Default.Chair
    "clothes" -> Icons.Default.Checkroom
    "laundry" -> Icons.Default.LocalLaundryService
    "handyman" -> Icons.Default.Handyman
    // Food / drink
    "coffee" -> Icons.Default.LocalCafe
    "bar" -> Icons.Default.LocalBar
    "pizza" -> Icons.Default.LocalPizza
    "fastfood" -> Icons.Default.Fastfood
    "icecream" -> Icons.Default.Icecream
    // Health / wellness
    "medication" -> Icons.Default.Medication
    "heartrate" -> Icons.Default.MonitorHeart
    "vaccine" -> Icons.Default.Vaccines
    "mind" -> Icons.Default.Psychology
    "sleep" -> Icons.Default.Bedtime
    "water" -> Icons.Default.WaterDrop
    // Sport / outdoors
    "soccer" -> Icons.Default.SportsSoccer
    "basketball" -> Icons.Default.SportsBasketball
    "swim" -> Icons.Default.Pool
    "hiking" -> Icons.Default.Hiking
    "park" -> Icons.Default.Park
    "forest" -> Icons.Default.Forest
    "sunny" -> Icons.Default.WbSunny
    "beach" -> Icons.Default.BeachAccess
    "umbrella" -> Icons.Default.Umbrella
    // Travel
    "hotel" -> Icons.Default.Hotel
    "train" -> Icons.Default.Train
    "boat" -> Icons.Default.DirectionsBoat
    "luggage" -> Icons.Default.Luggage
    "map" -> Icons.Default.Map
    "explore" -> Icons.Default.Explore
    // Learning / creative
    "menubook" -> Icons.AutoMirrored.Filled.MenuBook
    "science" -> Icons.Default.Science
    "calculate" -> Icons.Default.Calculate
    "translate" -> Icons.Default.Translate
    "draw" -> Icons.Default.Draw
    // Media
    "headphones" -> Icons.Default.Headphones
    "podcast" -> Icons.Default.Podcasts
    "mic" -> Icons.Default.Mic
    "video" -> Icons.Default.Videocam
    "tv" -> Icons.Default.Tv
    // Goals / misc
    "trophy" -> Icons.Default.EmojiEvents
    "streak" -> Icons.Default.Whatshot
    "sparkle" -> Icons.Default.AutoAwesome
    "diamond" -> Icons.Default.Diamond
    else -> Icons.Default.Layers
}

/** Full curated set offered by the icon picker, in display order. */
val FOLDER_ICON_KEYS = listOf(
    "folder", "layers", "inbox", "flag", "lock", "pin", "taskalt", "hourglass", "bookmark", "settings", "key", "home", "star", "label", "work", "school",
    "assignment", "document", "badge",
    // Finance / Work
    "bank", "wallet", "savings", "card", "receipt", "invoice", "money", "trending", "atm", "timeline", "chart", "analytics", "shipping", "phone", "email",
    "forum", "handshake", "campaign", "support", "inventory", "apartment",
    // Shopping / food
    "shopping", "bag", "store", "restaurant", "coffee", "bar", "pizza", "fastfood", "icecream",
    // Home / chores
    "build", "construction", "garden", "cleaning", "handyman", "bed", "kitchen", "furniture", "clothes", "laundry",
    // Travel / transport
    "flight", "car", "bike", "bus", "train", "boat", "backpack", "luggage", "hotel", "world", "location", "map", "explore",
    // Health / fitness / wellness
    "fitness", "health", "spa", "meditation", "run", "medication", "heartrate", "vaccine", "mind", "sleep", "water",
    // Sport / outdoors
    "soccer", "basketball", "swim", "hiking", "park", "forest", "sunny", "beach", "umbrella",
    // Family / social
    "family", "childcare", "pets", "heart",
    // Tech / creative / organization
    "computer", "devices", "camera", "code", "terminal", "data", "memory", "rocket", "art", "music", "book", "menubook", "idea", "event", "cloud", "brush",
    "draw", "bug", "storage", "extension", "science", "calculate", "translate",
    // Entertainment / misc
    "movie", "theater", "gaming", "cake", "celebration", "headphones", "podcast", "mic", "video", "tv",
    // Goals / eco / charity / utilities
    "trophy", "streak", "sparkle", "diamond", "eco", "charity", "energy", "gavel", "alarm"
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
            // Roomier than it looks it needs to be: the set is ~140 icons, and at 172dp only three
            // rows were visible, which made the back half of the list a scroll nobody reaches.
            .heightIn(max = 224.dp)
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
