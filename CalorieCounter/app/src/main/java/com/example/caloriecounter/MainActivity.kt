package com.example.caloriecounter

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

data class FoodItem(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val calories: String,
    val protein: String,
    val carbs: String,
    val fat: String,
    val mealType: String
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CalorieTrackerScreen()
                }
            }
        }
    }
}

// --- HELPERS ---
fun saveLog(context: Context, list: List<FoodItem>) {
    val sharedPref = context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE)
    val editor = sharedPref.edit()
    val gson = Gson()
    val json = gson.toJson(list)
    editor.putString("daily_log", json)
    editor.apply()
}

fun loadLog(context: Context): List<FoodItem> {
    val sharedPref = context.getSharedPreferences("CalorieApp", Context.MODE_PRIVATE)
    val json = sharedPref.getString("daily_log", null)
    return if (json != null) {
        val type = object : TypeToken<List<FoodItem>>() {}.type
        Gson().fromJson(json, type)
    } else {
        emptyList()
    }
}

@Composable
fun CalorieTrackerScreen() {
    val context = LocalContext.current
    val generativeModel = GenerativeModel(
        modelName = "gemini-flash-latest",
        apiKey = BuildConfig.API_KEY
    )

    var userInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var dailyFoodLog by remember { mutableStateOf(loadLog(context)) }
    val scope = rememberCoroutineScope()

    fun String.extractFloat(): Float {
        val regex = Regex("[0-9]*\\.?[0-9]+")
        return regex.find(this)?.value?.toFloatOrNull() ?: 0f
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    val header = "Meal,Food Item,Calories,Protein,Carbs,Fat\n"
                    val sb = StringBuilder(header)
                    dailyFoodLog.forEach { item ->
                        sb.append("${item.mealType},${item.name},${item.calories},${item.protein},${item.carbs},${item.fat}\n")
                    }
                    val allMeals = listOf("Breakfast", "Lunch", "Dinner", "Snack")
                    allMeals.forEach { meal ->
                        if (!dailyFoodLog.any { it.mealType == meal }) {
                            sb.append("$meal,Not Entered,0,0,0,0\n")
                        }
                    }
                    stream.write(sb.toString().toByteArray())
                }
                Toast.makeText(context, "Saved Successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun addFood(mealCategory: String) {
        if (userInput.isBlank()) return
        isLoading = true

        scope.launch {
            try {
                val prompt = """
                    You are a nutritionist. I ate: "$userInput".
                    Identify food items. Estimate Calories, Protein(g), Carbs(g), and Fat(g).
                    Output strictly in this CSV format per line: Name,Calories,Protein,Carbs,Fat
                    Example: 
                    2 Porotta,450,10g,60g,15g
                    Fried Egg,135,12.5g,1.2g,10g
                    No headers. No markdown.
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val responseText = response.text ?: ""

                val newItems = responseText.lines().mapNotNull { line ->
                    val parts = line.split(",")
                    if (parts.size >= 5) {
                        FoodItem(
                            id = System.nanoTime(),
                            name = parts[0].trim(),
                            calories = parts[1].trim(),
                            protein = parts[2].trim(),
                            carbs = parts[3].trim(),
                            fat = parts[4].trim(),
                            mealType = mealCategory
                        )
                    } else null
                }

                val updatedList = dailyFoodLog + newItems
                dailyFoodLog = updatedList
                saveLog(context, updatedList)

                userInput = ""
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    fun deleteItem(itemToDelete: FoodItem) {
        val updatedList = dailyFoodLog.filter { it.id != itemToDelete.id }
        dailyFoodLog = updatedList
        saveLog(context, updatedList)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding() // <--- THIS IS THE FIX (Pushes content down)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // --- TOTALS ---
        val totalCals = dailyFoodLog.sumOf { it.calories.extractFloat().toInt() }
        val totalProt = dailyFoodLog.sumOf { it.protein.extractFloat().toDouble() }.toFloat()
        val totalCarb = dailyFoodLog.sumOf { it.carbs.extractFloat().toDouble() }.toFloat()
        val totalFat  = dailyFoodLog.sumOf { it.fat.extractFloat().toDouble() }.toFloat()

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252525)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("TOTAL TODAY", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 2.sp)

                Text(
                    text = "$totalCals",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )
                Text("kcal", fontSize = 12.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MacroStat("Protein", String.format("%.1fg", totalProt), Color(0xFFBB86FC))
                    MacroStat("Carbs", String.format("%.1fg", totalCarb), Color(0xFF4CAF50))
                    MacroStat("Fat", String.format("%.1fg", totalFat), Color(0xFFFF9800))
                }
            }
        }

        // --- INPUT ---
        OutlinedTextField(
            value = userInput,
            onValueChange = { userInput = it },
            label = { Text("What did you eat?") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFF00E5FF),
                focusedBorderColor = Color(0xFF00E5FF),
                unfocusedBorderColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- BUTTONS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val meals = listOf("Breakfast", "Lunch", "Dinner", "Snack")
            meals.forEach { meal ->
                Button(
                    onClick = { addFood(meal) },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6200EE),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(meal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                color = Color(0xFF00E5FF)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- LIST ---
        if (dailyFoodLog.isNotEmpty()) {
            dailyFoodLog.forEach { item ->
                FoodRow(item, onDelete = { deleteItem(item) })
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { saveLauncher.launch("daily_log.csv") },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text("SAVE TO EXCEL", color = Color.Black, fontWeight = FontWeight.Bold)
            }
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.mealType.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFBB86FC),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Text(
                    text = "P: ${item.protein}  C: ${item.carbs}  F: ${item.fat}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.calories,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFFF5252)
                    )
                }
            }
        }
    }
}