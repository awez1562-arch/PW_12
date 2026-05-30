package com.example.multiwindowapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MySettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "wolf_game_prefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_settings);

        EditText etName = findViewById(R.id.etPlayerName);
        Switch swSound = findViewById(R.id.swSound);
        RadioGroup rgDiff = findViewById(R.id.rbEasy).getId() == 0 ? findViewById(R.id.rgDifficulty) : findViewById(R.id.rgDifficulty);
        Button btnSave = findViewById(R.id.btnSaveSettings);

        // Загрузка текущих настроек
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        etName.setText(prefs.getString("player_name", ""));
        swSound.setChecked(prefs.getBoolean("sound_enabled", true));

        String savedDiff = prefs.getString("difficulty", "Средняя");
        if ("Лёгкая".equals(savedDiff)) rgDiff.check(R.id.rbEasy);
        else if ("Сложная".equals(savedDiff)) rgDiff.check(R.id.rbHard);
        else rgDiff.check(R.id.rbMedium);

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            boolean sound = swSound.isChecked();
            String difficulty;
            int selectedId = rgDiff.getCheckedRadioButtonId();
            if (selectedId == R.id.rbEasy) difficulty = "Лёгкая";
            else if (selectedId == R.id.rbHard) difficulty = "Сложная";
            else difficulty = "Средняя";

            // Сохранение
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("player_name", name.isEmpty() ? "Игрок" : name);
            editor.putBoolean("sound_enabled", sound);
            editor.putString("difficulty", difficulty);
            editor.apply(); // Асинхронное сохранение

            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
            finish(); // Возврат на главный экран
        });
    }
}