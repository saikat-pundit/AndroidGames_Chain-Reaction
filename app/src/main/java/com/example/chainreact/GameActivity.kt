package com.example.chainreact

import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GameActivity : AppCompatActivity() {

    private lateinit var engine: GameEngine
    private lateinit var boardView: BoardView
    private lateinit var tvStatus: TextView
    private lateinit var tvRoomCode: TextView

    private var mode = "AI"
    private var roomCode = ""
    private var myPlayerId = 1 // Host/AI is P1(Red), Joiner is P2(Blue)
    private var networkPollingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        tvStatus = findViewById(R.id.tvStatus)
        tvRoomCode = findViewById(R.id.tvRoomCode)

        mode = intent.getStringExtra("MODE") ?: "AI"
        roomCode = intent.getStringExtra("ROOM_CODE") ?: ""

        engine = GameEngine(6, 9)
        
        boardView = BoardView(this, engine) { x, y ->
            handleCellClick(x, y)
        }
        
        findViewById<FrameLayout>(R.id.boardContainer).addView(boardView)

        setupGameMode()
    }

    private fun setupGameMode() {
        if (mode == "AI") {
            tvRoomCode.text = "vs Computer"
            myPlayerId = 1
        } else if (mode == "HOST") {
            tvRoomCode.text = "Room: $roomCode"
            myPlayerId = 1
            pushStateToNetwork()
            startNetworkPolling()
        } else if (mode == "JOIN") {
            tvRoomCode.text = "Room: $roomCode"
            myPlayerId = 2
            startNetworkPolling()
        }
        updateUI()
    }

    private fun handleCellClick(x: Int, y: Int) {
        if (engine.isGameOver) return
        
        // Multiplayer lock
        if (mode != "AI" && engine.currentPlayer != myPlayerId) return

        if (engine.play(x, y)) {
            updateUI()
            
            if (mode == "AI" && !engine.isGameOver && engine.currentPlayer == 2) {
                // Trigger AI Move
                lifecycleScope.launch {
                    delay(500) // Thinking pause
                    val move = engine.getAIMove()
                    if (move != null) {
                        engine.play(move.first, move.second)
                        updateUI()
                    }
                }
            } else if (mode == "HOST" || mode == "JOIN") {
                pushStateToNetwork()
            }
        }
    }

    private fun updateUI() {
        boardView.invalidate()
        val myColor = if (myPlayerId == 1) Color.parseColor("#FF5252") else Color.parseColor("#448AFF")
        val opponentColor = if (myPlayerId == 1) Color.parseColor("#448AFF") else Color.parseColor("#FF5252")

        if (engine.isGameOver) {
            // Check if the current user made the winning move
            val didIWin = (engine.currentPlayer == myPlayerId)

            if (didIWin) {
                tvStatus.text = "YOU WIN! 🎉"
                tvStatus.setTextColor(Color.YELLOW)
            } else {
                tvStatus.text = if (mode == "AI") "COMPUTER WINS!" else "FRIEND WINS!"
                tvStatus.setTextColor(Color.LTGRAY)
            }
            lifecycleScope.launch {
                delay(2000)
                finish() 
            }
        } else {
            val isMyTurn = (engine.currentPlayer == myPlayerId)

            if (isMyTurn) {
                tvStatus.text = "Your Turn"
                tvStatus.setTextColor(myColor)
            } else {
                tvStatus.text = if (mode == "AI") "Computer's Turn" else "Friend's Turn"
                tvStatus.setTextColor(opponentColor)
            }
        }
    }
    private fun pushStateToNetwork() {
        lifecycleScope.launch {
            NetworkManager.updateGameState(roomCode, GameState.fromChainEngine(engine))
        }
    }

    private fun startNetworkPolling() {
        networkPollingJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000) // Poll every second
                if (engine.currentPlayer != myPlayerId && !engine.isGameOver) {
                    val state = NetworkManager.pollGameState(roomCode)
                    if (state != null) {
                        GameState.applyToEngine(state, engine)
                        updateUI()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkPollingJob?.cancel()
    }
}
