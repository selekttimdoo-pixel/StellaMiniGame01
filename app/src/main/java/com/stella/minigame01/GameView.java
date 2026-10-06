package com.stella.minigame01;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    private Bitmap stellaScene;

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
            stellaScene = BitmapFactory.decodeResource(getResources(), R.drawable.stella_scene_bottom);
        } catch (Throwable ignored) {
            stellaScene = null;
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
        float top=getHeight()*0.25f, bottom=getHeight()*0.68f;
        float radius=Math.min(getWidth(),getHeight())*0.057f;

        List<Item> generated=new ArrayList<>();
        for (int i=0;i<FIELD_ITEMS;i++) {
            int row=i/cols, col=i%cols;
            float x=left+(right-left)*col/(cols-1f);
            float y=top+(bottom-top)*row/(rows-1f);
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
        paint.setShader(sky);
        canvas.drawRect(0,0,getWidth(),getHeight(),paint);
        paint.setShader(null);

        drawNightSky(canvas);
        try { drawScenicBottom(canvas); } catch (Throwable ignored) { }

        if (gameFinished) { drawFinalScore(canvas); return; }
        drawBrand(canvas);
        drawHeaderCard(canvas);
        drawItems(canvas);
        drawFooter(canvas);
    }

    private void drawNightSky(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        for (int i=0;i<95;i++) {
            random.setSeed(9001L+i*7919L);
            float x=random.nextFloat()*getWidth();
            float y=random.nextFloat()*getHeight()*0.72f;
            float r=1.1f+random.nextFloat()*2.4f;
            int a=90+random.nextInt(130);
            paint.setColor(Color.argb(a,255,255,255));
            canvas.drawCircle(x,y,r,paint);
        }
        paint.setColor(0x44A8C7FF);
        RectF galaxy=new RectF(getWidth()*0.52f,getHeight()*0.07f,getWidth()*0.95f,getHeight()*0.58f);
        canvas.save();
        canvas.rotate(18, galaxy.centerX(), galaxy.centerY());
        canvas.drawOval(galaxy, paint);
        canvas.restore();
    }

    private void drawScenicBottom(Canvas canvas) {
        if (stellaScene==null) return;
        float top=getHeight()*0.72f;
        Rect src=new Rect(0,0,stellaScene.getWidth(),stellaScene.getHeight());
        RectF dst=new RectF(0,top,getWidth(),getHeight());
        paint.setAlpha(255);
        canvas.drawBitmap(stellaScene,src,dst,paint);
    }

    private void drawBrand(Canvas canvas) {
        float w=getWidth(), h=getHeight();
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create(Typeface.SERIF,Typeface.NORMAL));
        paint.setColor(Color.WHITE);
        paint.setTextSize(w*0.052f);
        canvas.drawText("STELLA",w*0.055f,h*0.045f,paint);
        canvas.drawText("HOUSE",w*0.055f,h*0.085f,paint);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));
        paint.setTextSize(w*0.021f);
        paint.setColor(0xFFDCE8FF);
        canvas.drawText("LANGUAGE FOR A BRIGHTER YOU",w*0.055f,h*0.112f,paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(0xEFFFFFFF);
        paint.setTextSize(w*0.034f);
        canvas.drawText("★  " + score,w*0.93f,h*0.055f,paint);
    }

    private void drawHeaderCard(Canvas canvas) {
        float w=getWidth(), h=getHeight();
        RectF card=new RectF(w*0.18f,h*0.105f,w*0.82f,h*0.225f);
        paint.setColor(0xFFF9F0DE);
        paint.setShadowLayer(14,0,5,0x66000000);
        canvas.drawRoundRect(card,34,34,paint);
        paint.clearShadowLayer();

        stroke.setColor(0xFFE2B98A);
        stroke.setStrokeWidth(Math.max(3f,w*0.006f));
        canvas.drawRoundRect(card,34,34,stroke);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));
        paint.setColor(0xFF10264A);
        paint.setTextSize(w*0.047f);
        canvas.drawText(taskText,w/2f,h*0.158f,paint);

        paint.setColor(goalIsColor ? targetColor.color : 0xFF2F9CFF);
        paint.setTextSize(w*0.061f);
        canvas.drawText(targetEnglish,w/2f,h*0.202f,paint);
    }

    private void drawItems(Canvas canvas) {
        for (Item item:items) if (!item.collected) drawShape(canvas,item);
    }

    private void drawShape(Canvas canvas, Item item) {
        float r=item.radius;
        int c=item.color.color;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(c);
        paint.setShadowLayer(r*0.55f,0,0,withAlpha(c,210));

        if (item.shape==ShapeType.STAR) {
            Path p=starPath(item.x,item.y,r,r*0.46f);
            canvas.drawPath(p,paint);
            paint.clearShadowLayer();
            stroke.setColor(0xDDFFFFFF);
            stroke.setStrokeWidth(Math.max(4f,r*0.075f));
            canvas.drawPath(p,stroke);
            paint.setColor(withAlpha(Color.WHITE,55));
            canvas.drawCircle(item.x-r*0.22f,item.y-r*0.22f,r*0.16f,paint);
        } else if (item.shape==ShapeType.SUN) {
            stroke.setColor(c);
            stroke.setStrokeWidth(Math.max(6f,r*0.13f));
            stroke.setShadowLayer(r*0.42f,0,0,withAlpha(c,180));
            for (int i=0;i<12;i++) {
                double a=i*Math.PI*2/12.0;
                float x1=item.x+(float)Math.cos(a)*r*0.74f;
                float y1=item.y+(float)Math.sin(a)*r*0.74f;
                float x2=item.x+(float)Math.cos(a)*r*1.12f;
                float y2=item.y+(float)Math.sin(a)*r*1.12f;
                canvas.drawLine(x1,y1,x2,y2,stroke);
            }
            stroke.clearShadowLayer();
            canvas.drawCircle(item.x,item.y,r*0.72f,paint);
            paint.clearShadowLayer();
            stroke.setColor(0xDDFFFFFF);
            stroke.setStrokeWidth(Math.max(4f,r*0.06f));
            canvas.drawCircle(item.x,item.y,r*0.72f,stroke);
        } else {
            canvas.drawCircle(item.x,item.y,r*0.9f,paint);
            paint.clearShadowLayer();
            paint.setColor(0xFF0B2A62);
            canvas.drawCircle(item.x+r*0.38f,item.y-r*0.14f,r*0.76f,paint);
            stroke.setColor(0xCCFFFFFF);
            stroke.setStrokeWidth(Math.max(3f,r*0.05f));
            canvas.drawArc(new RectF(item.x-r*0.9f,item.y-r*0.9f,item.x+r*0.9f,item.y+r*0.9f),70,220,false,stroke);
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
        RectF panel=new RectF(w*0.08f,h*0.665f,w*0.92f,h*0.715f);
        paint.setColor(0x66102C5D);
        canvas.drawRoundRect(panel,30,30,paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL));
        paint.setColor(Color.WHITE);
        paint.setTextSize(w*0.026f);
        String status = micStatus + "   •   " + targetsLeft + " preostalo";
        canvas.drawText(status,w/2f,h*0.695f,paint);

        micButton.set(w*0.80f,h*0.625f,w*0.94f,h*0.705f);
        paint.setColor(0xFF2D8CFF);
        paint.setShadowLayer(16,0,0,0xAA2D8CFF);
        canvas.drawCircle(micButton.centerX(),micButton.centerY(),w*0.055f,paint);
        paint.clearShadowLayer();
        paint.setColor(Color.WHITE);
        paint.setTextSize(w*0.042f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("MIC",micButton.centerX(),micButton.centerY()+w*0.014f,paint);
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
