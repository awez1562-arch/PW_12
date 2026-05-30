package com.example.multiwindowapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class RecordsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "wolf_game_prefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);

        TextView tvHighScore = findViewById(R.id.tvHighScore);
        Button btnBack = findViewById(R.id.btnBackRecords);

        // Чтение рекорда
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int highScore = prefs.getInt("high_score", 0);
        tvHighScore.setText("Лучший счёт: " + highScore);

        btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Обновляем рекорд при возврате из игры
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int highScore = prefs.getInt("high_score", 0);
        ((TextView) findViewById(R.id.tvHighScore)).setText("Лучший счёт: " + highScore);
    }
}