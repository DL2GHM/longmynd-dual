package de.dl2ghm.longmyndmobil;

/** Fit a whole number of line cells without making the text substantially smaller. */
final class ChatLayoutModel {
    static final class Fit {
        final float textSize;final int lineHeight,bottomExtra;
        Fit(float size,int height,int extra){textSize=size;lineHeight=height;bottomExtra=extra;}
    }
    static Fit fit(int available,float baseSize,int fontHeight,int spacing){
        int natural=Math.max(1,fontHeight+spacing);
        if(available<=0)return new Fit(baseSize,natural,0);
        int rows=Math.max(1,Math.round(available/(float)natural));
        int target=Math.max(1,available/rows);
        float scale=Math.max(.9f,Math.min(1.1f,(target-spacing)/(float)Math.max(1,fontHeight)));
        return new Fit(baseSize*scale,target,available%target);
    }
}
