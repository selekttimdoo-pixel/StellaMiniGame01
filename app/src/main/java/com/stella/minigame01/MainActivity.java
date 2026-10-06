package com.stella.minigame01;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_AUDIO = 1001;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private GameView gameView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentTarget = "";
    private boolean permissionGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            gameView = new GameView(this, new GameView.Listener() {
                @Override public void onRoundStarted(String promptSerbian, String targetEnglish) {
                    currentTarget = targetEnglish.toLowerCase(Locale.US);
                    speakRound(promptSerbian, targetEnglish);
                }
                @Override public void onMicRequested(String targetEnglish) {
                    currentTarget = targetEnglish.toLowerCase(Locale.US);
                    ensureMicAndListen();
                }
                @Override public void onGameFinished(int score, int correct, int wrong, int speechSuccess, int rounds) {
                    stopListening();
                }
            });
            setContentView(gameView);
        } catch (Throwable t) {
            android.widget.TextView fallback = new android.widget.TextView(this);
            fallback.setText("Stella Mini Game\nGreška pri učitavanju scene");
            fallback.setTextSize(24);
            fallback.setTextColor(android.graphics.Color.WHITE);
            fallback.setGravity(android.view.Gravity.CENTER);
            fallback.setBackgroundColor(android.graphics.Color.rgb(6,18,50));
            setContentView(fallback);
            return;
        }

        try {
            tts = new TextToSpeech(this, this);
        } catch (Throwable ignored) {
            tts = null;
        }

        permissionGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        setupSpeechRecognizerSafely();

        if (!permissionGranted) {
            try {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            } catch (Throwable ignored) {
                gameView.setMicStatus("Mikrofon nije dostupan");
            }
        } else {
            gameView.setMicStatus(speechRecognizer != null ? "Mikrofon spreman" : "Dodirni MIC za govor");
        }
    }

    private void setupSpeechRecognizerSafely() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                gameView.setMicStatus("Prepoznavanje govora nije dostupno");
                return;
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    gameView.setMicStatus("Slušam… reci " + currentTarget.toUpperCase(Locale.US));
                }
                @Override public void onBeginningOfSpeech() { gameView.setMicStatus("Čujem te…"); }
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { gameView.setMicStatus("Proveravam…"); }
                @Override public void onError(int error) { gameView.setMicStatus("Nisam razumeo. Dodirni MIC i probaj ponovo."); }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String heard = (matches == null || matches.isEmpty()) ? "" : matches.get(0);
                    boolean success = false;
                    if (matches != null) {
                        for (String candidate : matches) {
                            if (matchesTarget(candidate, currentTarget)) {
                                success = true;
                                heard = candidate;
                                break;
                            }
                        }
                    }
                    gameView.registerSpeechResult(heard, success);
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        } catch (Throwable t) {
            speechRecognizer = null;
            recognizerIntent = null;
            gameView.setMicStatus("Govor trenutno nije dostupan");
        }
    }

    private boolean matchesTarget(String heard, String target) {
        if (heard == null || target == null || target.isEmpty()) return false;
        String clean = heard.toLowerCase(Locale.US).replaceAll("[^a-z ]", " ").trim();
        for (String word : clean.split("\\s+")) {
            if (word.equals(target)) return true;
            if (Math.abs(word.length() - target.length()) <= 1 && levenshtein(word, target) <= 1) return true;
        }
        return false;
    }

    private int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }

    private void speakRound(String promptSerbian, String targetEnglish) {
        if (gameView == null) return;
        gameView.setMicStatus(permissionGranted ? "Dodirni MIC ako želiš da kažeš " + targetEnglish : "Potrebna dozvola za mikrofon");

        if (ttsReady && tts != null) {
            try {
                tts.setLanguage(new Locale("sr", "RS"));
                tts.speak(promptSerbian, TextToSpeech.QUEUE_FLUSH, null, "prompt_sr");
                handler.postDelayed(() -> {
                    try {
                        if (tts != null) {
                            tts.setLanguage(Locale.US);
                            tts.speak(targetEnglish, TextToSpeech.QUEUE_FLUSH, null, "target_en");
                        }
                    } catch (Throwable ignored) {}
                }, 1300);
            } catch (Throwable ignored) {}
        }
    }

    private void ensureMicAndListen() {
        if (!permissionGranted) {
            try { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO); }
            catch (Throwable ignored) { gameView.setMicStatus("Mikrofon nije dostupan"); }
            return;
        }
        startListening();
    }

    private void startListening() {
        if (speechRecognizer == null || recognizerIntent == null || currentTarget.isEmpty()) {
            gameView.setMicStatus("Prepoznavanje govora nije dostupno");
            return;
        }
        try {
            speechRecognizer.cancel();
            speechRecognizer.startListening(recognizerIntent);
        } catch (Throwable t) {
            gameView.setMicStatus("Mikrofon trenutno nije dostupan");
        }
    }

    private void stopListening() {
        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (Throwable ignored) {}
        }
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            ttsReady = true;
            try { tts.setSpeechRate(0.92f); } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            permissionGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (gameView != null) {
                gameView.setMicStatus(permissionGranted ? "Mikrofon dozvoljen — dodirni MIC" : "Mikrofon nije dozvoljen");
            }
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null) {
            try { speechRecognizer.destroy(); } catch (Throwable ignored) {}
        }
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }
}
