package de.dl2ghm.longmyndmobil;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import org.json.*;
import java.util.*;

/** Wideband spectrum with tapping, tuning and a two-second source selector. */
public class SpectrumView extends View {
    public interface Listener { void select(int channel,int frequencyKhz,int rateKsps,boolean chooseRate); }
    static final long SOURCE_HOLD_MS=2000;
    Listener listener;Runnable onAutomatic,onAdvance,onSource;
    final SpectrumTapModel taps;
    final Runnable commitTaps=this::commitPendingTaps;
    final Runnable chooseSource=this::chooseSourceAfterHold;
    final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    final GestureDetector gestures;final ScaleGestureDetector scaling;
    int[] bins=new int[0];JSONArray signals=new JSONArray();boolean online=false;
    int[] displayBins=bins;
    boolean localCurve, rtlCurve;
    int rtlCeilingDb;
    double low=10490500,span=9000;
    final double[] tuned={0,0};final String[] calls={"",""};final boolean[] locked={false,false};
    final int[] channelColors={Color.rgb(121,229,151),Color.rgb(255,198,105)};
    final SpectrumModel model=new SpectrumModel();
    final SpectrumModel.LocalDisplay localDisplay=new SpectrumModel.LocalDisplay();
    final long[] tunerReceived={-10000,-10000};
    long received=0;
    String unavailable="";
    boolean multiTouch=false;
    final float density;
    public SpectrumView(Context context){
        super(context);
        ViewConfiguration config=ViewConfiguration.get(context);taps=new SpectrumTapModel(ViewConfiguration.getDoubleTapTimeout(),ViewConfiguration.getLongPressTimeout(),config.getScaledTouchSlop(),config.getScaledDoubleTapSlop());
        density=getResources().getDisplayMetrics().density;
        setContentDescription("QO-100-Spektrum. Obere Hälfte Kanal 1, untere Hälfte Kanal 2. Signal antippen zum Abstimmen. Zwei Sekunden gedrückt halten für die Spektrumquelle. Zwei Finger zoomen. Doppeltipp: Automatik. Dreifachtipp: andere Stationen.");
        setOnLongClickListener(view->{if(onSource==null)return false;onSource.run();return true;});
        scaling=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){
                double anchor=frequency(d.getFocusX()),next=Math.max(250,Math.min(9000,span/d.getScaleFactor()));
                low=anchor-(anchor-low)*next/span;span=next;clamp();invalidate();return true;
            }
        });
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(android.view.MotionEvent e){return true;}
            @Override public boolean onSingleTapConfirmed(android.view.MotionEvent e){return true;}
            @Override public boolean onDoubleTap(android.view.MotionEvent e){return true;}
            @Override public boolean onScroll(android.view.MotionEvent a,android.view.MotionEvent b,float dx,float dy){
                if(!scaling.isInProgress()&&span<9000){low+=dx/Math.max(1,getWidth()-16*density)*span;clamp();invalidate();}return true;
            }
        });
        gestures.setIsLongpressEnabled(false);
    }
    void clamp(){low=Math.max(10490500,Math.min(10499500-span,low));}
    double frequency(float x){return low+(x-8*density)/Math.max(1,getWidth()-16*density)*span;}
    float x(double frequency){return 8*density+(float)((frequency-low)/span)*(getWidth()-16*density);}
    public void setData(JSONObject data,boolean local)throws JSONException{
        online=data.getBoolean("online");JSONArray array=data.getJSONArray("bins");
        unavailable=online?"":data.optString("detail","");
        bins=new int[array.length()];for(int i=0;i<bins.length;i++)bins[i]=array.getInt(i);
        signals=data.getJSONArray("signals");received=SystemClock.elapsedRealtime();
        rtlCurve=local&&online&&"RTL-SDR".equals(data.optString("source"));
        localCurve=local&&online&&!rtlCurve;
        if(localCurve)displayBins=localDisplay.update(bins,received);
        else{localDisplay.reset();displayBins=bins;}
        if(rtlCurve)rtlCeilingDb=SpectrumModel.RtlScale.ceilingDb(bins);
        int count=signals.length();int[] centers=new int[count],starts=new int[count],ends=new int[count];
        for(int i=0;i<count;i++){JSONObject signal=signals.getJSONObject(i);centers[i]=signal.getInt("frequency_khz");starts[i]=signal.getInt("start_khz");ends[i]=signal.getInt("end_khz");}
        model.update(bins,centers,starts,ends,received,online);
        rememberTuners();invalidate();
    }
    public void setTuner(int channel,int frequency,String call,boolean lock){tuned[channel]=frequency;calls[channel]=call;locked[channel]=lock;tunerReceived[channel]=SystemClock.elapsedRealtime();rememberTuners();invalidate();}
    void clearSource(){
        online=false;bins=new int[0];signals=new JSONArray();received=0;unavailable="";
        displayBins=bins;localCurve=false;rtlCurve=false;localDisplay.reset();
        model.labels.clear();
        model.update(bins,new int[0],new int[0],new int[0],0,false);
        invalidate();
    }
    void showUnavailable(String message){
        clearSource();unavailable=message;invalidate();
    }
    void rememberTuners(){
        if(!online||SystemClock.elapsedRealtime()-received>4000)return;
        for(int ch=0;ch<2;ch++)if(locked[ch]&&SystemClock.elapsedRealtime()-tunerReceived[ch]<=4000)model.remember(ch,tuned[ch],calls[ch]);
    }
    void tap(float position,float vertical,boolean manual){
        if(listener==null||!online||SystemClock.elapsedRealtime()-received>4000)return;
        double clicked=frequency(position);int freq=(int)Math.round(clicked),rate=0;
        // A small screen-space margin catches the flank and near misses at all zoom levels.
        double tolerance=Math.min(180,span*10*density/Math.max(1,getWidth()-16*density));
        SpectrumModel.Peak hit=model.pick(clicked,tolerance);
        if(hit!=null){
            freq=(int)Math.round(hit.center);
            try{for(int i=0;i<signals.length();i++){
                JSONObject signal=signals.getJSONObject(i);
                if(signal.getInt("frequency_khz")==freq){rate=signal.getInt("symbolrate_ksps");break;}
            }}catch(JSONException ignored){}
        }
        listener.select(vertical<getHeight()/2f?0:1,freq,rate,manual||rate==0);
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event){
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)multiTouch=false;
        if(event.getPointerCount()>1&&!multiTouch){
            multiTouch=true;cancelTouches();android.view.MotionEvent cancel=android.view.MotionEvent.obtain(event);
            cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);gestures.onTouchEvent(cancel);cancel.recycle();
        }
        if(!multiTouch){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                removeCallbacks(commitTaps);removeCallbacks(chooseSource);
                taps.down(event.getEventTime(),event.getX(),event.getY());
                postDelayed(chooseSource,Math.max(0,event.getEventTime()+SOURCE_HOLD_MS-SystemClock.uptimeMillis()));
            }else if(action==MotionEvent.ACTION_MOVE){
                taps.move(event.getX(),event.getY());
                if(!taps.pressed)removeCallbacks(chooseSource);
            }else if(action==MotionEvent.ACTION_UP){
                removeCallbacks(chooseSource);
                taps.move(event.getX(),event.getY());
                long now=event.getEventTime();
                if(taps.hold(now,SOURCE_HOLD_MS))performLongClick();
                else if(taps.hold(now,taps.maxPress))tap(taps.downX,taps.downY,true);
                else{
                    int result=taps.up(now,event.getX(),event.getY());
                    if(result==3)dispatchTaps(3);
                    else if(taps.count>0)postDelayed(commitTaps,ViewConfiguration.getDoubleTapTimeout()+1);
                }
            }else if(action==MotionEvent.ACTION_CANCEL)cancelTouches();
        }
        scaling.onTouchEvent(event);if(!multiTouch&&!scaling.isInProgress())gestures.onTouchEvent(event);return true;
    }
    void commitPendingTaps(){dispatchTaps(taps.commit(SystemClock.uptimeMillis()));}
    void dispatchTaps(int count){
        if(count==1){tap(taps.x,taps.y,false);performClick();}
        else if(count==2){low=10490500;span=9000;invalidate();if(onAutomatic!=null)onAutomatic.run();}
        else if(count==3){if(onAdvance!=null)onAdvance.run();}
    }
    void chooseSourceAfterHold(){if(taps.hold(SystemClock.uptimeMillis(),SOURCE_HOLD_MS))performLongClick();}
    void cancelTouches(){removeCallbacks(chooseSource);removeCallbacks(commitTaps);taps.cancel();}
    @Override protected void onDetachedFromWindow(){cancelTouches();super.onDetachedFromWindow();}
    @Override public boolean performClick(){super.performClick();return true;}
    float plotPosition(float level,float floor,float ceiling){
        if(rtlCurve)return SpectrumModel.RtlScale.position(level,rtlCeilingDb);
        return Math.max(0,Math.min(1,(level-floor)/(ceiling-floor)));
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(Color.rgb(10,22,16));
        float top=6*density,bottom=getHeight()-6*density,height=Math.max(1,bottom-top);
        float middle=getHeight()/2f;
        Path spectrumFill=null,spectrumLine=null;
        float floor=0,ceiling=1;
        pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(density);pen.setColor(Color.rgb(49,72,55));
        if(rtlCurve){
            for(int db=0;db<=rtlCeilingDb;db+=3){
                float y=bottom-height*plotPosition(SpectrumModel.RtlScale.binAtDb(db),floor,ceiling);
                canvas.drawLine(8*density,y,getWidth()-8*density,y,pen);
            }
        }else{
            for(int i=0;i<=4;i++){float y=top+height*i/4;canvas.drawLine(8*density,y,getWidth()-8*density,y,pen);}
        }
        double step=span>4000?1000:span>1500?500:span>600?100:50;
        pen.setTextSize(10*density);
        for(double f=Math.ceil(low/step)*step;f<=low+span;f+=step){
            float px=x(f);pen.setStyle(Paint.Style.STROKE);pen.setColor(Color.rgb(49,72,55));canvas.drawLine(px,top,px,bottom,pen);
        }
        if(online&&bins.length>0){
            if(rtlCurve){
                floor=SpectrumModel.RtlScale.PLOT_FLOOR;
            }else if(localCurve){
                floor=SpectrumModel.LocalDisplay.PLOT_FLOOR;
                ceiling=localDisplay.ceiling();
            }else{
                int[] sorted=bins.clone();Arrays.sort(sorted);
                floor=Math.max(0,sorted[sorted.length/8]-1000);
                ceiling=Math.max(floor+12000,sorted[(sorted.length-1)*99/100]+2500);
            }
            Path path=new Path();boolean started=false;float firstX=0,lastX=0;
            for(int i=0;i<bins.length;i++){
                double f=10490500+(i+1)*9000.0/bins.length;if(f<low||f>low+span)continue;
                float px=x(f),py=bottom-height*plotPosition(displayBins[i],floor,ceiling);
                if(!started){path.moveTo(px,py);firstX=px;started=true;}else path.lineTo(px,py);lastX=px;
            }
            if(started){
                Path fill=new Path(path);fill.lineTo(lastX,bottom);fill.lineTo(firstX,bottom);fill.close();
                spectrumFill=fill;spectrumLine=path;
                pen.setStyle(Paint.Style.FILL);
                if(rtlCurve)pen.setColor(0x60287E50);
                else pen.setShader(new LinearGradient(0,top,0,bottom,0xA09DA7A0,0x304D5851,Shader.TileMode.CLAMP));
                canvas.drawPath(fill,pen);pen.setShader(null);
            }
            pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.5f*density);pen.setColor(rtlCurve?Color.rgb(135,225,171):Color.rgb(171,188,176));canvas.drawPath(path,pen);
            pen.setStyle(Paint.Style.FILL);pen.setTextSize(10*density);pen.setColor(Color.rgb(180,206,186));
            try{for(int i=0;i<signals.length();i++){
                JSONObject s=signals.getJSONObject(i);int f=s.getInt("frequency_khz");if(f<low||f>low+span)continue;
                canvas.drawText(s.getInt("symbolrate_ksps")+" kS",Math.min(getWidth()-60*density,x(f)),bottom-4*density,pen);
            }}catch(JSONException ignored){}
        }
        pen.setColor(0x809DBCA7);pen.setStrokeWidth(density);canvas.drawLine(8*density,getHeight()/2f,getWidth()-8*density,getHeight()/2f,pen);
        pen.setStyle(Paint.Style.FILL);pen.setTextSize(10*density);pen.setColor(Color.rgb(121,229,151));canvas.drawText("K1",8*density,top+12*density,pen);
        pen.setColor(Color.rgb(255,198,105));canvas.drawText("K2",8*density,getHeight()/2f+13*density,pen);
        if(!online&&!unavailable.isEmpty()){
            pen.setColor(Color.rgb(180,206,186));pen.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(unavailable,getWidth()/2f,middle-6*density,pen);
            pen.setTextAlign(Paint.Align.LEFT);
        }
        boolean fresh=online&&bins.length>=3&&SystemClock.elapsedRealtime()-received<=4000;
        for(int ch=0;ch<2;ch++)if(tuned[ch]>=low&&tuned[ch]<=low+span){
            int color=channelColors[ch];
            float regionTop=ch==0?top:middle,regionBottom=ch==0?middle:bottom;
            float px=x(tuned[ch]),signalLeft=px,signalRight=px,signalY=bottom;
            SpectrumModel.Peak selected=null;
            boolean matched=false;
            if(fresh&&spectrumFill!=null){
                SpectrumModel.Peak signal=model.at(tuned[ch]);
                if(signal!=null){
                    selected=signal;px=x(signal.visualCenter());
                    signalLeft=Math.max(8*density,x(signal.leftAt(floor)));
                    signalRight=Math.min(getWidth()-8*density,x(signal.rightAt(floor)));
                    int index=SpectrumModel.index(signal.visualCenter(),bins.length);
                    signalY=bottom-height*plotPosition(displayBins[index],floor,ceiling);
                    matched=true;
                }
            }
            float visibleTop=Math.max(regionTop,signalY);
            boolean area=matched&&signalRight>signalLeft&&regionBottom-visibleTop>2*density;
            if(area){
                // Clip the actual spectrum silhouette to this signal AND this channel's half.
                int save=canvas.save();canvas.clipRect(signalLeft,regionTop,signalRight,regionBottom);
                // Below the shoulders, colour only inside the straight flank extensions.
                float shoulderY=bottom-height*plotPosition((float)selected.shoulderLevel,floor,ceiling);
                Path boundary=new Path();boundary.moveTo(x(selected.leftAt(floor)),bottom);
                boundary.lineTo(x(selected.leftAt(selected.shoulderLevel)),shoulderY);
                boundary.lineTo(x(selected.leftAt(selected.shoulderLevel)),top);
                boundary.lineTo(x(selected.rightAt(selected.shoulderLevel)),top);
                boundary.lineTo(x(selected.rightAt(selected.shoulderLevel)),shoulderY);
                boundary.lineTo(x(selected.rightAt(floor)),bottom);boundary.close();
                canvas.clipPath(boundary);
                pen.setShader(null);pen.setStyle(Paint.Style.FILL);pen.setColor((color&0x00FFFFFF)|0xA0000000);
                canvas.drawPath(spectrumFill,pen);
                pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.5f*density);pen.setColor(color);
                canvas.drawPath(spectrumLine,pen);canvas.restoreToCount(save);
            }
            // No full-height marker: a short stroke always sits on the tuned signal's centre.
            float badgeY=area?(visibleTop+regionBottom)/2:(regionTop+regionBottom)/2;
            float radius=area?Math.min(11*density,Math.min((signalRight-signalLeft)*0.36f,(regionBottom-visibleTop)*0.36f)):0;
            int save=canvas.save();canvas.clipRect(8*density,regionTop,getWidth()-8*density,regionBottom);
            pen.setShader(null);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(2*density);pen.setColor(color);
            canvas.drawLine(px,Math.max(regionTop,badgeY-12*density),px,Math.min(regionBottom,badgeY+12*density),pen);
            if(radius>=6*density){
                pen.setStyle(Paint.Style.FILL);pen.setColor(color);canvas.drawCircle(px,badgeY,radius,pen);
                pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(density);pen.setColor(Color.rgb(10,22,16));canvas.drawCircle(px,badgeY,radius,pen);
                pen.setStyle(Paint.Style.FILL);pen.setTextSize(radius*1.35f);pen.setFakeBoldText(true);
                String number=Integer.toString(ch+1);
                Paint.FontMetrics metrics=pen.getFontMetrics();
                canvas.drawText(number,px-pen.measureText(number)/2,badgeY-(metrics.ascent+metrics.descent)/2,pen);
                pen.setFakeBoldText(false);
            }
            canvas.restoreToCount(save);
        }
        // Calls belong to a signal, not to the currently selected tuning position.
        if(fresh)for(SpectrumModel.Label label:model.labels){
            SpectrumModel.Peak signal=label.peak;
            if(signal.visualCenter()<low||signal.visualCenter()>low+span)continue;
            int index=SpectrumModel.index(signal.visualCenter(),bins.length);
            float py=bottom-height*plotPosition(displayBins[index],floor,ceiling);
            for(int ch=0;ch<2;ch++){
                String text=label.calls[ch];if(text.isEmpty())continue;
                float regionTop=ch==0?top:middle,regionBottom=ch==0?middle:bottom;
                float peakTop=Math.max(regionTop,py);
                float ty=Math.max(regionTop+12*density,Math.min(regionBottom-4*density,
                    peakTop<regionBottom?peakTop-4*density:(regionTop+regionBottom)/2-15*density));
                pen.setStyle(Paint.Style.FILL);pen.setTextSize(11*density);
                float width=pen.measureText(text),px=x(signal.visualCenter());
                float tx=Math.max(8*density,Math.min(getWidth()-width-8*density,px-width/2));
                int save=canvas.save();canvas.clipRect(8*density,regionTop,getWidth()-8*density,regionBottom);
                pen.setColor(0xD0101914);canvas.drawRect(tx-2*density,ty-11*density,tx+width+2*density,ty+2*density,pen);
                pen.setColor(channelColors[ch]);canvas.drawText(text,tx,ty,pen);canvas.restoreToCount(save);
            }
        }
        pen.setStyle(Paint.Style.FILL);pen.setTextSize(11*density);pen.setColor(Color.LTGRAY);
        if(!online)canvas.drawText("Spektrum wartet auf Daten",8*density,15*density,pen);
    }
}
