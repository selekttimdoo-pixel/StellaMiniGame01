package com.stella.minigame01;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class GameView extends View {
    public interface Listener {
        void onRoundStarted(String promptSerbian, String targetEnglish);
        void onMicRequested(String targetEnglish);
        void onGameFinished(int score, int correct, int wrong, int speechSuccess, int rounds);
    }

    private enum ShapeType {
        STAR("STAR", "ZVEZDE"),
        MOON("MOON", "MESECE"),
        SUN("SUN", "SUNCA");

        final String en;
        final String srTask;
        ShapeType(String en, String srTask) {
            this.en = en;
            this.srTask = srTask;
        }
    }

    private static class ColorDef {
        final String en;
        final String srTask;
        final int color;
        ColorDef(String en, String srTask, int color) {
            this.en = en;
            this.srTask = srTask;
            this.color = color;
        }
    }

    private static class Item {
        float x, y, radius;
        ShapeType shape;
        ColorDef color;
        boolean collected;

        Item(float x, float y, float radius, ShapeType shape, ColorDef color) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.shape = shape;
            this.color = color;
        }
    }

    private static final ColorDef[] COLORS = {
            new ColorDef("RED", "CRVENE", 0xFFFF4D5A),
            new ColorDef("BLUE", "PLAVE", 0xFF39A8FF),
            new ColorDef("GREEN", "ZELENE", 0xFF46D17A),
            new ColorDef("YELLOW", "ŽUTE", 0xFFFFD84D),
            new ColorDef("ORANGE", "NARANDŽASTE", 0xFFFF9F43),
            new ColorDef("PURPLE", "LJUBIČASTE", 0xFFB678FF),
            new ColorDef("BROWN", "SMEĐE", 0xFFA66A43)
    };

    private static final ShapeType[] SHAPES = ShapeType.values();
    private static final int TOTAL_ROUNDS = 5;
    private static final int TARGETS_PER_ROUND = 4;

    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final List<Item> items = new ArrayList<>();

    private LinearGradient sky;
    private int roundIndex = 0;
    private int score = 0;
    private int correct = 0;
    private int wrong = 0;
    private int speechSuccess = 0;
    private int targetsLeft = TARGETS_PER_ROUND;
    private boolean goalIsColor = true;
    private ColorDef targetColor;
    private ShapeType targetShape;
    private String targetEnglish = "";
    private String taskText = "";
    private String micStatus = "Pripremam mikrofon…";
    private String lastHeard = "";
    private boolean speechAwardedThisRound = false;
    private boolean transitioning = false;
    private boolean gameFinished = false;
    private RectF micButton = new RectF();
    private RectF replayButton = new RectF();

    public GameView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setMicStatus(String status) {
        micStatus = status == null ? "" : status;
        invalidate();
    }

    public void registerSpeechResult(String heard, boolean success) {
        lastHeard = heard == null ? "" : heard;
        if (success) {
            micStatus = "Bravo! Čuo sam: " + lastHeard;
            if (!speechAwardedThisRound && !gameFinished) {
                speechAwardedThisRound = true;
                speechSuccess++;
                score += 15;
            }
        } else {
            micStatus = lastHeard.isEmpty()
                    ? "Nisam razumeo. Probaj ponovo."
                    : "Čuo sam: " + lastHeard + " — probaj još jednom.";
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        sky = new LinearGradient(0, 0, 0, h,
                new int[]{0xFF061126, 0xFF0B2147, 0xFF12345E},
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP);
        if (items.isEmpty() && !gameFinished) {
            startRound(true);
        }
    }

    private void startRound(boolean first) {
        transitioning = false;
        speechAwardedThisRound = false;
        targetsLeft = TARGETS_PER_ROUND;
        lastHeard = "";

        if (first) {
            goalIsColor = random.nextBoolean();
        } else {
            goalIsColor = !goalIsColor;
        }

        if (goalIsColor) {
            ColorDef next;
            do {
                next = COLORS[random.nextInt(COLORS.length)];
            } while (targetColor != null && next.en.equals(targetColor.en));
            targetColor = next;
            targetEnglish = targetColor.en;
            taskText = "Sakupi sve " + targetColor.srTask.toLowerCase(Locale.ROOT);
        } else {
            ShapeType next;
            do {
                next = SHAPES[random.nextInt(SHAPES.length)];
            } while (targetShape != null && next == targetShape);
            targetShape = next;
            targetEnglish = targetShape.en;
            taskText = "Sakupi " + targetShape.srTask.toLowerCase(Locale.ROOT);
        }

        buildItems();
        invalidate();

        String prompt = taskText + ". Ako želiš, ponovi za mnom.";
        postDelayed(() -> listener.onRoundStarted(prompt, targetEnglish), 250);
    }

    private void buildItems() {
        items.clear();
        if (getWidth() <= 0 || getHeight() <= 0) return;

        int cols = 3;
        int rows = 4;
        float left = getWidth() * 0.18f;
        float right = getWidth() * 0.82f;
        float top = getHeight() * 0.30f;
        float bottom = getHeight() * 0.69f;
        float radius = Math.min(getWidth(), getHeight()) * 0.072f;

        List<Item> generated = new ArrayList<>();
        for (int i = 0; i < cols * rows; i++) {
            int row = i / cols;
            int col = i % cols;
            float x = left + (right - left) * col / (cols - 1f);
            float y = top + (bottom - top) * row / (rows - 1f);
            generated.add(new Item(x, y, radius, randomShape(), randomColor()));
        }

        Collections.shuffle(generated, random);

        for (int i = 0; i < TARGETS_PER_ROUND; i++) {
            Item item = generated.get(i);
            if (goalIsColor) item.color = targetColor;
            else item.shape = targetShape;
        }

        for (int i = TARGETS_PER_ROUND; i < generated.size(); i++) {
            Item item = generated.get(i);
            if (goalIsColor) {
                while (item.color.en.equals(targetColor.en)) item.color = randomColor();
            } else {
                while (item.shape == targetShape) item.shape = randomShape();
            }
        }

        items.addAll(generated);
    }

    private ColorDef randomColor() {
        return COLORS[random.nextInt(COLORS.length)];
    }

    private ShapeType randomShape() {
        return SHAPES[random.nextInt(SHAPES.length)];
    }

    private boolean isTarget(Item item) {
        return goalIsColor ? item.color.en.equals(targetColor.en) : item.shape == targetShape;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setShader(sky);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);

        drawBackgroundSparkles(canvas);

        if (gameFinished) {
            drawFinalScore(canvas);
            return;
        }

        drawHeader(canvas);
        drawItems(canvas);
        drawFooter(canvas);
    }

    private void drawBackgroundSparkles(Canvas canvas) {
        paint.setColor(0x55FFFFFF);
        for (int i = 0; i < 40; i++) {
            random.setSeed(4200L + i * 7919L);
            float x = random.nextFloat() * getWidth();
            float y = random.nextFloat() * getHeight();
            canvas.drawCircle(x, y, 1.5f + random.nextFloat() * 2.5f, paint);
        }
    }

    private void drawHeader(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        paint.setColor(0xFFBBD6FF);
        paint.setTextSize(w * 0.038f);
        canvas.drawText("RUNDA " + (roundIndex + 1) + " / " + TOTAL_ROUNDS, w / 2f, h * 0.065f, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(w * 0.064f);
        canvas.drawText(taskText, w / 2f, h * 0.115f, paint);

        paint.setColor(goalIsColor ? targetColor.color : 0xFF7EC8FF);
        paint.setTextSize(w * 0.092f);
        canvas.drawText(targetEnglish, w / 2f, h * 0.18f, paint);

        paint.setColor(0xFFD9E7FF);
        paint.setTextSize(w * 0.036f);
        canvas.drawText("Preostalo: " + targetsLeft + "    •    Score: " + score, w / 2f, h * 0.225f, paint);
    }

    private void drawItems(Canvas canvas) {
        for (Item item : items) {
            if (!item.collected) drawShape(canvas, item);
        }
    }

    private void drawShape(Canvas canvas, Item item) {
        float r = item.radius;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(item.color.color);
        paint.setShadowLayer(r * 0.34f, 0, 0, withAlpha(item.color.color, 160));

        if (item.shape == ShapeType.STAR) {
            Path p = starPath(item.x, item.y, r, r * 0.45f);
            canvas.drawPath(p, paint);
            paint.clearShadowLayer();
            stroke.setColor(0xCCFFFFFF);
            stroke.setStrokeWidth(Math.max(4f, r * 0.07f));
            canvas.drawPath(p, stroke);
        } else if (item.shape == ShapeType.SUN) {
            stroke.setColor(item.color.color);
            stroke.setStrokeWidth(Math.max(6f, r * 0.12f));
            stroke.setShadowLayer(r * 0.25f, 0, 0, withAlpha(item.color.color, 130));
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI * 2 / 12.0;
                float x1 = item.x + (float)Math.cos(a) * r * 0.72f;
                float y1 = item.y + (float)Math.sin(a) * r * 0.72f;
                float x2 = item.x + (float)Math.cos(a) * r * 1.08f;
                float y2 = item.y + (float)Math.sin(a) * r * 1.08f;
                canvas.drawLine(x1, y1, x2, y2, stroke);
            }
            stroke.clearShadowLayer();
            canvas.drawCircle(item.x, item.y, r * 0.68f, paint);
            paint.clearShadowLayer();
            stroke.setColor(0xCCFFFFFF);
            stroke.setStrokeWidth(Math.max(4f, r * 0.06f));
            canvas.drawCircle(item.x, item.y, r * 0.68f, stroke);
        } else {
            canvas.drawCircle(item.x, item.y, r * 0.88f, paint);
            paint.clearShadowLayer();
            paint.setColor(0xFF0B2147);
            canvas.drawCircle(item.x + r * 0.38f, item.y - r * 0.12f, r * 0.75f, paint);
            stroke.setColor(0x99FFFFFF);
            stroke.setStrokeWidth(Math.max(3f, r * 0.05f));
            Path edge = crescentEdge(item.x, item.y, r * 0.88f);
            canvas.drawPath(edge, stroke);
        }
        paint.clearShadowLayer();
    }

    private Path starPath(float cx, float cy, float outer, float inner) {
        Path path = new Path();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            float rad = (i % 2 == 0) ? outer : inner;
            float x = cx + (float)Math.cos(angle) * rad;
            float y = cy + (float)Math.sin(angle) * rad;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        return path;
    }

    private Path crescentEdge(float cx, float cy, float r) {
        Path path = new Path();
        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
        path.addArc(oval, 75, 210);
        return path;
    }

    private void drawFooter(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        RectF panel = new RectF(w * 0.06f, h * 0.76f, w * 0.94f, h * 0.96f);
        paint.setColor(0x55253E67);
        canvas.drawRoundRect(panel, 34, 34, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        paint.setColor(Color.WHITE);
        paint.setTextSize(w * 0.037f);
        canvas.drawText(micStatus, w / 2f, h * 0.815f, paint);

        if (!lastHeard.isEmpty()) {
            paint.setColor(0xFFB9CBE4);
            paint.setTextSize(w * 0.031f);
            canvas.drawText("Poslednje čuo: " + lastHeard, w / 2f, h * 0.85f, paint);
        }

        micButton.set(w * 0.20f, h * 0.875f, w * 0.80f, h * 0.94f);
        paint.setColor(0xFF2D8CFF);
        canvas.drawRoundRect(micButton, 42, 42, paint);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setTextSize(w * 0.041f);
        canvas.drawText("🎙  SLUŠAJ ME", w / 2f, h * 0.918f, paint);
    }

    private void drawFinalScore(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setColor(0xFFFFD85A);
        paint.setTextSize(w * 0.095f);
        canvas.drawText("BRAVO!", w / 2f, h * 0.18f, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(w * 0.055f);
        canvas.drawText("KRAJNJI SCORE", w / 2f, h * 0.27f, paint);

        paint.setColor(0xFF72C5FF);
        paint.setTextSize(w * 0.16f);
        canvas.drawText(String.valueOf(score), w / 2f, h * 0.40f, paint);

        paint.setColor(0xFFDDEAFF);
        paint.setTextSize(w * 0.040f);
        canvas.drawText("Tačni klikovi: " + correct, w / 2f, h * 0.50f, paint);
        canvas.drawText("Pogrešni klikovi: " + wrong, w / 2f, h * 0.55f, paint);
        canvas.drawText("Uspešan govor: " + speechSuccess + " / " + TOTAL_ROUNDS, w / 2f, h * 0.60f, paint);
        canvas.drawText("Završene runde: " + TOTAL_ROUNDS, w / 2f, h * 0.65f, paint);

        replayButton.set(w * 0.20f, h * 0.75f, w * 0.80f, h * 0.84f);
        paint.setColor(0xFF2D8CFF);
        canvas.drawRoundRect(replayButton, 46, 46, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(w * 0.045f);
        canvas.drawText("IGRAJ PONOVO", w / 2f, h * 0.808f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;

        float x = event.getX();
        float y = event.getY();

        if (gameFinished) {
            if (replayButton.contains(x, y)) restartGame();
            return true;
        }

        if (micButton.contains(x, y)) {
            listener.onMicRequested(targetEnglish);
            return true;
        }

        if (transitioning) return true;

        for (Item item : items) {
            if (item.collected) continue;
            float dx = x - item.x;
            float dy = y - item.y;
            float hit = item.radius * 1.18f;
            if (dx * dx + dy * dy <= hit * hit) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (isTarget(item)) {
                    item.collected = true;
                    correct++;
                    score += 10;
                    targetsLeft--;
                    if (targetsLeft <= 0) {
                        score += 20;
                        transitioning = true;
                        roundIndex++;
                        if (roundIndex >= TOTAL_ROUNDS) {
                            gameFinished = true;
                            listener.onGameFinished(score, correct, wrong, speechSuccess, TOTAL_ROUNDS);
                        } else {
                            postDelayed(() -> startRound(false), 850);
                        }
                    }
                } else {
                    wrong++;
                    score = Math.max(0, score - 3);
                }
                invalidate();
                return true;
            }
        }
        return true;
    }

    private void restartGame() {
        roundIndex = 0;
        score = 0;
        correct = 0;
        wrong = 0;
        speechSuccess = 0;
        targetColor = null;
        targetShape = null;
        gameFinished = false;
        transitioning = false;
        startRound(true);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
