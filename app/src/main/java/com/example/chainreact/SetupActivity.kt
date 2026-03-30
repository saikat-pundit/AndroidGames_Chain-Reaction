package com.example.chainreact
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class SetupActivity : AppCompatActivity() {
    private var gameType = "CHAIN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        gameType = intent.getStringExtra("GAME_TYPE") ?: "CHAIN"
        findViewById<TextView>(R.id.tvGameTitle).text = if(gameType == "CHAIN") "CHAIN REACTION" else "LUDO"

        findViewById<Button>(R.id.btnVsAi).setOnClickListener { startGame("AI", "") }
        findViewById<Button>(R.id.btnHost).setOnClickListener { 
            startGame("HOST", String.format("%04d", Random.nextInt(10000))) 
        }
        findViewById<Button>(R.id.btnJoin).setOnClickListener {
            val code = findViewById<EditText>(R.id.etRoomCode).text.toString()
            if (code.length == 4) startGame("JOIN", code) else Toast.makeText(this, "Enter 4-digit code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startGame(mode: String, roomCode: String) {
        val targetClass = if(gameType == "CHAIN") GameActivity::class.java else LudoActivity::class.java
        val intent = Intent(this, targetClass).apply {
            putExtra("MODE", mode)
            putExtra("ROOM_CODE", roomCode)
        }
        startActivity(intent)
    }
}
