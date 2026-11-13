package de.dl2ghm.longmyndmobil;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import org.videolan.libvlc.util.VLCVideoLayout;

/** Paints opaque corner cutouts above VLC's texture, then its matching contour. */
final class RoundedVideoLayout extends VLCVideoLayout {
    final Paint maskPaint=new Paint(Paint.ANTI_ALIAS_FLAG),borderPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path corners=new Path();final RectF bounds=new RectF(),contour=new RectF();
    final float radius,stroke;boolean rounded=true;
    RoundedVideoLayout(Context context,int surround,int border,float radius,float stroke){
        super(context);this.radius=radius;this.stroke=stroke;
        setWillNotDraw(false);setBackgroundColor(android.graphics.Color.BLACK);
        // Parent-outline clipping did not constrain this device's VLC texture.
        // The cutouts must themselves remain drawable outside the rounded contour.
        setClipToOutline(false);
        maskPaint.setColor(surround);maskPaint.setStyle(Paint.Style.FILL);
        borderPaint.setColor(border);borderPaint.setStyle(Paint.Style.STROKE);borderPaint.setStrokeWidth(stroke);
    }
    void setRounded(boolean enabled){if(rounded!=enabled){rounded=enabled;invalidate();}}
    @Override public void onDrawForeground(Canvas canvas){
        super.onDrawForeground(canvas);
        if(!rounded||getWidth()==0||getHeight()==0)return;
        float r=Math.min(radius,Math.min(getWidth(),getHeight())/2f);
        bounds.set(0,0,getWidth(),getHeight());
        corners.reset();corners.setFillType(Path.FillType.EVEN_ODD);
        corners.addRect(bounds,Path.Direction.CW);corners.addRoundRect(bounds,r,r,Path.Direction.CW);
        canvas.drawPath(corners,maskPaint);
        float inset=stroke/2f;contour.set(inset,inset,getWidth()-inset,getHeight()-inset);
        canvas.drawRoundRect(contour,Math.max(0,r-inset),Math.max(0,r-inset),borderPaint);
    }
}
