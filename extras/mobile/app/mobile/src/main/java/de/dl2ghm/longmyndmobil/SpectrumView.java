package de.dl2ghm.longmyndmobil;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import org.json.*;
import java.util.*;

/** BATC remote spectrum; tap detected signal, long-press for manual rate. */
public class SpectrumView extends View {
    public interface Listener { void select(int frequencyKhz,int rateKsps,boolean chooseRate); }
    Listener listener;
    final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    final GestureDetector gestures;final ScaleGestureDetector scaling;
    int[] bins=new int[0];JSONArray signals=new JSONArray();boolean online=false;
    double low=10490500,span=9000;
    final double[] tuned={0,0};final String[] calls={"",""};final boolean[] locked={false,false};
    long received=0;
    boolean multiTouch=false;
    final float density;
    public SpectrumView(Context context){
        super(context);density=getResources().getDisplayMetrics().density;
        setContentDescription("QO-100 BATC-Spektrum. Signal antippen zum Abstimmen. Zum Zoomen zwei Finger verwenden.");
        scaling=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){
                double anchor=frequency(d.getFocusX()),next=Math.max(250,Math.min(9000,span/d.getScaleFactor()));
                low=anchor-(anchor-low)*next/span;span=next;clamp();invalidate();return true;
            }
        });
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(android.view.MotionEvent e){return true;}
            @Override public boolean onSingleTapConfirmed(android.view.MotionEvent e){tap(e.getX(),false);performClick();return true;}
            @Override public void onLongPress(android.view.MotionEvent e){if(!scaling.isInProgress())tap(e.getX(),true);}
            @Override public boolean onDoubleTap(android.view.MotionEvent e){low=10490500;span=9000;invalidate();return true;}
            @Override public boolean onScroll(android.view.MotionEvent a,android.view.MotionEvent b,float dx,float dy){
                if(!scaling.isInProgress()&&span<9000){low+=dx/Math.max(1,getWidth()-16*density)*span;clamp();invalidate();}return true;
            }
        });
    }
    void clamp(){low=Math.max(10490500,Math.min(10499500-span,low));}
    double frequency(float x){return low+(x-8*density)/Math.max(1,getWidth()-16*density)*span;}
    float x(double frequency){return 8*density+(float)((frequency-low)/span)*(getWidth()-16*density);}
    public void setData(JSONObject data)throws JSONException{
        online=data.getBoolean("online");JSONArray array=data.getJSONArray("bins");
        bins=new int[array.length()];for(int i=0;i<bins.length;i++)bins[i]=array.getInt(i);
        signals=data.getJSONArray("signals");received=SystemClock.elapsedRealtime();invalidate();
    }
    public void setTuner(int channel,int frequency,String call,boolean lock){tuned[channel]=frequency;calls[channel]=call;locked[channel]=lock;invalidate();}
    void tap(float position,boolean manual){
        if(listener==null||!online||SystemClock.elapsedRealtime()-received>4000)return;
        double clicked=frequency(position);int freq=(int)Math.round(clicked),rate=0;
        try{
            for(int i=0;i<signals.length();i++){
                JSONObject s=signals.getJSONObject(i);
                if(clicked>=s.getInt("start_khz")&&clicked<=s.getInt("end_khz")){
                    freq=s.getInt("frequency_khz");rate=s.getInt("symbolrate_ksps");break;
                }
            }
        }catch(JSONException ignored){}
        listener.select(freq,rate,manual||rate==0);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event){
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)multiTouch=false;
        if(event.getPointerCount()>1&&!multiTouch){
            multiTouch=true;android.view.MotionEvent cancel=android.view.MotionEvent.obtain(event);
            cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);gestures.onTouchEvent(cancel);cancel.recycle();
        }
        scaling.onTouchEvent(event);if(!multiTouch&&!scaling.isInProgress())gestures.onTouchEvent(event);return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(Color.rgb(10,22,16));
        float top=24*density,bottom=getHeight()-25*density,height=Math.max(1,bottom-top);
        pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(density);pen.setColor(Color.rgb(49,72,55));
        for(int i=0;i<=4;i++){float y=top+height*i/4;canvas.drawLine(8*density,y,getWidth()-8*density,y,pen);}
        double step=span>4000?1000:span>1500?500:span>600?100:50;
        pen.setTextSize(10*density);
        for(double f=Math.ceil(low/step)*step;f<=low+span;f+=step){
            float px=x(f);pen.setStyle(Paint.Style.STROKE);pen.setColor(Color.rgb(49,72,55));canvas.drawLine(px,top,px,bottom,pen);
            pen.setStyle(Paint.Style.FILL);pen.setColor(Color.LTGRAY);canvas.drawText(String.format(Locale.US,step>=1000?"%.0f":"%.2f",f/1000),px+2*density,getHeight()-7*density,pen);
        }
        if(online&&bins.length>0){
            int[] sorted=bins.clone();Arrays.sort(sorted);float floor=Math.max(0,sorted[sorted.length/8]-1000),ceiling=Math.max(floor+12000,sorted[(sorted.length-1)*99/100]+2500);
            Path path=new Path();boolean started=false;
            for(int i=0;i<bins.length;i++){
                double f=10490500+(i+1)*9000.0/bins.length;if(f<low||f>low+span)continue;
                float px=x(f),py=bottom-height*Math.max(0,Math.min(1,(bins[i]-floor)/(ceiling-floor)));
                if(!started){path.moveTo(px,py);started=true;}else path.lineTo(px,py);
            }
            pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.5f*density);pen.setColor(Color.rgb(121,229,151));canvas.drawPath(path,pen);
            pen.setStyle(Paint.Style.FILL);pen.setTextSize(10*density);pen.setColor(Color.rgb(180,206,186));
            try{for(int i=0;i<signals.length();i++){
                JSONObject s=signals.getJSONObject(i);int f=s.getInt("frequency_khz");if(f<low||f>low+span)continue;
                canvas.drawText(s.getInt("symbolrate_ksps")+" kS",Math.min(getWidth()-60*density,x(f)),bottom-4*density,pen);
            }}catch(JSONException ignored){}
        }
        for(int ch=0;ch<2;ch++)if(tuned[ch]>=low&&tuned[ch]<=low+span){
            pen.setColor(ch==0?Color.rgb(121,229,151):Color.rgb(255,198,105));pen.setStrokeWidth(2*density);pen.setStyle(Paint.Style.STROKE);
            float px=x(tuned[ch]);canvas.drawLine(px,top,px,bottom,pen);pen.setStyle(Paint.Style.FILL);pen.setTextSize(11*density);
            String text="K"+(ch+1)+(locked[ch]?" "+calls[ch]:"");float width=pen.measureText(text);
            canvas.drawText(text,Math.max(8*density,Math.min(getWidth()-width-4*density,px+3*density)),top+(ch+1)*13*density,pen);
        }
        pen.setStyle(Paint.Style.FILL);pen.setTextSize(11*density);pen.setColor(Color.LTGRAY);
        canvas.drawText(online?"BATC / AMSAT-UK · Tippen: Signal · Halten: SR waehlen":"BATC-Spektrum wartet auf Daten",8*density,15*density,pen);
    }
}
