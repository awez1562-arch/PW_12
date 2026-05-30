package com.example.multiwindowapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "wolf_game_prefs";
    private static final String KEY_HIGH_SCORE = "high_score";

    private TextView tvScore, tvGameStatus;
    private TextView[] tvLives = new TextView[3];

    // Позиции волка
    private TextView tvWolfTL, tvWolfTR, tvWolfBL, tvWolfBR;
    // Позиции яиц
    private TextView tvEggTL, tvEggTR, tvEggBL, tvEggBR;
    // Кнопки
    private Button btnUpLeft, btnUpRight, btnDownLeft, btnDownRight, btnExit;

    // Состояние игры
    private int score = 0;
    private int lives = 3;
    private int wolfTrack = 2; // 0=TL, 1=TR, 2=BL, 3=BR (старт снизу слева)
    private boolean isGameRunning = false;
    private boolean isPaused = false;

    private final Handler gameHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    // Шаги яиц по дорожкам: -1 = нет, 0 = у курицы, 1 = в центре, 2 = у волка
    private int[] eggStep = new int[4];

    // Тайминги (мс)
    private long spawnInterval = 1500;
    private long moveInterval = 400;
    private long lastSpawnTime = 0;
    private long lastMoveTime = 0;
    private static final int TICK_MS = 50;

    private boolean soundEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        initViews();
        loadSettings();
        initListeners();
        resetGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lives > 0 && !isGameRunning) {
            startGame();
        } else if (isPaused) {
            resumeGame();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseGame();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopGameLoop();
    }

    // === ИНИЦИАЛИЗАЦИЯ ===
    private void initViews() {
        tvScore = findViewById(R.id.score);
        tvGameStatus = findViewById(R.id.gameStatus);
        tvLives[0] = findViewById(R.id.life1);
        tvLives[1] = findViewById(R.id.life2);
        tvLives[2] = findViewById(R.id.life3);

        tvWolfTL = findViewById(R.id.wolfTopLeft);
        tvWolfTR = findViewById(R.id.wolfTopRight);
        tvWolfBL = findViewById(R.id.wolfBottomLeft);
        tvWolfBR = findViewById(R.id.wolfBottomRight);

        tvEggTL = findViewById(R.id.eggTopLeft);
        tvEggTR = findViewById(R.id.eggTopRight);
        tvEggBL = findViewById(R.id.eggBottomLeft);
        tvEggBR = findViewById(R.id.eggBottomRight);

        btnUpLeft = findViewById(R.id.btnUpLeft);
        btnUpRight = findViewById(R.id.btnUpRight);
        btnDownLeft = findViewById(R.id.btnDownLeft);
        btnDownRight = findViewById(R.id.btnDownRight);
        btnExit = findViewById(R.id.btnExitGame);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        soundEnabled = prefs.getBoolean("sound_enabled", true);
        String difficulty = prefs.getString("difficulty", "Средняя");

        // Применяем сложность к начальным скоростям
        switch (difficulty) {
            case "Лёгкая":
                spawnInterval = 2000; moveInterval = 500;
                break;
            case "Сложная":
                spawnInterval = 1000; moveInterval = 300;
                break;
            default: // Средняя
                spawnInterval = 1500; moveInterval = 400;
                break;
        }
    }

    private void initListeners() {
        View.OnClickListener moveListener = v -> {
            if (!isGameRunning || isPaused) return;
            int newTrack = -1;
            int id = v.getId();
            if (id == R.id.btnUpLeft) newTrack = 0;
            else if (id == R.id.btnUpRight) newTrack = 1;
            else if (id == R.id.btnDownLeft) newTrack = 2;
            else if (id == R.id.btnDownRight) newTrack = 3;

            if (newTrack != -1 && newTrack != wolfTrack) {
                wolfTrack = newTrack;
                renderWolf();
                checkImmediateCatch();
            }
        };

        btnUpLeft.setOnClickListener(moveListener);
        btnUpRight.setOnClickListener(moveListener);
        btnDownLeft.setOnClickListener(moveListener);
        btnDownRight.setOnClickListener(moveListener);

        if (btnExit != null) {
            btnExit.setOnClickListener(v -> finish());
        }
    }

    // === УПРАВЛЕНИЕ ИГРОЙ ===
    private void resetGame() {
        score = 0;
        lives = 3;
        wolfTrack = 2;
        isGameRunning = false;
        isPaused = false;

        for (int i = 0; i < 4; i++) eggStep[i] = -1;

        updateUI();
        renderWolf();
        renderEggs();
    }

    private void startGame() {
        if (isGameRunning) return;
        isGameRunning = true;
        isPaused = false;
        lastSpawnTime = SystemClock.uptimeMillis();
        lastMoveTime = SystemClock.uptimeMillis();
        tvGameStatus.setText("🎮 Игра началась!");
        startGameLoop();
    }

    private void pauseGame() {
        if (!isGameRunning) return;
        isPaused = true;
        tvGameStatus.setText("⏸ Пауза");
        stopGameLoop();
    }

    private void resumeGame() {
        if (!isGameRunning || !isPaused) return;
        isPaused = false;
        long now = SystemClock.uptimeMillis();
        lastSpawnTime = now;
        lastMoveTime = now;
        tvGameStatus.setText("▶ Продолжаем...");
        startGameLoop();
    }

    private void gameOver() {
        isGameRunning = false;
        stopGameLoop();
        saveHighScore();
        tvGameStatus.setText("💔 Игра окончена! Счёт: " + score);
        Toast.makeText(this, "Итог: " + score + " яиц", Toast.LENGTH_LONG).show();

        // Авто-возврат в меню через 2 сек
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 2500);
    }

    // === ИГРОВОЙ ЦИКЛ (Единый тик) ===
    private void startGameLoop() {
        stopGameLoop();
        gameHandler.post(tickRunnable);
    }

    private void stopGameLoop() {
        gameHandler.removeCallbacks(tickRunnable);
    }

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isGameRunning || isPaused) return;

            long now = SystemClock.uptimeMillis();

            if (now - lastSpawnTime >= spawnInterval) {
                trySpawnEgg();
                lastSpawnTime = now;
            }

            if (now - lastMoveTime >= moveInterval) {
                moveEggs();
                lastMoveTime = now;
            }

            gameHandler.postDelayed(this, TICK_MS);
        }
    };

    // === ЛОГИКА ===
    private void trySpawnEgg() {
        List<Integer> freeTracks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (eggStep[i] == -1) freeTracks.add(i);
        }
        if (freeTracks.isEmpty()) return;

        eggStep[freeTracks.get(random.nextInt(freeTracks.size()))] = 0;
    }

    private void moveEggs() {
        for (int track = 0; track < 4; track++) {
            if (eggStep[track] == -1) continue;

            eggStep[track]++;
            if (eggStep[track] == 3) {
                if (wolfTrack == track) onEggCaught(track);
                else onEggMissed(track);
                eggStep[track] = -1;
            }
        }
        renderEggs();
    }

    private void checkImmediateCatch() {
        if (eggStep[wolfTrack] == 2) {
            onEggCaught(wolfTrack);
            eggStep[wolfTrack] = -1;
            renderEggs();
        }
    }

    private void onEggCaught(int track) {
        score++;
        updateUI();
        showStatus("🎉 +1!");

        if (score % 10 == 0) {
            spawnInterval = Math.max(600, spawnInterval - 150);
            moveInterval = Math.max(200, moveInterval - 50);
            showStatus("🔥 Скорость ↑");
        }
    }

    private void onEggMissed(int track) {
        lives--;
        updateUI();
        showStatus("😥 -1 жизнь");
        if (lives <= 0) gameOver();
    }

    // === ОТРИСОВКА ===
    private void renderWolf() {
        tvWolfTL.setText(""); tvWolfTR.setText("");
        tvWolfBL.setText(""); tvWolfBR.setText("");
        switch (wolfTrack) {
            case 0: tvWolfTL.setText("🐺"); break;
            case 1: tvWolfTR.setText("🐺"); break;
            case 2: tvWolfBL.setText("🐺"); break;
            case 3: tvWolfBR.setText("🐺"); break;
        }
    }

    private void renderEggs() {
        tvEggTL.setText(eggStep[0] >= 0 ? "🥚" : "");
        tvEggTR.setText(eggStep[1] >= 0 ? "🥚" : "");
        tvEggBL.setText(eggStep[2] >= 0 ? "🥚" : "");
        tvEggBR.setText(eggStep[3] >= 0 ? "🥚" : "");
    }

    private void updateUI() {
        tvScore.setText(String.valueOf(score));
        for (int i = 0; i < 3; i++) {
            float alpha = (i < lives) ? 0.5f : 1.0f;
            tvLives[i].animate().alpha(alpha).setDuration(200).start();
        }
    }

    private void showStatus(String text) {
        tvGameStatus.setText(text);
        tvGameStatus.postDelayed(() -> {
            if (isGameRunning && !isPaused) tvGameStatus.setText("");
        }, 700);
    }

    private void saveHighScore() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int currentHigh = prefs.getInt(KEY_HIGH_SCORE, 0);
        if (score > currentHigh) {
            prefs.edit().putInt(KEY_HIGH_SCORE, score).apply();
            Toast.makeText(this, "🏆 Новый рекорд: " + score, Toast.LENGTH_SHORT).show();
        }
    }
}