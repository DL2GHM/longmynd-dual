package de.dl2ghm.longmyndmobil;

/** Actual letterboxed picture bounds; overlays must follow these, not the panel. */
final class VideoLayoutModel {
    static final class Frame {
        final float left,top,width,height;
        Frame(float l,float t,float w,float h){left=l;top=t;width=w;height=h;}
    }
    static Frame fit(float width,float height,float aspect){
        if(width<=0||height<=0)return new Frame(0,0,0,0);
        if(!(aspect>0)||Float.isInfinite(aspect))aspect=16f/9;
        float w=Math.min(width,height*aspect),h=w/aspect;
        return new Frame((width-w)/2,(height-h)/2,w,h);
    }
    static int portraitVideoHeight(int available,int width,float[] aspects,int minimumChat){
        return portraitVideoHeight(available,width,aspects,minimumChat,0);
    }
    static Frame fitInset(float width,float height,float aspect,int inset){
        Frame f=fit(Math.max(0,width-2*inset),Math.max(0,height-2*inset),aspect);
        return new Frame(f.left+inset,f.top+inset,f.width,f.height);
    }
    static int portraitVideoHeight(int available,int width,float[] aspects,int minimumChat,int gap){
        return portraitVideoHeight(available,width,aspects,minimumChat,gap,0);
    }
    static int portraitVideoHeight(int available,int width,float[] aspects,int minimumChat,int gap,int inset){
        float natural=Math.max(0,aspects.length-1)*gap;
        for(float aspect:aspects)natural+=Math.max(0,width-2*inset)/(aspect>0?aspect:16f/9)+2*inset;
        return Math.max(0,Math.min(Math.round(natural),available-Math.max(0,minimumChat)));
    }
    static int landscapeVideoHeight(int available,int width,float[] aspects,int minimumChat,int gap,int inset){
        if(aspects.length==0)return 0;
        float column=Math.max(0,width-Math.max(0,aspects.length-1)*gap)/(float)aspects.length;
        float natural=0;
        for(float aspect:aspects)natural=Math.max(natural,Math.max(0,column-2*inset)/(aspect>0?aspect:16f/9)+2*inset);
        return Math.max(0,Math.min(Math.round(natural),available-Math.max(0,minimumChat)));
    }
    static int selection(int fullscreen,int preferred,boolean composing){
        return fullscreen>=0?fullscreen+1:composing?1:preferred;
    }
    static int playbackSelection(int displayed,int preferred,boolean composing){
        // Both audio channels stay active during chat when K2 belonged to the previous view.
        return composing&&preferred!=1?0:displayed;
    }
    static float scale(float pictureWidth,float density){return Math.max(.65f,Math.min(1.35f,pictureWidth/(480*density)));}
}
