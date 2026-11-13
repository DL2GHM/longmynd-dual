package de.dl2ghm.longmyndmobil;

import android.content.Context;
import android.graphics.Paint;
import android.text.Layout;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

/** Whole visible text rows, while retaining the user's choice to follow the tail. */
public final class ChatScrollView extends ScrollView {
    private boolean following=true,layingOut=false,touching=false;
    private final float baseSize;
    private final int basePadding,baseSpacing;
    private final Runnable settle=this::snapToLine;
    public ChatScrollView(Context context){
        super(context);baseSize=12*getResources().getDisplayMetrics().scaledDensity;
        float density=getResources().getDisplayMetrics().density;
        basePadding=Math.round(4*density);baseSpacing=Math.round(density);setClipToPadding(true);
    }
    int preferredLineHeight(){
        Paint p=new Paint();p.setTypeface(android.graphics.Typeface.MONOSPACE);p.setTextSize(baseSize);
        return p.getFontMetricsInt(null)+baseSpacing;
    }
    int preferredPadding(){return 2*basePadding;}
    private void fitText(){
        if(!(getChildAt(0) instanceof TextView)||getHeight()<=2*basePadding)return;
        TextView text=(TextView)getChildAt(0);Paint base=new Paint(text.getPaint());base.setTextSize(baseSize);
        int available=getHeight()-basePadding*2;
        ChatLayoutModel.Fit fit=ChatLayoutModel.fit(available,baseSize,base.getFontMetricsInt(null),baseSpacing);
        if(Math.abs(text.getTextSize()-fit.textSize)>.05f)text.setTextSize(TypedValue.COMPLEX_UNIT_PX,fit.textSize);
        int natural=text.getPaint().getFontMetricsInt(null),line=Math.max(natural,fit.lineHeight);
        int spacing=line-natural;
        if(Math.abs(text.getLineSpacingExtra()-spacing)>.01f||text.getLineSpacingMultiplier()!=1f)text.setLineSpacing(spacing,1f);
        int remainder=available%line;
        if(getPaddingBottom()!=basePadding+remainder)setPadding(basePadding,basePadding,basePadding,basePadding+remainder);
        Layout layout=text.getLayout();
        // StaticLayout omits the final line's extra spacing. Fill that cell too,
        // so tail-following starts and ends at complete rows rather than half a row.
        if(layout!=null&&(layout.getLineCount()==1||layout.getLineTop(1)-layout.getLineTop(0)==line)){
            int padding=Math.max(0,layout.getLineCount()*line-layout.getHeight());
            if(text.getPaddingBottom()!=padding)text.setPadding(text.getPaddingLeft(),0,text.getPaddingRight(),padding);
        }
    }
    private void snapToLine(){
        if(touching||following||!(getChildAt(0) instanceof TextView))return;
        TextView text=(TextView)getChildAt(0);int line=Math.max(1,text.getLineHeight());
        int bottom=Math.max(0,text.getHeight()-getHeight()+getPaddingTop()+getPaddingBottom());
        int y=Math.min(bottom,Math.round(getScrollY()/(float)line)*line);
        layingOut=true;try{scrollTo(0,y);}finally{layingOut=false;}
        if(y>=bottom-1)following=true;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){touching=true;removeCallbacks(settle);}
        boolean result=super.dispatchTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){touching=false;removeCallbacks(settle);postDelayed(settle,150);}
        return result;
    }
    @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){
        super.onScrollChanged(x,y,oldX,oldY);
        if(!layingOut){
            View child=getChildAt(0);
            int bottom=child==null?0:Math.max(0,child.getHeight()-getHeight()+getPaddingTop()+getPaddingBottom());
            following=y>=bottom-1;
            removeCallbacks(settle);if(!touching&&!following)postDelayed(settle,150);
        }
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        layingOut=true;
        try{
            super.onLayout(changed,left,top,right,bottom);fitText();
            if(following){View child=getChildAt(0);if(child!=null)scrollTo(0,Math.max(0,child.getHeight()-getHeight()+getPaddingTop()+getPaddingBottom()));}
            else if(!touching){removeCallbacks(settle);postDelayed(settle,150);}
        }finally{layingOut=false;}
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(settle);super.onDetachedFromWindow();}
}
