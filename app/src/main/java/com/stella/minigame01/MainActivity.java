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
import java.util.List;
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

        tts = new TextToSpeech(this, this);
        permissionGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        gameView = new GameView(this, new GameView.Listener() {
            @Override
            public void onRoundStarted(String promptSerbian, String targetEnglish) {
                currentTarget = targetEnglish.toLowerCase(Locale.US);
                speakRound(promptSerbian, targetEnglish);
            }

            @Override
            public void onMicRequested(String targetEnglish) {
                currentTarget = targetEnglish.toLowerCase(Locale.US);
                ensureMicAndListen();
            }

            @Override
            public void onGameFinished(int score, int correct, int wrong, int speechSuccess, int rounds) {
                stopListening();
            }
        });

        setContentView(gameView);
        setupSpeechRecognizer();

        if (!permissionGranted) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        } else {
            gameView.setMicStatus("Mikrofon spreman");
        }
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            gameView.setMicStatus("Prepoznavanje govora nije dostupno na ovom telefonu");
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
                gameView.setMicStatus("Slušam… reci: " + currentTarget.toUpperCase(Locale.US));
            }

            @Override public void onBeginningOfSpeech() {
                gameView.setMicStatus("Čujem te…");
            }

            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() {
                gameView.setMicStatus("Proveravam…");
            }

            @Override
            public void onError(int error) {
                gameView.setMicStatus("Nisam razumeo. Dodirni mikrofon i probaj ponovo.");
            }

            @Override
            public void onResults(Bundle results) {
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

            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    private boolean matchesTarget(String heard, String target) {
        if (heard == null || target == null || target.isEmpty()) return false;
        String clean = heard.toLowerCase(Locale.US).replaceAll("[^a-z ]", " ").trim();
        String[] words = clean.split("\\s+");
        for (String word : words) {
            if (word.equals(target)) return true;
            if (Math.abs(word.length() - target.length()) <= 1 && levenshtein(word, target) <= 1) return true;
        }
        return false;
    }

    private int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
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
        gameView.setMicStatus(permissionGranted ? "Spremi se da kažeš " + targetEnglish.toUpperCase(Locale.US) : "Potrebna je dozvola za mikrofon");

        if (ttsReady) {
            tts.setLanguage(new Locale("sr", "RS"));
            tts.speak(promptSerbian, TextToSpeech.QUEUE_FLUSH, null, "prompt_sr");
            handler.postDelayed(() -> {
                if (tts != null) {
                    tts.setLanguage(Locale.US);
                    tts.speak(targetEnglish, TextToSpeech.QUEUE_FLUSH, null, "target_en");
                }
            }, 1300);
        }

        handler.postDelayed(() -> {
            if (permissionGranted) startListening();
        }, 2600);
    }

    private void ensureMicAndListen() {
        if (!permissionGranted) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        startListening();
    }

    private void startListening() {
        if (speechRecognizer == null || recognizerIntent == null || currentTarget.isEmpty()) return;
        try {
            speechRecognizer.cancel();
            speechRecognizer.startListening(recognizerIntent);
        } catch (Exception e) {
            gameView.setMicStatus("Mikrofon trenutno nije dostupan. Probaj ponovo.");
        }
    }

    private void stopListening() {
        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (Exception ignored) { }
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            tts.setSpeechRate(0.92f);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            permissionGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (permissionGranted) {
                gameView.setMicStatus("Mikrofon dozvoljen");
                handler.postDelayed(this::startListening, 500);
            } else {
                gameView.setMicStatus("Mikrofon nije dozvoljen");
            }
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
