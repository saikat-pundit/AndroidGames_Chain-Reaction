package com.example.chainreact

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LudoActivity : AppCompatActivity() {
    private lateinit var engine: LudoEngine
    private lateinit var tvStatus: TextView
    private lateinit var tvRoomCode: TextView
    private lateinit var btnDice: Button
    
    private var mode = "AI"
    private var roomCode = ""
    private var myPlayerId = 1 
    private var networkPollingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ludo)

        tvStatus = findViewById(R.id.tvStatus)
        tvRoomCode = findViewById(R.id.tvRoomCode)
        btnDice = findViewById(R.id.btnDice)

        mode = intent.getStringExtra("MODE") ?: "AI"
        roomCode = intent.getStringExtra("ROOM_CODE") ?: ""
        
        engine = LudoEngine()

        if (mode == "AI" || mode == "HOST") myPlayerId = 1 else myPlayerId = 2
        tvRoomCode.text = if (mode == "AI") "vs Computer" else "Room: $roomCode"

        btnDice.setOnClickListener {
            if (engine.currentPlayer == myPlayerId && !engine.diceRolled && !engine.isGameOver) {
                val roll = engine.rollDice()
                btnDice.text = roll.toString()
                pushNetwork()
                updateUI()
            }
        }

        updateUI()
        if (mode != "AI") startNetworkPolling()
    }

    // Mock token click for now - In a full graphics implementation you'd use a Custom View (LudoBoardView)
    // To keep it simple to click, we will add 4 buttons representing your tokens dynamically.
    private fun attemptMove(tokenIndex: Int) {
        if (engine.currentPlayer == myPlayerId && engine.diceRolled) {
            if(engine.playToken(tokenIndex)) {
                btnDice.text = "ROLL"
                pushNetwork()
                updateUI()
                
                // Trigger AI if it's AI turn
                if (mode == "AI" && engine.currentPlayer == 2 && !engine.isGameOver) {
                    lifecycleScope.launch {
                        delay(1000)
                        val aiRoll = engine.rollDice()
                        btnDice.text = aiRoll.toString()
                        delay(1000)
                        val move = engine.getAIMove()
                        if (move != null) engine.playToken(move)
                        btnDice.text = "ROLL"
                        updateUI()
                    }
                }
            } else {
                Toast.makeText(this, "Invalid Move", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        val isMyTurn = (engine.currentPlayer == myPlayerId)
        val myColor = if (myPlayerId == 1) Color.parseColor("#FF5252") else Color.parseColor("#448AFF")
        val oppColor = if (myPlayerId == 1) Color.parseColor("#448AFF") else Color.parseColor("#FF5252")

        if (engine.isGameOver) {
            val didIWin = (engine.p1Tokens.all { it == 56 } && myPlayerId == 1) || (engine.p2Tokens.all { it == 56 } && myPlayerId == 2)
            tvStatus.text = if (didIWin) "YOU WIN! 🎉" else if (mode == "AI") "COMPUTER WINS!" else "FRIEND WINS!"
            tvStatus.setTextColor(Color.YELLOW)
            lifecycleScope.launch { delay(3000); finish() }
        } else {
            tvStatus.text = if (isMyTurn) "Your Turn" else if (mode == "AI") "Computer's Turn" else "Friend's Turn"
            tvStatus.setTextColor(if (isMyTurn) myColor else oppColor)
            btnDice.isEnabled = isMyTurn && !engine.diceRolled
            if(!engine.diceRolled) btnDice.text = "ROLL" else btnDice.text = engine.currentDice.toString()
        }
        
        renderTokensAsButtons() // Fallback UI to play the game
    }

    private fun renderTokensAsButtons() {
        val container = findViewById<FrameLayout>(R.id.ludoBoardContainer)
        container.removeAllViews()
        
        val layout = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        
        val title = TextView(this).apply { text = "Your Tokens (Click to Move):"; setTextColor(Color.WHITE); textSize = 18f; setPadding(0,0,0,20) }
        layout.addView(title)

        val myTokens = if(myPlayerId == 1) engine.p1Tokens else engine.p2Tokens
        for (i in 0..3) {
            val btn = Button(this).apply {
                val stateStr = when(myTokens[i]) { -1 -> "In Base"; 56 -> "Finished"; else -> "At Step ${myTokens[i]}" }
                text = "Token ${i+1}: $stateStr"
                setOnClickListener { attemptMove(i) }
            }
            layout.addView(btn)
        }
        container.addView(layout)
    }

    private fun pushNetwork() {
        if (mode == "AI") return
        lifecycleScope.launch {
            val state = NetworkManager.pollGameState(roomCode) ?: GameState()
            state.ludoState = engine.serialize()
            NetworkManager.updateGameState(roomCode, state)
        }
    }

    private fun startNetworkPolling() {
        networkPollingJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                if (engine.currentPlayer != myPlayerId && !engine.isGameOver) {
                    val state = NetworkManager.pollGameState(roomCode)
                    if (state != null && state.ludoState.isNotEmpty()) {
                        engine.deserialize(state.ludoState)
                        updateUI()
                    }
                }
            }
        }
    }
    override fun onDestroy() { super.onDestroy(); networkPollingJob?.cancel() }
}
