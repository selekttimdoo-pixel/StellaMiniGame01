package com.stella.minigame01;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private TextToSpeech tts;
    private FrameLayout field;
    private TextView counter;
    private TextView title;
    private int collected = 0;
    private static final int TOTAL = 5;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tts = new TextToSpeech(this, this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(24, 36, 24, 24);

        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(6, 15, 45), Color.rgb(20, 43, 85)}
        );
        root.setBackground(background);

        title = makeText("Skupi sve plave zvezdice", 25, Color.WHITE);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView intro = makeText(
                "BLUE znači PLAVO\nAko želiš, ponovi za mnom: Blue",
                18,
                Color.rgb(190, 220, 255)
        );
        intro.setGravity(Gravity.CENTER);
        root.addView(intro, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        counter = makeText("0 / 5", 22, Color.WHITE);
        counter.setGravity(Gravity.CENTER);
        root.addView(counter, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        field = new FrameLayout(this);
        root.addView(field, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
        field.post(this::addStars);
    }

    private TextView makeText(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setPadding(8, 10, 8, 10);
        return view;
    }

    private void addStars() {
        int[][] positions = {{12,12}, {62,8}, {35,35}, {8,62}, {68,65}};
        for (int[] position : positions) {
            TextView star = makeText("★", 64, Color.rgb(70, 170, 255));
            star.setShadowLayer(18, 0, 0, Color.rgb(50, 150, 255));

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(120, 120);
            params.leftMargin = Math.max(0, (field.getWidth() - 120) * position[0] / 100);
            params.topMargin = Math.max(0, (field.getHeight() - 120) * position[1] / 100);

            star.setOnClickListener(v -> collect((TextView) v));
            field.addView(star, params);
        }
        speak("Blue");
    }

    private void collect(TextView star) {
        if (star.getVisibility() != View.VISIBLE) return;

        star.setVisibility(View.INVISIBLE);
        collected++;
        counter.setText(collected + " / " + TOTAL);
        speak("Blue");

        if (collected == TOTAL) {
            title.setText("BRAVO!  +10 XP");
            counter.setText("Sve plave zvezdice su skupljene ★");
            Toast.makeText(this, "PUUUM! +10 XP", Toast.LENGTH_LONG).show();
        }
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "stella");
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.ENGLISH);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
