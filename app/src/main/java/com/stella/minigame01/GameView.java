package com.stella.minigame01;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
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
        final String en, srTask;
        ShapeType(String en, String srTask) { this.en = en; this.srTask = srTask; }
    }

    private static class ColorDef {
        final String en, srTask;
        final int color;
        ColorDef(String en, String srTask, int color) {
            this.en = en; this.srTask = srTask; this.color = color;
        }
    }

    private static class Item {
        float x, y, radius;
        ShapeType shape;
        ColorDef color;
        boolean collected;
        Item(float x, float y, float radius, ShapeType shape, ColorDef color) {
            this.x=x; this.y=y; this.radius=radius; this.shape=shape; this.color=color;
        }
    }

    private static final ColorDef[] COLORS = {
            new ColorDef("RED","CRVENE",0xFFFF4F5F),
            new ColorDef("BLUE","PLAVE",0xFF2F9CFF),
            new ColorDef("GREEN","ZELENE",0xFF43D17A),
            new ColorDef("YELLOW","ŽUTE",0xFFFFD84D),
            new ColorDef("ORANGE","NARANDŽASTE",0xFFFF9C3A),
            new ColorDef("PURPLE","LJUBIČASTE",0xFFB66BFF),
            new ColorDef("BROWN","SMEĐE",0xFFAD7048)
    };

    private static final ShapeType[] SHAPES = ShapeType.values();
    private static final int TOTAL_ROUNDS = 5;
    private static final int TARGETS_PER_ROUND = 5;
    private static final int FIELD_ITEMS = 15;

    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final List<Item> items = new ArrayList<>();
    private Bitmap artAtlas;

    private LinearGradient sky;
    private int roundIndex=0, score=0, correct=0, wrong=0, speechSuccess=0;
    private int targetsLeft=TARGETS_PER_ROUND;
    private boolean goalIsColor=true, speechAwardedThisRound=false, transitioning=false, gameFinished=false;
    private ColorDef targetColor;
    private ShapeType targetShape;
    private String targetEnglish="", taskText="", micStatus="Pripremam mikrofon…", lastHeard="";
    private final RectF micButton = new RectF();
    private final RectF replayButton = new RectF();

    public GameView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        try {
            artAtlas = android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.stella_v5_atlas);
        } catch (Throwable ignored) {
            artAtlas = null;
        }
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setMicStatus(String status) { micStatus = status == null ? "" : status; invalidate(); }

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
            micStatus = lastHeard.isEmpty() ? "Nisam razumeo. Probaj ponovo." : "Čuo sam: " + lastHeard + " — probaj još jednom.";
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        sky = new LinearGradient(0, 0, 0, h,
                new int[]{0xFF061232,0xFF0B2B67,0xFF184A88,0xFFFF7A49},
                new float[]{0f,0.45f,0.72f,1f}, Shader.TileMode.CLAMP);
        if (items.isEmpty() && !gameFinished) startRound(true);
    }

    private void startRound(boolean first) {
        transitioning=false;
        speechAwardedThisRound=false;
        targetsLeft=TARGETS_PER_ROUND;
        lastHeard="";

        goalIsColor = first ? random.nextBoolean() : !goalIsColor;

        if (goalIsColor) {
            ColorDef next;
            do { next = COLORS[random.nextInt(COLORS.length)]; }
            while (targetColor != null && next.en.equals(targetColor.en));
            targetColor=next;
            targetEnglish=targetColor.en;
            taskText="Sakupi sve " + targetColor.srTask.toLowerCase(Locale.ROOT);
        } else {
            ShapeType next;
            do { next = SHAPES[random.nextInt(SHAPES.length)]; }
            while (targetShape != null && next == targetShape);
            targetShape=next;
            targetEnglish=targetShape.en;
            taskText="Sakupi " + targetShape.srTask.toLowerCase(Locale.ROOT);
        }

        buildItems();
        invalidate();
        postDelayed(() -> listener.onRoundStarted(taskText + ". Ako želiš, ponovi za mnom.", targetEnglish), 250);
    }

    private void buildItems() {
        items.clear();
        if (getWidth()<=0 || getHeight()<=0) return;

        int cols=3, rows=5;
        float left=getWidth()*0.15f, right=getWidth()*0.85f;
        float top=LayoutRules.gameplayTop(getHeight()), bottom=LayoutRules.gameplayBottom(getHeight());
        float radius=Math.min(getWidth(),getHeight())*0.057f;

        List<Item> generated=new ArrayList<>();
        for (int i=0;i<FIELD_ITEMS;i++) {
            int row=i/cols, col=i%cols;
            float x=left+(right-left)*col/(cols-1f);
            float y=LayoutRules.rowY(getHeight(), row, rows);
            float jitterX=(random.nextFloat()-0.5f)*getWidth()*0.05f;
            float jitterY=(random.nextFloat()-0.5f)*getHeight()*0.018f;
            generated.add(new Item(x+jitterX,y+jitterY,radius,randomShape(),randomColor()));
        }
        Collections.shuffle(generated, random);

        for (int i=0;i<TARGETS_PER_ROUND;i++) {
            Item item=generated.get(i);
            if (goalIsColor) item.color=targetColor; else item.shape=targetShape;
        }
        for (int i=TARGETS_PER_ROUND;i<generated.size();i++) {
            Item item=generated.get(i);
            if (goalIsColor) while (item.color.en.equals(targetColor.en)) item.color=randomColor();
            else while (item.shape==targetShape) item.shape=randomShape();
        }
        items.addAll(generated);
    }

    private ColorDef randomColor(){ return COLORS[random.nextInt(COLORS.length)]; }
    private ShapeType randomShape(){ return SHAPES[random.nextInt(SHAPES.length)]; }
    private boolean isTarget(Item item){ return goalIsColor ? item.color.en.equals(targetColor.en) : item.shape==targetShape; }

    @Override
    protected void onDraw(Canvas canvas) {
        drawBackground(canvas);

        if (gameFinished) { drawFinalScore(canvas); return; }
        drawScoreBadge(canvas);
        drawHeaderCard(canvas);
        drawItems(canvas);
        drawStellaLayer(canvas);
        drawFooter(canvas);
    }

    private void drawBackground(Canvas canvas) {
        if (artAtlas == null) {
            paint.setShader(sky);
            canvas.drawRect(0,0,getWidth(),getHeight(),paint);
            paint.setShader(null);
            return;
        }
        Rect src = new Rect(0, 0, 360, 640);
        RectF dst = new RectF(0, 0, getWidth(), getHeight());
        paint.setAlpha(255);
        canvas.drawBitmap(artAtlas, src, dst, paint);
    }

    private void drawStellaLayer(Canvas canvas) {
        if (artAtlas == null) return;
        float w=getWidth(), h=getHeight();
        Rect src = new Rect(0, 640, 220, 1031);
        float targetW = w * 0.34f;
        float targetH = targetW * (391f / 220f);
        RectF dst = new RectF(w*0.015f, h-targetH-h*0.018f, w*0.015f+targetW, h-h*0.018f);
        paint.setAlpha(255);
        canvas.drawBitmap(artAtlas, src, dst, paint);
    }

    private void drawScoreBadge(Canvas canvas) {
        float w=getWidth(), h=getHeight();
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));
        paint.setColor(0xEFFFFFFF);
        paint.setTextSize(w*0.034f);
        canvas.drawText("★  " + score,w*0.94f,h*0.045f,paint);
    }

    private void drawHeaderCard(Canvas canvas) {
        float w=getWidth(), h=getHeight();
        RectF card=new RectF(w*0.19f,h*0.035f,w*0.81f,h*0.125f);
        paint.setColor(0xD9FFF7E8);
        paint.setShadowLayer(10,0,4,0x55000000);
        canvas.drawRoundRect(card,30,30,paint);
        paint.clearShadowLayer();

        stroke.setColor(0xFFD9B078);
        stroke.setStrokeWidth(Math.max(2.5f,w*0.0045f));
        canvas.drawRoundRect(card,30,30,stroke);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));
        paint.setColor(0xFF10264A);
        paint.setTextSize(w*0.038f);
        canvas.drawText(taskText,w/2f,h*0.073f,paint);

        paint.setColor(goalIsColor ? targetColor.color : 0xFF2F9CFF);
        paint.setTextSize(w*0.052f);
        canvas.drawText(targetEnglish,w/2f,h*0.108f,paint);
    }

    private void drawItems(Canvas canvas) {
        for (Item item:items) if (!item.collected) drawShape(canvas,item);
    }

    private void drawShape(Canvas canvas, Item item) {
        if (artAtlas != null) {
            int col = colorColumn(item.color.en);
            int row = item.shape==ShapeType.STAR ? 0 : item.shape==ShapeType.SUN ? 1 : 2;
            int left = SpriteLayout.cellLeft(420, 7, col);
            int top = SpriteLayout.rowTop(row);
            Rect src = new Rect(left, 1031 + top, left + SpriteLayout.cropSize(), 1031 + top + 78);
            float r = item.radius * 1.10f;
            RectF dst = new RectF(item.x-r, item.y-r*1.15f, item.x+r, item.y+r*1.15f);
            paint.setAlpha(255);
            canvas.drawBitmap(artAtlas, src, dst, paint);
            return;
        }
        drawFallbackShape(canvas, item);
    }

    private int colorColumn(String en) {
        if ("RED".equals(en)) return 0;
        if ("BLUE".equals(en)) return 1;
        if ("GREEN".equals(en)) return 2;
        if ("YELLOW".equals(en)) return 3;
        if ("ORANGE".equals(en)) return 4;
        if ("PURPLE".equals(en)) return 5;
        return 6;
    }

    private void drawFallbackShape(Canvas canvas, Item item) {
        float r=item.radius;
        int c=item.color.color;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(c);
        paint.setShadowLayer(r*0.55f,0,0,withAlpha(c,210));
        if (item.shape==ShapeType.STAR) {
            Path p=starPath(item.x,item.y,r,r*0.46f);
            canvas.drawPath(p,paint);
        } else if (item.shape==ShapeType.SUN) {
            canvas.drawCircle(item.x,item.y,r*0.72f,paint);
        } else {
            canvas.drawCircle(item.x,item.y,r*0.9f,paint);
            paint.setColor(0xFF0B2A62);
            canvas.drawCircle(item.x+r*0.38f,item.y-r*0.14f,r*0.76f,paint);
        }
        paint.clearShadowLayer();
    }

    private Path starPath(float cx,float cy,float outer,float inner) {
        Path path=new Path();
        for (int i=0;i<10;i++) {
            double angle=-Math.PI/2+i*Math.PI/5;
            float rad=(i%2==0)?outer:inner;
            float x=cx+(float)Math.cos(angle)*rad;
            float y=cy+(float)Math.sin(angle)*rad;
            if(i==0) path.moveTo(x,y); else path.lineTo(x,y);
        }
        path.close();
        return path;
    }

    private void drawFooter(Canvas canvas) {
        float w=getWidth(), h=getHeight();

        RectF panel=new RectF(w*0.20f,h*0.605f,w*0.76f,h*0.645f);
        paint.setColor(0x55102C5D);
        canvas.drawRoundRect(panel,28,28,paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL));
        paint.setColor(Color.WHITE);
        paint.setTextSize(w*0.024f);
        canvas.drawText(micStatus,panel.centerX(),h*0.632f,paint);

        micButton.set(w*0.81f,h*0.59f,w*0.95f,h*0.67f);
        paint.setColor(0xFF2D8CFF);
        paint.setShadowLayer(14,0,0,0x992D8CFF);
        canvas.drawCircle(micButton.centerX(),micButton.centerY(),w*0.054f,paint);
        paint.clearShadowLayer();
        paint.setColor(Color.WHITE);
        paint.setTextSize(w*0.039f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("MIC",micButton.centerX(),micButton.centerY()+w*0.013f,paint);
    }

    private void drawFinalScore(Canvas canvas) {
        float w=getWidth(), h=getHeight();
        paint.setColor(0xAA05122D);
        canvas.drawRect(0,0,w,h,paint);

        RectF card=new RectF(w*0.12f,h*0.20f,w*0.88f,h*0.72f);
        paint.setColor(0xFFF9F0DE);
        paint.setShadowLayer(18,0,8,0x88000000);
        canvas.drawRoundRect(card,42,42,paint);
        paint.clearShadowLayer();

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));
        paint.setColor(0xFF132A50);
        paint.setTextSize(w*0.075f);
        canvas.drawText("BRAVO!",w/2f,h*0.30f,paint);

        paint.setColor(0xFF2D8CFF);
        paint.setTextSize(w*0.15f);
        canvas.drawText(String.valueOf(score),w/2f,h*0.43f,paint);

        paint.setColor(0xFF31455F);
        paint.setTextSize(w*0.034f);
        canvas.drawText("Tačni klikovi: "+correct,w/2f,h*0.51f,paint);
        canvas.drawText("Pogrešni klikovi: "+wrong,w/2f,h*0.56f,paint);
        canvas.drawText("Uspešan govor: "+speechSuccess+" / "+TOTAL_ROUNDS,w/2f,h*0.61f,paint);

        replayButton.set(w*0.27f,h*0.64f,w*0.73f,h*0.70f);
        paint.setColor(0xFF2D8CFF);
        canvas.drawRoundRect(replayButton,38,38,paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(w*0.037f);
        canvas.drawText("IGRAJ PONOVO",w/2f,h*0.682f,paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction()!=MotionEvent.ACTION_UP) return true;
        float x=event.getX(), y=event.getY();

        if (gameFinished) {
            if (replayButton.contains(x,y)) restartGame();
            return true;
        }
        if (micButton.contains(x,y)) {
            listener.onMicRequested(targetEnglish);
            return true;
        }
        if (transitioning) return true;

        for (Item item:items) {
            if (item.collected) continue;
            float dx=x-item.x, dy=y-item.y;
            float hit=item.radius*1.25f;
            if (dx*dx+dy*dy<=hit*hit) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (isTarget(item)) {
                    item.collected=true; correct++; score+=10; targetsLeft--;
                    if (targetsLeft<=0) {
                        score+=20; transitioning=true; roundIndex++;
                        if (roundIndex>=TOTAL_ROUNDS) {
                            gameFinished=true;
                            listener.onGameFinished(score,correct,wrong,speechSuccess,TOTAL_ROUNDS);
                        } else postDelayed(() -> startRound(false),850);
                    }
                } else {
                    wrong++; score=Math.max(0,score-3);
                }
                invalidate();
                return true;
            }
        }
        return true;
    }

    private void restartGame() {
        roundIndex=0; score=0; correct=0; wrong=0; speechSuccess=0;
        targetColor=null; targetShape=null; gameFinished=false; transitioning=false;
        startRound(true);
    }

    private int withAlpha(int color,int alpha){ return Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color)); }
}
