package com.example.myfincat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay

private val Paper = Color(0xFFF3E7CE)
private val CardColor = Color(0xFFFFF9EA)
private val Green = Color(0xFF7FA36F)
private val DarkGreen = Color(0xFF3F4D3B)
private val SoftGreen = Color(0xFFDDE9D4)
private val Peach = Color(0xFFF1C9A8)
private val Blush = Color(0xFFE9B4AA)
private val Ink = Color(0xFF4E4A42)
private val RetroFont = FontFamily.Monospace

private val LocalCurrency = compositionLocalOf { "INR" }

private val supportedCurrencies: List<Pair<String, String>> by lazy {
    // Java/Android ships with the ISO 4217 currency database. This keeps the
    // picker broad and avoids maintaining a short hand-written currency list.
    java.util.Currency.getAvailableCurrencies()
        .filter { it.currencyCode !in setOf("XAU", "XAG", "XPT", "XPD", "XDR", "XTS", "XXX") }
        .sortedBy { it.displayName }
        .map { currency ->
            val code = currency.currencyCode
            val symbol = when (code) {
                "NPR" -> "रू"
                "INR" -> "₹"
                "USD" -> "$"
                "EUR" -> "€"
                "GBP" -> "£"
                "JPY" -> "¥"
                "CNY" -> "¥"
                "KRW" -> "₩"
                else -> currency.getSymbol(Locale.US).takeIf { it.isNotBlank() && it != "¤" } ?: code
            }
            code to "$symbol ${currency.displayName} ($code)"
        }
}

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val amount: Double,
    val category: String,
    val note: String
)

data class Goal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val target: Double,
    val saved: Double = 0.0,
    val emoji: String = "🎯"
)

data class AppData(
    val transactions: List<Transaction> = emptyList(),
    val monthlyBudget: Double = 0.0,
    val goals: List<Goal> = emptyList(),
    val hasStarted: Boolean = false,
    val profileName: String = "FinCat User",
    val notificationsEnabled: Boolean = true,
    val currencyCode: String = "INR",
    val darkMode: Boolean = false,
    val pinEnabled: Boolean = false,
    val pinHash: String = "",
    val welcomeSeen: Boolean = false
)

private const val PREFS = "fincat_data"
private const val KEY_DATA = "app_data"

private fun loadData(context: Context): AppData {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_DATA, null) ?: return AppData()

    return try {
        val root = JSONObject(raw)
        val txs = mutableListOf<Transaction>()
        val txArray = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txArray.length()) {
            val o = txArray.getJSONObject(i)
            txs += Transaction(
                id = o.optString("id", UUID.randomUUID().toString()),
                type = o.optString("type", "Expense"),
                amount = o.optDouble("amount", 0.0),
                category = o.optString("category", "Other"),
                note = o.optString("note", "")
            )
        }

        val goals = mutableListOf<Goal>()
        val goalArray = root.optJSONArray("goals") ?: JSONArray()
        for (i in 0 until goalArray.length()) {
            val o = goalArray.getJSONObject(i)
            goals += Goal(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Goal"),
                target = o.optDouble("target", 0.0),
                saved = o.optDouble("saved", 0.0),
                emoji = o.optString("emoji", "🎯")
            )
        }

        AppData(
            transactions = txs,
            monthlyBudget = root.optDouble("monthlyBudget", 0.0),
            goals = goals,
            hasStarted = root.optBoolean("hasStarted", false),
            profileName = root.optString("profileName", "FinCat User"),
            notificationsEnabled = root.optBoolean("notificationsEnabled", true),
            currencyCode = root.optString("currencyCode", "INR"),
            darkMode = root.optBoolean("darkMode", false),
            pinEnabled = root.optBoolean("pinEnabled", false),
            pinHash = root.optString("pinHash", ""),
            welcomeSeen = root.optBoolean("welcomeSeen", false)
        )
    } catch (_: Exception) {
        AppData()
    }
}

private fun saveData(context: Context, data: AppData) {
    val root = JSONObject()
    root.put("monthlyBudget", data.monthlyBudget)
    root.put("hasStarted", data.hasStarted)
    root.put("profileName", data.profileName)
    root.put("notificationsEnabled", data.notificationsEnabled)
    root.put("currencyCode", data.currencyCode)
    root.put("darkMode", data.darkMode)
    root.put("pinEnabled", data.pinEnabled)
    root.put("pinHash", data.pinHash)
    root.put("welcomeSeen", data.welcomeSeen)

    val txArray = JSONArray()
    data.transactions.forEach {
        txArray.put(JSONObject().apply {
            put("id", it.id)
            put("type", it.type)
            put("amount", it.amount)
            put("category", it.category)
            put("note", it.note)
        })
    }
    root.put("transactions", txArray)

    val goalArray = JSONArray()
    data.goals.forEach {
        goalArray.put(JSONObject().apply {
            put("id", it.id)
            put("name", it.name)
            put("target", it.target)
            put("saved", it.saved)
            put("emoji", it.emoji)
        })
    }
    root.put("goals", goalArray)

    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_DATA, root.toString())
        .apply()
}

private fun dataToJson(data: AppData): String {
    val root = JSONObject()
    root.put("monthlyBudget", data.monthlyBudget)
    root.put("hasStarted", data.hasStarted)
    root.put("profileName", data.profileName)
    root.put("notificationsEnabled", data.notificationsEnabled)
    root.put("currencyCode", data.currencyCode)
    root.put("darkMode", data.darkMode)
    root.put("pinEnabled", data.pinEnabled)
    root.put("pinHash", data.pinHash)
    root.put("welcomeSeen", data.welcomeSeen)
    root.put("transactions", JSONArray().apply {
        data.transactions.forEach { tx ->
            put(JSONObject().apply {
                put("id", tx.id); put("type", tx.type); put("amount", tx.amount)
                put("category", tx.category); put("note", tx.note)
            })
        }
    })
    root.put("goals", JSONArray().apply {
        data.goals.forEach { goal ->
            put(JSONObject().apply {
                put("id", goal.id); put("name", goal.name); put("target", goal.target)
                put("saved", goal.saved); put("emoji", goal.emoji)
            })
        }
    })
    return root.toString(2)
}

private fun parseBackup(json: String): AppData? = try {
    val root = JSONObject(json)
    val txs = mutableListOf<Transaction>()
    val txArray = root.optJSONArray("transactions") ?: JSONArray()
    for (i in 0 until txArray.length()) {
        val o = txArray.getJSONObject(i)
        txs += Transaction(
            id = o.optString("id", UUID.randomUUID().toString()),
            type = o.optString("type", "Expense"),
            amount = o.optDouble("amount", 0.0),
            category = o.optString("category", "Other"),
            note = o.optString("note", "")
        )
    }
    val goals = mutableListOf<Goal>()
    val goalArray = root.optJSONArray("goals") ?: JSONArray()
    for (i in 0 until goalArray.length()) {
        val o = goalArray.getJSONObject(i)
        goals += Goal(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Goal"),
            target = o.optDouble("target", 0.0),
            saved = o.optDouble("saved", 0.0),
            emoji = o.optString("emoji", "🎯")
        )
    }
    AppData(
        transactions = txs,
        monthlyBudget = root.optDouble("monthlyBudget", 0.0),
        goals = goals,
        hasStarted = root.optBoolean("hasStarted", true),
        profileName = root.optString("profileName", "FinCat User"),
        notificationsEnabled = root.optBoolean("notificationsEnabled", true),
        currencyCode = root.optString("currencyCode", "INR"),
        darkMode = root.optBoolean("darkMode", false)
    )
} catch (_: Exception) { null }

private fun currencySymbol(code: String): String = when (code) {
    "INR" -> "₹"
    "NPR" -> "रू"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "AUD" -> "A$"
    "CAD" -> "C$"
    else -> code
}

private fun money(value: Double, currencyCode: String): String =
    NumberFormat.getCurrencyInstance(Locale.US).apply {
        currency = java.util.Currency.getInstance(currencyCode)
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }.format(value)

private fun hashPin(pin: String): String = MessageDigest.getInstance("SHA-256")
    .digest(pin.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

class FinCatSounds(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private var released = false

    private fun play(resId: Int) {
        if (released) return

        try {
            // MediaPlayer is deliberately used here instead of SoundPool. It is more
            // reliable on Android Studio emulators for short UI sounds and uses the
            // normal media/game audio stream.
            val player = android.media.MediaPlayer.create(context, resId) ?: return
            player.setOnCompletionListener { mp -> mp.release() }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                true
            }
            player.setVolume(1f, 1f)
            player.start()
        } catch (_: Exception) {
            // Never let an audio problem interrupt the app UI.
        }
    }

    fun playMeow() = play(R.raw.meow)
    fun playCoin(income: Boolean) = play(if (income) R.raw.coin_income else R.raw.coin_expense)
    fun playYay() = play(R.raw.yay)

    fun release() {
        released = true
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC)
        setContent { FincatApp() }
    }
}

@Composable
fun FincatApp() {
    val context = LocalContext.current
    val sounds = remember { FinCatSounds(context) }
    DisposableEffect(Unit) { onDispose { sounds.release() } }
    var data by remember { mutableStateOf(loadData(context)) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showTransactionDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var pinUnlocked by remember(data.pinEnabled, data.pinHash) { mutableStateOf(!data.pinEnabled) }
    var completedGoalCelebration by remember { mutableStateOf<Goal?>(null) }
    var incomeCelebration by remember { mutableStateOf(false) }
    var expenseCelebration by remember { mutableStateOf(false) }
    // Show the FinCat welcome journey on every fresh app launch.
    // The button only dismisses it for the current app session.
    var showLaunchWelcome by remember { mutableStateOf(true) }

    LaunchedEffect(data) { saveData(context, data) }

    CompositionLocalProvider(LocalCurrency provides data.currencyCode) {
        MaterialTheme(
            colorScheme = if (data.darkMode) darkColorScheme(primary = Green) else lightColorScheme(
                primary = Green,
                background = Paper,
                surface = CardColor
            )
        ) {
            if (showLaunchWelcome) {
                WelcomeScreen(onGetStarted = {
                    sounds.playMeow()
                    showLaunchWelcome = false
                    data = data.copy(hasStarted = true, welcomeSeen = true)
                })
                return@MaterialTheme
            }

            if (data.pinEnabled && !pinUnlocked) {
                PinUnlockScreen(
                    onUnlock = { pin -> (hashPin(pin) == data.pinHash).also { if (it) pinUnlocked = true } }
                )
                return@MaterialTheme
            }

            Scaffold(
                containerColor = Paper,
                bottomBar = {
                    NavigationBar(containerColor = CardColor, tonalElevation = 8.dp) {
                        NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                        NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.BarChart, null) }, label = { Text("Budget") })
                        NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Goals") })
                        NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.MoreHoriz, null) }, label = { Text("More") })
                    }
                }
            ) { padding ->
                when (selectedTab) {
                    0 -> HomeScreen(Modifier.padding(padding), data, { showTransactionDialog = true }, { id -> data = data.copy(transactions = data.transactions.filterNot { it.id == id }) }, { showBudgetDialog = true })
                    1 -> BudgetScreen(Modifier.padding(padding), data, { showBudgetDialog = true })
                    2 -> GoalsScreen(Modifier.padding(padding), data, { showGoalDialog = true }, { id ->
                        data = data.copy(goals = data.goals.filterNot { it.id == id })
                    }, { id, amount ->
                        val current = data.goals.firstOrNull { it.id == id }
                        if (current != null) {
                            val newSaved = (current.saved + amount).coerceAtMost(current.target)
                            val completedNow = current.target > 0 && current.saved < current.target && newSaved >= current.target
                            data = data.copy(goals = data.goals.map { if (it.id == id) it.copy(saved = newSaved) else it })
                            if (completedNow) {
                                sounds.playYay()
                                completedGoalCelebration = current.copy(saved = newSaved)
                            }
                        }
                    })
                    3 -> MoreScreen(
                        modifier = Modifier.padding(padding), data = data,
                        onUpdate = { data = it },
                        onReset = { data = data.copy(transactions = emptyList(), monthlyBudget = 0.0, goals = emptyList(), hasStarted = true); selectedTab = 0 }
                    )
                }
            }

            if (showTransactionDialog) AddTransactionDialog({ showTransactionDialog = false }) { tx ->
                data = data.copy(transactions = listOf(tx) + data.transactions)
                val isIncome = tx.type == "Income"
                sounds.playCoin(isIncome)
                if (isIncome) incomeCelebration = true else expenseCelebration = true
                showTransactionDialog = false
            }
            if (showBudgetDialog) BudgetDialog(data.monthlyBudget, { showBudgetDialog = false }) { amount -> data = data.copy(monthlyBudget = amount); showBudgetDialog = false }
            if (showGoalDialog) GoalDialog({ showGoalDialog = false }) { goal -> data = data.copy(goals = data.goals + goal); showGoalDialog = false }
            completedGoalCelebration?.let { goal ->
                GoalCompletedCelebration(goal = goal, onDismiss = { completedGoalCelebration = null })
            }
            if (incomeCelebration) {
                IncomeCelebration(onFinished = { incomeCelebration = false })
            }
            if (expenseCelebration) {
                ExpenseChaseCelebration(onFinished = { expenseCelebration = false })
            }
        }
    }
}

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    // 12-frame game-style bank journey, looping until the user taps
    // “Let's Get Started”. The frames contain the character movement.
    val frameIds = remember {
        listOf(
            R.drawable.fincat_sprite_01, // Idle
            R.drawable.fincat_sprite_02, // Walk 1
            R.drawable.fincat_sprite_03, // Walk 2
            R.drawable.fincat_sprite_04, // Walk 3
            R.drawable.fincat_sprite_05, // Walk 4
            R.drawable.fincat_sprite_06, // Jump prep
            R.drawable.fincat_sprite_07, // Jump 1
            R.drawable.fincat_sprite_08, // Jump 2
            R.drawable.fincat_sprite_09, // Land
            R.drawable.fincat_sprite_10, // Arrive at bank
            R.drawable.fincat_sprite_11, // Hand in money
            R.drawable.fincat_sprite_12  // Celebrate
        )
    }
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(115)
            frame = (frame + 1) % frameIds.size
        }
    }

    Box(Modifier.fillMaxSize().background(Paper)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Keep only the FinCat title above the animation, using the same
            // retro font but larger for a stronger welcome-screen identity.
            Text(
                "MyFincat",
                fontFamily = RetroFont,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = DarkGreen
            )
            Spacer(Modifier.height(8.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 520.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(frameIds[frame]),
                    contentDescription = "FinCat bank journey animation",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) {
                Text("Let's Get Started  →", fontFamily = RetroFont, fontSize = 15.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier, data: AppData, onAddTransaction: () -> Unit, onDeleteTransaction: (String) -> Unit, onBudgetClick: () -> Unit) {
    val currency = LocalCurrency.current
    val income = data.transactions.filter { it.type == "Income" }.sumOf { it.amount }
    val expenses = data.transactions.filter { it.type == "Expense" }.sumOf { it.amount }
    val balance = income - expenses
    val budgetProgress = if (data.monthlyBudget > 0) (expenses / data.monthlyBudget).coerceIn(0.0, 1.0).toFloat() else 0f
    val animatedBalance by animateFloatAsState(balance.toFloat(), animationSpec = tween(700), label = "balance-number")
    val animatedBudgetProgress by animateFloatAsState(budgetProgress, animationSpec = tween(900), label = "budget-progress")
    val catTransition = rememberInfiniteTransition(label = "home-cat")
    val coinTransition = rememberInfiniteTransition(label = "home-coins")
    val coinBob by coinTransition.animateFloat(-5f, 5f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "coin-bob")
    val catBob by catTransition.animateFloat(-3f, 3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "home-cat-bob")
    LazyColumn(modifier.fillMaxSize().background(Paper), contentPadding = PaddingValues(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("FINCAT", fontFamily = RetroFont, fontSize = 12.sp, letterSpacing = 2.sp, color = Green)
                    Text("Hello, ${data.profileName} ✦", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                    Text("Small steps, big savings.", fontFamily = RetroFont, fontSize = 12.sp, color = Ink)
                }
                Box(Modifier.size(96.dp)) {
                    Image(
                        painter = painterResource(R.drawable.cat_money_mascot),
                        contentDescription = "FinCat mascot holding a coin",
                        modifier = Modifier.size(86.dp).align(Alignment.Center).offset(y = catBob.dp).clip(RoundedCornerShape(22.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Text("🪙", fontSize = 18.sp, modifier = Modifier.align(Alignment.TopEnd).offset(y = coinBob.dp))
                    Text("✨", fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomStart).offset(y = (-8).dp))
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SoftGreen), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("CURRENT BALANCE", fontFamily = RetroFont, fontSize = 12.sp, letterSpacing = 1.sp)
                    Text(money(animatedBalance.toDouble(), currency), fontFamily = RetroFont, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = DarkGreen, modifier = Modifier.animateContentSize())
                    Text(if (data.transactions.isEmpty()) "🐱 Your FinCat journey starts at zero." else if (balance >= 0) "🐱 Small steps, big savings." else "🐱 Keep an eye on your spending.")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        item {
            Card(onClick = onBudgetClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CardColor)) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Monthly Budget", fontWeight = FontWeight.Bold); Text(if (data.monthlyBudget > 0) "${(budgetProgress * 100).toInt()}%" else "Not set") }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { animatedBudgetProgress }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), color = Green)
                    Spacer(Modifier.height(8.dp))
                    Text(if (data.monthlyBudget > 0) "${money(expenses, currency)} / ${money(data.monthlyBudget, currency)}" else "Tap to set a monthly budget")
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        item { Text("Recent Transactions", fontSize = 19.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)) }
        if (data.transactions.isEmpty()) {
            item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("🐱💭", fontSize = 42.sp); Spacer(Modifier.height(8.dp)); Text("No transactions yet", fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("Add your first income or expense below.") } } }
        } else items(data.transactions, key = { it.id }) { tx -> TransactionRow(tx, onDelete = { onDeleteTransaction(tx.id) }) }
        item { Spacer(Modifier.height(10.dp)); Button(onClick = onAddTransaction, modifier = Modifier.fillMaxWidth().height(55.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Green)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add Transaction", fontFamily = RetroFont) }; Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun TransactionRow(tx: Transaction, onDelete: () -> Unit) {
    val currency = LocalCurrency.current
    val isIncome = tx.type == "Income"
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (isIncome) "💰" else categoryEmoji(tx.category), fontSize = 25.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(tx.category, fontSize = 16.sp, fontWeight = FontWeight.Medium); if (tx.note.isNotBlank()) Text(tx.note, fontSize = 12.sp) }
        Text("${if (isIncome) "+" else "-"} ${money(tx.amount, currency)}", fontWeight = FontWeight.Bold, color = if (isIncome) DarkGreen else Color.Unspecified)
        IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete transaction") }
    }
}

private fun categoryEmoji(category: String): String = when (category.lowercase()) {
    "food" -> "🍜"; "transport" -> "🚌"; "shopping" -> "🛍️"; "entertainment" -> "🎮"; "bills" -> "🧾"; "health" -> "💊"; "education" -> "📚"; "salary", "income" -> "💰"; else -> "💸"
}

@Composable
fun AddTransactionDialog(onDismiss: () -> Unit, onSave: (Transaction) -> Unit) {
    val currency = LocalCurrency.current
    var type by remember { mutableStateOf("Expense") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var note by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    val categories = listOf("Food", "Transport", "Shopping", "Entertainment", "Bills", "Health", "Education", "Salary", "Other")
    val validAmount = amount.toDoubleOrNull()?.let { it > 0 } == true
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add Transaction 🐾") }, text = {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = type == "Expense", onClick = { type = "Expense" }, label = { Text("Expense") }); FilterChip(selected = type == "Income", onClick = { type = "Income" }, label = { Text("Income") }) }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount") }, prefix = { Text("${currencySymbol(currency)} ") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            Box {
                OutlinedButton(onClick = { categoryExpanded = true }, Modifier.fillMaxWidth()) { Text("${categoryEmoji(category)}  $category") }
                DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) { categories.forEach { item -> DropdownMenuItem(text = { Text("${categoryEmoji(item)}  $item") }, onClick = { category = item; categoryExpanded = false }) } }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, singleLine = true)
        }
    }, confirmButton = { Button(enabled = validAmount, onClick = { onSave(Transaction(type = type, amount = amount.toDouble(), category = category, note = note.trim())) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun ExpenseChaseCelebration(onFinished: () -> Unit) {
    // Gentle expense reaction: FinCat stays close to the coin, looking like it is chasing it.
    LaunchedEffect(Unit) {
        delay(1500)
        onFinished()
    }

    val bounce by rememberInfiniteTransition(label = "expense-cat-bounce").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "expense-cat-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(180),
        label = "expense-cat-alpha"
    )

    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 82.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.fincat_sprite_02),
                    contentDescription = "FinCat chasing a coin",
                    modifier = Modifier
                        .size(74.dp)
                        .graphicsLayer {
                            scaleX = bounce
                            scaleY = bounce
                        },
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "🪙",
                    fontSize = 31.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column {
                    Text("Money spent!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                }
            }
        }
    }
}

@Composable
fun IncomeCelebration(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        onFinished()
    }

    val bounce by rememberInfiniteTransition(label = "income-cat-bounce").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "income-cat-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(180),
        label = "income-cat-alpha"
    )

    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 82.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.fincat_sprite_12),
                    contentDescription = "Happy FinCat celebrating income",
                    modifier = Modifier
                        .size(74.dp)
                        .graphicsLayer {
                            scaleX = bounce
                            scaleY = bounce
                        },
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Yay! 💰", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                    Text("Money earned!", fontSize = 12.sp, color = Ink)
                }
            }
        }
    }
}

@Composable
fun BudgetScreen(modifier: Modifier, data: AppData, onSetBudget: () -> Unit) {
    val currency = LocalCurrency.current
    val expenses = data.transactions.filter { it.type == "Expense" }.sumOf { it.amount }
    val progress = if (data.monthlyBudget > 0) (expenses / data.monthlyBudget).coerceIn(0.0, 1.0).toFloat() else 0f
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("My Budget", fontSize = 28.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(20.dp)) { Text("Monthly budget", fontWeight = FontWeight.Bold); Text(if (data.monthlyBudget > 0) money(data.monthlyBudget, currency) else "${money(0.0, currency)}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DarkGreen); Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)); Spacer(Modifier.height(8.dp)); Text("${money(expenses, currency)} spent"); Spacer(Modifier.height(14.dp)); Button(onClick = onSetBudget) { Text("Set / Change Budget") } }
        }
        Spacer(Modifier.height(18.dp)); Text("Category spending is calculated from your transactions.", fontSize = 15.sp)
    }
}

@Composable
fun GoalsScreen(modifier: Modifier, data: AppData, onAddGoal: () -> Unit, onDeleteGoal: (String) -> Unit, onAddToGoal: (String, Double) -> Unit) {
    LazyColumn(modifier.fillMaxSize().padding(20.dp)) {
        item { Text("My Goals 🎯", fontSize = 28.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Start from zero and build toward something you want.", fontSize = 17.sp); Spacer(Modifier.height(16.dp)); Button(onClick = onAddGoal, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add Goal") }; Spacer(Modifier.height(16.dp)) }
        if (data.goals.isEmpty()) item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("🎯🐱", fontSize = 42.sp); Text("No goals yet", fontWeight = FontWeight.Bold) } } }
        else items(data.goals, key = { it.id }) { goal -> GoalCard(goal, onAddToGoal, onDeleteGoal) }
    }
}

@Composable
fun GoalCompletedCelebration(goal: Goal, onDismiss: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "goal-treat-celebration")
    val catBob by transition.animateFloat(0f, -10f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "cat-bob")
    val treatY by transition.animateFloat(55f, -18f, infiniteRepeatable(tween(1150), RepeatMode.Restart), label = "treat-to-cat")
    val treatScale by transition.animateFloat(0.85f, 1.12f, infiniteRepeatable(tween(575), RepeatMode.Reverse), label = "treat-pulse")
    var visible by remember { mutableStateOf(true) }

    if (!visible) return

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(0.88f).animateContentSize(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎉 Goal complete!", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                Spacer(Modifier.height(6.dp))
                Text("${goal.emoji} ${goal.name}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))

                Box(Modifier.height(190.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("✨", fontSize = 30.sp, modifier = Modifier.offset(x = (-90).dp, y = (-48).dp))
                    Text("✨", fontSize = 24.sp, modifier = Modifier.offset(x = 92.dp, y = (-28).dp))
                    Text("🦋", fontSize = 22.sp, modifier = Modifier.offset(x = (-92).dp, y = 50.dp).graphicsLayer { alpha = 0.8f })
                    Text("🦋", fontSize = 19.sp, modifier = Modifier.offset(x = 88.dp, y = 58.dp).graphicsLayer { alpha = 0.75f })

                    Image(
                        painter = painterResource(id = R.drawable.cat_money_mascot),
                        contentDescription = "FinCat celebrating",
                        modifier = Modifier
                            .size(145.dp)
                            .offset(y = catBob.dp),
                        contentScale = ContentScale.Fit
                    )

                    Text(
                        "🍪",
                        fontSize = 34.sp,
                        modifier = Modifier
                            .offset(x = 48.dp, y = treatY.dp)
                            .graphicsLayer {
                                scaleX = treatScale
                                scaleY = treatScale
                            }
                    )
                }

                Text("Your FinCat earned a treat!", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text("Nom nom! You reached ${money(goal.target, LocalCurrency.current)}.", fontSize = 15.sp, color = Ink)
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { visible = false; onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Give FinCat the treat 🐱🍪")
                }
            }
        }
    }
}

@Composable
fun GoalCard(goal: Goal, onAddToGoal: (String, Double) -> Unit, onDeleteGoal: (String) -> Unit) {
    val currency = LocalCurrency.current
    var showAdd by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val progress = if (goal.target > 0) (goal.saved / goal.target).coerceIn(0.0, 1.0).toFloat() else 0f
    Card(Modifier.fillMaxWidth().padding(vertical = 7.dp), colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${goal.emoji}  ${goal.name}", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete goal")
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(9.dp).clip(CircleShape))
            Spacer(Modifier.height(6.dp))
            Text("${money(goal.saved, currency)} / ${money(goal.target, currency)}")
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { showAdd = true }) { Text("Add to goal") }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete goal?") },
            text = { Text("Delete \"${goal.name}\"? This will remove the goal and its saved progress.") },
            confirmButton = {
                Button(onClick = { showDeleteConfirm = false; onDeleteGoal(goal.id) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showAdd) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("Add money to ${goal.name}") }, text = { OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount") }, prefix = { Text("${currencySymbol(currency)} ") }, singleLine = true) }, confirmButton = { Button(enabled = amount.toDoubleOrNull()?.let { it > 0 } == true, onClick = { onAddToGoal(goal.id, amount.toDouble()); showAdd = false }) { Text("Add") } }, dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } })
    }
}

@Composable
fun MoreScreen(modifier: Modifier, data: AppData, onUpdate: (AppData) -> Unit, onReset: () -> Unit) {
    var dialog by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                val restored = json?.let { parseBackup(it) }
                if (restored != null) { onUpdate(restored); backupMessage = "Backup restored successfully." }
                else backupMessage = "That file is not a valid FinCat backup."
            } catch (_: Exception) { backupMessage = "Could not read the backup file." }
        }
    }

    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("MORE", fontFamily = RetroFont, fontSize = 12.sp, letterSpacing = 2.sp, color = Green)
        Text("FinCat settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
        Spacer(Modifier.height(18.dp))
        SettingsRow("👤", "Account & Profile", "${data.profileName}") { dialog = "profile" }
        SettingsRow("🔔", "Notifications", if (data.notificationsEnabled) "On" else "Off") { dialog = "notifications" }
        SettingsRow("💱", "Currency", supportedCurrencies.firstOrNull { it.first == data.currencyCode }?.second ?: data.currencyCode) { dialog = "currency" }
        SettingsRow("☁️", "Backup & Sync", "Backup, restore, and share your data") { dialog = "backup" }
        SettingsRow("🎨", "App Appearance", if (data.darkMode) "Dark mode" else "Light mode") { dialog = "appearance" }
        SettingsRow("🔐", "App PIN", if (data.pinEnabled) "PIN lock is on" else "Protect FinCat with a PIN") { dialog = "pin" }
        SettingsRow("❓", "Help & Support", "FAQs and contact support") { dialog = "help" }
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset all financial data") }
        backupMessage?.let { Text(it, modifier = Modifier.padding(top = 12.dp), color = DarkGreen) }
    }

    when (dialog) {
        "profile" -> ProfileDialog(data.profileName, { dialog = null }) { name -> onUpdate(data.copy(profileName = name)); dialog = null }
        "notifications" -> NotificationDialog(data.notificationsEnabled, { dialog = null }) { enabled -> onUpdate(data.copy(notificationsEnabled = enabled)); dialog = null }
        "currency" -> CurrencyDialog(data.currencyCode, { dialog = null }) { code -> onUpdate(data.copy(currencyCode = code)); dialog = null }
        "appearance" -> AppearanceDialog(data.darkMode, { dialog = null }) { dark -> onUpdate(data.copy(darkMode = dark)); dialog = null }
        "pin" -> PinSettingsDialog(
            enabled = data.pinEnabled,
            onDismiss = { dialog = null },
            onSave = { enabled, pin -> onUpdate(data.copy(pinEnabled = enabled, pinHash = if (enabled) hashPin(pin) else "")); dialog = null }
        )
        "backup" -> BackupDialog(
            onDismiss = { dialog = null },
            onCopy = { clipboard.setText(AnnotatedString(dataToJson(data))); backupMessage = "Backup JSON copied to clipboard."; dialog = null },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, dataToJson(data)) }
                context.startActivity(Intent.createChooser(send, "Share FinCat backup")); dialog = null
            },
            onRestore = { launcher.launch(arrayOf("application/json", "text/plain", "*/*")); dialog = null }
        )
        "help" -> HelpDialog({ dialog = null }) {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@fincat.app")).apply { putExtra(Intent.EXTRA_SUBJECT, "FinCat Help") }
            try { context.startActivity(intent) } catch (_: Exception) { backupMessage = "No email app is configured on this device." }
        }
    }

    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Start over?") }, text = { Text("This removes all transactions, budget, and goals and returns the app to zero. Your settings stay unchanged.") }, confirmButton = { Button(onClick = { confirmReset = false; onReset() }) { Text("Reset") } }, dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } })
}

@Composable
fun PinUnlockScreen(onUnlock: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth().padding(24.dp), colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(5.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🐱🔐", fontSize = 52.sp)
                Spacer(Modifier.height(8.dp))
                Text("Welcome back to FinCat", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DarkGreen)
                Text("Enter your PIN to open your money garden.", fontFamily = RetroFont, fontSize = 12.sp, color = Ink)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { pin = it; wrong = false } }, label = { Text("PIN") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                if (wrong) Text("That PIN isn't correct. Try again.", color = Blush, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { if (pin.length >= 4) { val before = pin; val success = onUnlock(before); if (success) { pin = ""; wrong = false } else wrong = true } else wrong = true }, enabled = pin.length >= 4, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Unlock FinCat", fontFamily = RetroFont) }
            }
        }
    }
}

@Composable
fun PinSettingsDialog(enabled: Boolean, onDismiss: () -> Unit, onSave: (Boolean, String) -> Unit) {
    var isEnabled by remember { mutableStateOf(enabled) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("App PIN") }, text = {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("Lock FinCat with a PIN"); Switch(checked = isEnabled, onCheckedChange = { isEnabled = it; error = "" }) }
            if (isEnabled) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, label = { Text("4–6 digit PIN") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = confirm, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirm = it }, label = { Text("Confirm PIN") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                if (error.isNotBlank()) Text(error, color = Blush, fontSize = 12.sp)
            }
        }
    }, confirmButton = { Button(onClick = { if (!isEnabled) onSave(false, "") else if (pin.length < 4) error = "Use at least 4 digits." else if (pin != confirm) error = "The PINs do not match." else onSave(true, pin) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun SettingsRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = CardColor), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontFamily = RetroFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(subtitle, fontSize = 12.sp, color = Ink) }; Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
fun ProfileDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Account & Profile") }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Your name") }, singleLine = true) }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun NotificationDialog(enabled: Boolean, onDismiss: () -> Unit, onSave: (Boolean) -> Unit) {
    var value by remember { mutableStateOf(enabled) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Notifications") }, text = { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("Enable FinCat notifications"); Switch(checked = value, onCheckedChange = { value = it }) } }, confirmButton = { Button(onClick = { onSave(value) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun CurrencyDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        supportedCurrencies.filter { (code, label) ->
            query.isBlank() || code.contains(query, ignoreCase = true) || label.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Choose your currency 💱")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search currencies") },
                    placeholder = { Text("e.g. NPR, Dollar, Yen") }
                )
            }
        },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(filtered, key = { it.first }) { (code, label) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == code, onClick = { selected = code })
                        Text(label, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(selected) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AppearanceDialog(darkMode: Boolean, onDismiss: () -> Unit, onSave: (Boolean) -> Unit) {
    var dark by remember { mutableStateOf(darkMode) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("App Appearance") }, text = { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(if (dark) "Dark mode" else "Light mode"); Switch(checked = dark, onCheckedChange = { dark = it }) } }, confirmButton = { Button(onClick = { onSave(dark) }) { Text("Apply") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun BackupDialog(onDismiss: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit, onRestore: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Backup & Sync") }, text = { Text("Create a portable backup of your transactions, budget, goals, and settings. You can copy it, share it, or restore a previous backup file." ) }, confirmButton = { Button(onClick = onCopy) { Text("Copy Backup") } }, dismissButton = { Column { TextButton(onClick = onShare) { Text("Share Backup") }; TextButton(onClick = onRestore) { Text("Restore Backup") }; TextButton(onClick = onDismiss) { Text("Close") } } })
}

@Composable
fun HelpDialog(onDismiss: () -> Unit, onContact: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Help & Support") }, text = { Column { Text("FinCat starts at zero. Add income and expenses from Home, set your budget in Budget, and create savings goals in Goals."); Spacer(Modifier.height(12.dp)); Text("If something is not working, you can contact support from the button below.") } }, confirmButton = { Button(onClick = onContact) { Text("Contact Support") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
fun BudgetDialog(current: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    val currency = LocalCurrency.current
    var amount by remember { mutableStateOf(if (current == 0.0) "" else current.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Monthly Budget") }, text = { OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Budget amount") }, prefix = { Text("${currencySymbol(currency)} ") }, singleLine = true) }, confirmButton = { Button(enabled = amount.toDoubleOrNull()?.let { it >= 0 } == true, onClick = { onSave(amount.toDouble()) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun GoalDialog(onDismiss: () -> Unit, onSave: (Goal) -> Unit) {
    val currency = LocalCurrency.current
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🎯") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create a Goal") }, text = { Column { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Goal name") }, singleLine = true); Spacer(Modifier.height(10.dp)); OutlinedTextField(value = target, onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Target amount") }, prefix = { Text("${currencySymbol(currency)} ") }, singleLine = true); Spacer(Modifier.height(10.dp)); OutlinedTextField(value = emoji, onValueChange = { emoji = it.take(2) }, label = { Text("Emoji") }, singleLine = true) } }, confirmButton = { Button(enabled = name.isNotBlank() && target.toDoubleOrNull()?.let { it > 0 } == true, onClick = { onSave(Goal(name = name.trim(), target = target.toDouble(), emoji = emoji.ifBlank { "🎯" })) }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
