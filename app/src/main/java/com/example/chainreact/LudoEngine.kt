package com.example.chainreact

class LudoEngine {
    var currentPlayer = 1 // 1=Red, 2=Blue
    var isGameOver = false
    var currentDice = 0
    var diceRolled = false

    // -1 = Base, 0-50 = Main Path, 51-55 = Home Stretch, 56 = Finished
    var p1Tokens = intArrayOf(-1, -1, -1, -1)
    var p2Tokens = intArrayOf(-1, -1, -1, -1)

    fun rollDice(): Int {
        if (diceRolled || isGameOver) return currentDice
        currentDice = (1..6).random()
        diceRolled = true

        if (!hasValidMoves()) {
            passTurn()
        }
        return currentDice
    }

    private fun hasValidMoves(): Boolean {
        val tokens = if (currentPlayer == 1) p1Tokens else p2Tokens
        for (i in 0..3) {
            if (tokens[i] == -1 && currentDice == 6) return true
            if (tokens[i] in 0..55 && tokens[i] + currentDice <= 56) return true
        }
        return false
    }

    fun playToken(tokenIndex: Int): Boolean {
        if (!diceRolled || isGameOver) return false
        val tokens = if (currentPlayer == 1) p1Tokens else p2Tokens
        val enemyTokens = if (currentPlayer == 1) p2Tokens else p1Tokens
        
        val pos = tokens[tokenIndex]

        if (pos == -1 && currentDice == 6) {
            tokens[tokenIndex] = 0 // Enter board
        } else if (pos in 0..55) {
            if (pos + currentDice > 56) return false // Overshot home
            tokens[tokenIndex] += currentDice
        } else {
            return false // Invalid move
        }

        // Check Kill (only on main path 0-50, and not on safe squares: 0, 8, 13, 21, 26, 34, 39, 47)
        val safeZones = listOf(0, 8, 13, 21, 26, 34, 39, 47)
        if (tokens[tokenIndex] in 0..50) {
            val globalMyPos = getGlobalPath(currentPlayer, tokens[tokenIndex])
            if (!safeZones.contains(globalMyPos)) {
                for (i in 0..3) {
                    if (enemyTokens[i] in 0..50) {
                        val globalEnemyPos = getGlobalPath(if(currentPlayer==1) 2 else 1, enemyTokens[i])
                        if (globalMyPos == globalEnemyPos) {
                            enemyTokens[i] = -1 // Kill!
                        }
                    }
                }
            }
        }

        checkWin()
        if (currentDice != 6) passTurn() else diceRolled = false // 6 gives another turn
        return true
    }

    private fun getGlobalPath(player: Int, localPos: Int): Int {
        if (localPos > 50) return -1 // In home stretch
        return if (player == 1) localPos else (localPos + 26) % 52
    }

    private fun passTurn() {
        currentPlayer = if (currentPlayer == 1) 2 else 1
        diceRolled = false
        currentDice = 0
    }

    private fun checkWin() {
        if (p1Tokens.all { it == 56 } || p2Tokens.all { it == 56 }) {
            isGameOver = true
        }
    }

    fun getAIMove(): Int? {
        if (!diceRolled) return null
        val tokens = p2Tokens // AI is Blue
        
        // Priority 1: Move out of base if 6
        if (currentDice == 6) {
            for (i in 0..3) if (tokens[i] == -1) return i
        }
        
        // Priority 2: Move a piece into Home (56)
        for (i in 0..3) {
            if (tokens[i] != -1 && tokens[i] + currentDice == 56) return i
        }

        // Priority 3: Any valid move
        for (i in 0..3) {
            if (tokens[i] in 0..55 && tokens[i] + currentDice <= 56) return i
        }
        return null
    }

    // Network Serialization
    fun serialize(): String {
        return "$currentDice,$currentPlayer,${if(diceRolled) 1 else 0}|${p1Tokens.joinToString(",")}|${p2Tokens.joinToString(",")}"
    }

    fun deserialize(data: String) {
        if (data.isEmpty()) return
        val parts = data.split("|")
        val meta = parts[0].split(",")
        currentDice = meta[0].toInt()
        currentPlayer = meta[1].toInt()
        diceRolled = meta[2] == "1"
        
        val p1 = parts[1].split(",")
        val p2 = parts[2].split(",")
        for(i in 0..3) {
            p1Tokens[i] = p1[i].toInt()
            p2Tokens[i] = p2[i].toInt()
        }
    }
}
