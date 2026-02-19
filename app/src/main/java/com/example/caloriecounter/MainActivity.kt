package com.example.caloriecounter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- DATA CLASSES ---
data class UserProfile(
    val weightKg: Float,
    val heightCm: Float,
    val age: Int,
    val isMale: Boolean,
    val activityLevel: Float,
    val dailyCalorieTarget: Int
)

data class FoodItem(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val calories: String,
    val protein: String,
    val carbs: String,
    val fat: String,
    val mealType: String,
    val dateString: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    primary = Color(0xFFBB86FC),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainAppOrchestrator()
                }
            }
        }
    }
}

// --- STORAGE HELPERS ---
fun saveUserProfile(context: Context, profile: UserProfile?) {
    val editor = context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE).edit()
    if (profile == null) {
        editor.remove("user_profile").apply()
    } else {
        editor.putString("user_profile", Gson().toJson(profile)).apply()
    }
}

fun loadUserProfile(context: Context): UserProfile? {
    val json = context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE).getString("user_profile", null)
    return if (json != null) Gson().fromJson(json, UserProfile::class.java) else null
}

fun saveLog(context: Context, list: List<FoodItem>) {
    context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE)
        .edit().putString("daily_log_v3", Gson().toJson(list)).apply()
}

fun loadLog(context: Context): List<FoodItem> {
    val json = context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE).getString("daily_log_v3", null)
    return if (json != null) Gson().fromJson(json, object : TypeToken<List<FoodItem>>() {}.type) else emptyList()
}

// --- ORCHESTRATOR ---
@Composable
fun MainAppOrchestrator() {
    val context = LocalContext.current
    var userProfile by remember { mutableStateOf(loadUserProfile(context)) }

    if (userProfile == null) {
        OnboardingScreen { newProfile ->
            saveUserProfile(context, newProfile)
            userProfile = newProfile
        }
    } else {
        CalorieTrackerScreen(
            userProfile = userProfile!!,
            onEditProfile = {
                // Clear the profile so the Onboarding screen shows up again
                saveUserProfile(context, null)
                userProfile = null
            }
        )
    }
}

// --- SCREEN 1: ONBOARDING ---
@Composable
fun OnboardingScreen(onComplete: (UserProfile) -> Unit) {
    var isManualEntry by remember { mutableStateOf(false) }
    var manualCalories by remember { mutableStateOf("") }

    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var isMale by remember { mutableStateOf(true) }
    var activityMultiplier by remember { mutableStateOf(1.2f) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Setup Your Profile", style = MaterialTheme.typography.displaySmall, color = Color(0xFFBB86FC))
        Spacer(modifier = Modifier.height(32.dp))

        // TOGGLE SWITCH
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Calculate for me", color = if(!isManualEntry) Color.White else Color.Gray)
            Switch(
                checked = isManualEntry,
                onCheckedChange = { isManualEntry = it },
                modifier = Modifier.padding(horizontal = 8.dp),
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF03DAC5))
            )
            Text("Enter Custom", color = if(isManualEntry) Color.White else Color.Gray)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isManualEntry) {
            OutlinedTextField(
                value = manualCalories, onValueChange = { manualCalories = it },
                label = { Text("Daily Calorie Limit (e.g. 2200)") }, modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (kg)") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Height (cm)") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilterChip(selected = isMale, onClick = { isMale = true }, label = { Text("Male") })
                FilterChip(selected = !isMale, onClick = { isMale = false }, label = { Text("Female") })
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Activity Level", color = Color.Gray)
            Column(Modifier.fillMaxWidth()) {
                ActivityOption("Sedentary (Desk Job)", 1.2f, activityMultiplier) { activityMultiplier = 1.2f }
                ActivityOption("Light (1-3 days/wk)", 1.375f, activityMultiplier) { activityMultiplier = 1.375f }
                ActivityOption("Moderate (3-5 days/wk)", 1.55f, activityMultiplier) { activityMultiplier = 1.55f }
                ActivityOption("Active (Heavy lifting)", 1.725f, activityMultiplier) { activityMultiplier = 1.725f }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (isManualEntry) {
                    val target = manualCalories.toIntOrNull()
                    if (target != null) onComplete(UserProfile(0f, 0f, 0, true, 0f, target))
                } else {
                    val w = weight.toFloatOrNull()
                    val h = height.toFloatOrNull()
                    val a = age.toIntOrNull()
                    if (w != null && h != null && a != null) {
                        val s = if (isMale) 5 else -161
                        val bmr = (10 * w) + (6.25 * h) - (5 * a) + s
                        val tdee = (bmr * activityMultiplier).toInt()
                        onComplete(UserProfile(w, h, a, isMale, activityMultiplier, tdee))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5))
        ) { Text("Save & Start", color = Color.Black, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun ActivityOption(text: String, value: Float, selectedValue: Float, onSelect: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (value == selectedValue), onClick = onSelect)
        Text(text, modifier = Modifier.padding(start = 8.dp), color = Color.White)
    }
}

// --- SCREEN 2: MAIN TRACKER ---
@Composable
fun CalorieTrackerScreen(userProfile: UserProfile, onEditProfile: () -> Unit) {
    val context = LocalContext.current
    val generativeModel = GenerativeModel(modelName = "gemini-flash-latest", apiKey = BuildConfig.API_KEY)

    var userInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var allTimeFoodLog by remember { mutableStateOf(loadLog(context)) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showHistoryScreen by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> selectedBitmap = BitmapFactory.decodeStream(stream) } }
    }

    val scope = rememberCoroutineScope()
    val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val todaysFoodLog = allTimeFoodLog.filter { it.dateString == todayString }

    fun String.extractFloat(): Float = Regex("[0-9]*\\.?[0-9]+").find(this)?.value?.toFloatOrNull() ?: 0f

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    val sb = StringBuilder("Date,Meal,Food Item,Calories,Protein,Carbs,Fat\n")
                    allTimeFoodLog.sortedByDescending { log -> log.dateString }.forEach { item ->
                        sb.append("${item.dateString},${item.mealType},${item.name},${item.calories},${item.protein},${item.carbs},${item.fat}\n")
                    }
                    stream.write(sb.toString().toByteArray())
                }
                Toast.makeText(context, "Full History Saved!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) { Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
        }
    }

    fun addFood(mealCategory: String) {
        if (userInput.isBlank() && selectedBitmap == null) return
        isLoading = true

        scope.launch {
            try {
                val promptText = """
                    You are a nutritionist. 
                    The user provided this description: "$userInput".
                    If an image is attached, identify the food. IF the user gives context (e.g., "ate half"), adjust the calories/macros accordingly!
                    Estimate Calories, Protein(g), Carbs(g), and Fat(g).
                    Output strictly in this CSV format per line: Name,Calories,Protein,Carbs,Fat
                    Example: 2 Porotta,450,10g,60g,15g
                    No headers. No markdown.
                """.trimIndent()

                val response = if (selectedBitmap != null) {
                    generativeModel.generateContent(content { image(selectedBitmap!!); text(promptText) })
                } else {
                    generativeModel.generateContent(promptText)
                }

                val newItems = (response.text ?: "").lines().mapNotNull { line ->
                    val parts = line.split(",")
                    if (parts.size >= 5) FoodItem(
                        id = System.nanoTime(), name = parts[0].trim(), calories = parts[1].trim(),
                        protein = parts[2].trim(), carbs = parts[3].trim(), fat = parts[4].trim(),
                        mealType = mealCategory, dateString = todayString
                    ) else null
                }

                val updatedList = allTimeFoodLog + newItems
                allTimeFoodLog = updatedList
                saveLog(context, updatedList)
                userInput = ""
                selectedBitmap = null
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally { isLoading = false }
        }
    }

    if (showHistoryScreen) {
        BackHandler { showHistoryScreen = false }
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                IconButton(onClick = { showHistoryScreen = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text("All Time History", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            }

            val groupedLogs = allTimeFoodLog.groupBy { it.dateString }.toSortedMap(reverseOrder())

            LazyColumn(modifier = Modifier.weight(1f)) {
                groupedLogs.forEach { (date, logs) ->
                    item { Text(date, color = Color(0xFF03DAC5), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) }
                    items(logs) { item -> FoodRow(item, onDelete = { val updated = allTimeFoodLog.filter { it.id != item.id }; allTimeFoodLog = updated; saveLog(context, updated) }) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { saveLauncher.launch("calorie_full_history.csv") }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5))) {
                Text("EXPORT ALL TO EXCEL", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) {

            // TOP BAR (SETTINGS & HISTORY)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onEditProfile) { // <-- NEW EDIT BUTTON
                    Icon(Icons.Default.Settings, "Edit Profile", tint = Color.Gray)
                }
                IconButton(onClick = { showHistoryScreen = true }) {
                    Icon(Icons.AutoMirrored.Filled.List, "History", tint = Color(0xFFBB86FC))
                }
            }

            val totalCals = todaysFoodLog.sumOf { it.calories.extractFloat().toInt() }
            val dailyGoal = userProfile.dailyCalorieTarget.toFloat()
            val progress = (totalCals / dailyGoal).coerceIn(0f, 1f)
            val barColor by animateColorAsState(if (progress >= 1f) Color(0xFFFF5252) else if (progress >= 0.85f) Color(0xFFFFD740) else Color(0xFF00E5FF))

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF252525)), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TODAY'S CALORIES", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 2.sp)
                    Text("$totalCals / ${dailyGoal.toInt()}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = barColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = barColor, trackColor = Color(0xFF333333))
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MacroStat("Protein", String.format("%.1fg", todaysFoodLog.sumOf { it.protein.extractFloat().toDouble() }), Color(0xFFBB86FC))
                        MacroStat("Carbs", String.format("%.1fg", todaysFoodLog.sumOf { it.carbs.extractFloat().toDouble() }), Color(0xFF4CAF50))
                        MacroStat("Fat", String.format("%.1fg", todaysFoodLog.sumOf { it.fat.extractFloat().toDouble() }), Color(0xFFFF9800))
                    }
                }
            }

            // INPUT SECTION
            if (selectedBitmap != null) {
                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                    Image(bitmap = selectedBitmap!!.asImageBitmap(), contentDescription = "Food", modifier = Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)))
                    IconButton(onClick = { selectedBitmap = null }, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))) {
                        Icon(Icons.Default.Clear, "Remove", tint = Color.White)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = userInput, onValueChange = { userInput = it },
                    label = { Text("Food name OR image details (e.g., 'ate half')") },
                    modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color.Gray)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)), modifier = Modifier.height(56.dp)) {
                    Text("📷", fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Breakfast", "Lunch", "Dinner", "Snack").forEach { meal ->
                    Button(onClick = { addFood(meal) }, enabled = !isLoading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE), contentColor = Color.White), modifier = Modifier.weight(1f).padding(horizontal = 2.dp), contentPadding = PaddingValues(0.dp)) {
                        Text(meal.take(5), fontSize = 11.sp)
                    }
                }
            }

            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), color = Color(0xFF00E5FF))

            Spacer(modifier = Modifier.height(24.dp))
            if (todaysFoodLog.isEmpty()) Text("No food logged today.", color = Color.Gray)
            else todaysFoodLog.forEach { item -> FoodRow(item, onDelete = { val updated = allTimeFoodLog.filter { it.id != item.id }; allTimeFoodLog = updated; saveLog(context, updated) }) }
        }
    }
}

@Composable
fun MacroStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun FoodRow(item: FoodItem, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.mealType.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFFBB86FC), fontWeight = FontWeight.Bold)
                Text(item.name, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Text("P: ${item.protein}  C: ${item.carbs}  F: ${item.fat}", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.calories, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFFF5252)) }
            }
        }
    }
}