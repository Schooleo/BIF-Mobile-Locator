package com.bif.locator;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private int count = 0;

    private void updateCounter(TextView textView) {
        textView.setText(String.format("%s%s", getString(R.string.hello_world_current_count), count));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView textView = findViewById(R.id.helloCounter);
        Button incrementBtn = findViewById(R.id.incrementButton);
        Button resetBtn = findViewById(R.id.resetButton);

        updateCounter(textView);

        resetBtn.setOnClickListener(v -> {
            count = 0;
            updateCounter(textView);
        });

        incrementBtn.setOnClickListener(v -> {
            count++;
            updateCounter(textView);
        });
    }
}
