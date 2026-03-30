package com.example.chainreact

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// TO MAKE MULTIPLAYER WORK OVER THE INTERNET:
// 1. Create a free Firebase project.
// 2. Go to Realtime Database and set rules to public (read/write: true).
// 3. Replace the DB_URL below with your database URL.
// This REST approach avoids needing the google-services.json CI/CD headache!
object NetworkManager {
    private const val DB_URL = "https://chainreactiondb-default-rtdb.firebaseio.com"
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun updateGameState(roomCode: String, state: GameState) {
        withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(state)
                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$DB_URL/rooms/$roomCode.json")
                    .put(body)
                    .build()
                client.newCall(request).execute().close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun pollGameState(roomCode: String): GameState? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$DB_URL/rooms/$roomCode.json")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    if (json != null && json != "null") {
                        return@withContext gson.fromJson(json, GameState::class.java)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext null
        }
    }
}

// Data class to serialize the grid for network transmission
data class GameState(
    var currentPlayer: Int = 1,
    var isGameOver: Boolean = false,
    var serializedGrid: String = "" // "owner,mass|owner,mass..."
) {
    companion object {
        fun fromEngine(engine: GameEngine): GameState {
            val sb = java.lang.StringBuilder()
            for (i in 0 until engine.cols) {
                for (j in 0 until engine.rows) {
                    sb.append("${engine.grid[i][j].owner},${engine.grid[i][j].mass}|")
                }
            }
            return GameState(engine.currentPlayer, engine.isGameOver, sb.toString())
        }

        fun applyToEngine(state: GameState, engine: GameEngine) {
            engine.currentPlayer = state.currentPlayer
            engine.isGameOver = state.isGameOver
            val cells = state.serializedGrid.split("|")
            var idx = 0
            for (i in 0 until engine.cols) {
                for (j in 0 until engine.rows) {
                    if (idx < cells.size && cells[idx].isNotEmpty()) {
                        val parts = cells[idx].split(",")
                        engine.grid[i][j].owner = parts[0].toInt()
                        engine.grid[i][j].mass = parts[1].toInt()
                    }
                    idx++
                }
            }
        }
    }
}
