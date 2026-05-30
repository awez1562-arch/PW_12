package com.example.multiwindowapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Random;

public class TestGameActivity extends AppCompatActivity {

    // === UI ЭЛЕМЕНТЫ ===
    private TextView scoreText, gameStatus;
    private TextView[] livesViews = new TextView[3];

    // Позиции волка
    private TextView wolfTopLeft, wolfTopRight, wolfBottomLeft, wolfBottomRight;
    // Позиции для яиц
    private TextView eggTopLeft, eggTopRight, eggBottomLeft, eggBottomRight;

    // Кнопки управления
    private Button btnUpLeft, btnUpRight, btnDownLeft, btnDownRight;

    // === ИГРОВЫЕ ПЕРЕМЕННЫЕ ===
    private int score = 0;
    private int lives = 3;
    private int wolfTrack = 2; // 0=TL, 1=TR, 2=BL, 3=BR. Старт внизу слева

    private boolean isGameRunning = false;
    private boolean isPaused = false;

    private final Handler gameHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    // Состояние дорожек: -1 = нет яйца, 0 = у курицы, 1 = в центре, 2 = у волка
    private int[] eggStep = new int[4];

    // Тайминги (мс)
    private long spawnInterval = 1500;
    private long moveInterval = 400;
    private long lastSpawnTime = 0;
    private long lastMoveTime = 0;

    private static final int TICK_MS = 50; // Частота обновления цикла

    // === ЖИЗНЕННЫЙ ЦИКЛ ===
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        initViews();
        initListeners();
        resetGame();
    }

    @Override protected void onResume() {
        super.onResume();
        if (lives > 0 && !isGameRunning) startGame();
    }

    @Override protected void onPause() {
        super.onPause();
        pauseGame();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopGameLoop();
    }

    // === ИНИЦИАЛИЗАЦИЯ ===
    private void initViews() {
        gameStatus = findViewById(R.id.gameStatus);
        scoreText = findViewById(R.id.score);
        livesViews[0] = findViewById(R.id.life1);
        livesViews[1] = findViewById(R.id.life2);
        livesViews[2] = findViewById(R.id.life3);

        wolfTopLeft = findViewById(R.id.wolfTopLeft);
        wolfTopRight = findViewById(R.id.wolfTopRight);
        wolfBottomLeft = findViewById(R.id.wolfBottomLeft);
        wolfBottomRight = findViewById(R.id.wolfBottomRight);

        eggTopLeft = findViewById(R.id.eggTopLeft);
        eggTopRight = findViewById(R.id.eggTopRight);
        eggBottomLeft = findViewById(R.id.eggBottomLeft);
        eggBottomRight = findViewById(R.id.eggBottomRight);

        btnUpLeft = findViewById(R.id.btnUpLeft);
        btnUpRight = findViewById(R.id.btnUpRight);
        btnDownLeft = findViewById(R.id.btnDownLeft);
        btnDownRight = findViewById(R.id.btnDownRight);
    }

    private void initListeners() {
        View.OnClickListener moveListener = v -> {
            if (!isGameRunning || isPaused) return;
            int newTrack = -1;
            if (v.getId() == R.id.btnUpLeft) newTrack = 0;
            else if (v.getId() == R.id.btnUpRight) newTrack = 1;
            else if (v.getId() == R.id.btnDownLeft) newTrack = 2;
            else if (v.getId() == R.id.btnDownRight) newTrack = 3;

            if (newTrack != -1 && newTrack != wolfTrack) {
                wolfTrack = newTrack;
                renderWolf();
                checkImmediateCatch(); // Проверка поимки в моменте
            }
        };

        btnUpLeft.setOnClickListener(moveListener);
        btnUpRight.setOnClickListener(moveListener);
        btnDownLeft.setOnClickListener(moveListener);
        btnDownRight.setOnClickListener(moveListener);
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
        gameStatus.setText("🎮 Игра началась!");
        gameStatus.setVisibility(View.VISIBLE);
        startGameLoop();
    }

    private void pauseGame() {
        if (!isGameRunning) return;
        isPaused = true;
        gameStatus.setText("⏸ Пауза");
        stopGameLoop();
    }

    private void resumeGame() {
        if (!isGameRunning || !isPaused) return;
        isPaused = false;
        long now = SystemClock.uptimeMillis();
        lastSpawnTime = now;
        lastMoveTime = now;
        gameStatus.setText("▶ Продолжаем...");
        startGameLoop();
    }

    private void togglePause() {
        if (!isGameRunning) return;
        if (isPaused) resumeGame();
        else pauseGame();
    }

    private void gameOver() {
        isGameRunning = false;
        stopGameLoop();
        gameStatus.setText("💔 Игра окончена! Счёт: " + score);
        Toast.makeText(this, "Итог: " + score + " яиц", Toast.LENGTH_LONG).show();

        // Авто-рестарт через 3 сек
        new Handler(Looper.getMainLooper()).postDelayed(this::resetGame, 3000);
    }

    // === ИГРОВОЙ ЦИКЛ (Заменяет рекурсивные Handler) ===
    private void startGameLoop() {
        stopGameLoop(); // Гарантируем, что старый цикл уничтожен
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

            // 1. Спавн яиц
            if (now - lastSpawnTime >= spawnInterval) {
                trySpawnEgg();
                lastSpawnTime = now;
            }

            // 2. Движение яиц
            if (now - lastMoveTime >= moveInterval) {
                moveEggs();
                lastMoveTime = now;
            }

            // 3. Следующий тик
            gameHandler.postDelayed(this, TICK_MS);
        }
    };

    // === ЛОГИКА ЯИЦ ===
    private void trySpawnEgg() {
        // Ищем свободную дорожку
        int freeTrack = -1;
        for (int i = 0; i < 4; i++) {
            if (eggStep[i] == -1) {
                freeTrack = i;
                break;
            }
        }
        if (freeTrack == -1) return; // Все заняты

        // Случайная свободная дорожка
        java.util.List<Integer> free = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) if (eggStep[i] == -1) free.add(i);
        eggStep[free.get(random.nextInt(free.size()))] = 0; // Шаг 0: у курицы
    }

    private void moveEggs() {
        for (int track = 0; track < 4; track++) {
            int step = eggStep[track];
            if (step == -1) continue; // Нет яйца

            step++; // Двигаем вперёд

            if (step == 3) {
                // Яйцо достигло конца
                if (wolfTrack == track) {
                    onEggCaught(track);
                } else {
                    onEggMissed(track);
                }
                eggStep[track] = -1; // Дорожка свободна
            } else {
                eggStep[track] = step;
            }
        }
        renderEggs();
    }

    private void checkImmediateCatch() {
        // Если волк переместился на дорожку, где яйцо уже на шаге 2
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

        // Усложнение каждые 10 очков
        if (score % 10 == 0) {
            spawnInterval = Math.max(600, spawnInterval - 150);
            moveInterval = Math.max(200, moveInterval - 50);
            Toast.makeText(this, "🔥 Скорость ↑", Toast.LENGTH_SHORT).show();
        }
    }

    private void onEggMissed(int track) {
        lives--;
        updateUI();
        showStatus("😥 -1 жизнь");
        if (lives <= 0) gameOver();
    }

    // === ОТРИСОВКА И UI ===
    private void renderWolf() {
        wolfTopLeft.setText(""); wolfTopRight.setText("");
        wolfBottomLeft.setText(""); wolfBottomRight.setText("");

        switch (wolfTrack) {
            case 0: wolfTopLeft.setText("🐺"); break;
            case 1: wolfTopRight.setText("🐺"); break;
            case 2: wolfBottomLeft.setText("🐺"); break;
            case 3: wolfBottomRight.setText("🐺"); break;
        }
    }

    private void renderEggs() {
        eggTopLeft.setText(""); eggTopRight.setText("");
        eggBottomLeft.setText(""); eggBottomRight.setText("");

        // Отображаем яйца на соответствующих TextView
        // Логика упрощена: показываем 🥚 если шаг > 0, иначе пусто
        // Для классического вида можно добавить анимацию смещения, но эмодзи достаточно
        if (eggStep[0] >= 0) eggTopLeft.setText("🥚");
        if (eggStep[1] >= 0) eggTopRight.setText("🥚");
        if (eggStep[2] >= 0) eggBottomLeft.setText("🥚");
        if (eggStep[3] >= 0) eggBottomRight.setText("🥚");
    }

    private void updateUI() {
        scoreText.setText(String.valueOf(score));

        // Текст цыплят статичен (задан в XML), меняем только прозрачность
        for (int i = 0; i < 3; i++) {
            // i < lives  → жизнь активна (50% альфа)
            // i >= lives → жизнь потеряна (100% альфа, выделена)
            float alpha = (i < lives) ? 0.5f : 1.0f;
            livesViews[i].setAlpha(alpha);
        }
    }

    private void showStatus(String text) {
        gameStatus.setText(text);
        gameStatus.setVisibility(View.VISIBLE);
        gameStatus.postDelayed(() -> {
            if (isGameRunning && !isPaused) gameStatus.setVisibility(View.GONE);
        }, 700);
    }
}