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
        if (engine.isGameOver) {
            tvStatus.text = if (engine.currentPlayer == 1) "BLUE WINS!" else "RED WINS!"
            tvStatus.setTextColor(Color.YELLOW)
        } else {
            if (engine.currentPlayer == 1) {
                tvStatus.text = "Red's Turn"
                tvStatus.setTextColor(Color.parseColor("#FF5252"))
            } else {
                tvStatus.text = "Blue's Turn"
                tvStatus.setTextColor(Color.parseColor("#448AFF"))
            }
        }
    }

    private fun pushStateToNetwork() {
        lifecycleScope.launch {
            NetworkManager.updateGameState(roomCode, GameState.fromEngine(engine))
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
