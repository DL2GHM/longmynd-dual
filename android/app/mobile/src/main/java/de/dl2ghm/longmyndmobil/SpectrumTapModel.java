package de.dl2ghm.longmyndmobil;

/** Delay singles/doubles until the next tap is ruled out; triples commit on release. */
final class SpectrumTapModel {
    final long timeout,maxPress;final float tapSlop,doubleSlop;
    int count=0;long lastUp=-100000,downAt;float anchorX,anchorY,downX,downY,x,y;boolean pressed=false;
    SpectrumTapModel(long timeout,long maxPress,float tapSlop,float doubleSlop){this.timeout=timeout;this.maxPress=maxPress;this.tapSlop=tapSlop;this.doubleSlop=doubleSlop;}
    void down(long now,float px,float py){
        if(now-lastUp>timeout||Math.hypot(px-anchorX,py-anchorY)>doubleSlop)count=0;
        if(count==0){anchorX=px;anchorY=py;}
        pressed=true;downAt=now;downX=px;downY=py;
    }
    void move(float px,float py){if(pressed&&Math.hypot(px-downX,py-downY)>tapSlop)cancel();}
    boolean hold(long now,long duration){
        if(!pressed||now-downAt<duration)return false;
        cancel();return true;
    }
    int up(long now,float px,float py){
        if(!pressed)return 0;
        move(px,py);if(!pressed||now-downAt>=maxPress){cancel();return 0;}
        pressed=false;x=px;y=py;lastUp=now;count++;
        if(count==3){count=0;return 3;}return 0;
    }
    int commit(long now){if(pressed||count==0||now-lastUp<timeout)return 0;int result=count;count=0;return result;}
    void cancel(){pressed=false;count=0;}
}
