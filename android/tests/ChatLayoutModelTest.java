package de.dl2ghm.longmyndmobil;
public class ChatLayoutModelTest {
    public static void main(String[] args){
        for(int height=40;height<1000;height++){
            ChatLayoutModel.Fit f=ChatLayoutModel.fit(height,24,28,2);
            if((height-f.bottomExtra)%f.lineHeight!=0)throw new AssertionError("partial line at "+height);
            if(f.textSize<21.59||f.textSize>26.41)throw new AssertionError("font adjustment too large");
            if(f.bottomExtra<0||f.bottomExtra>=f.lineHeight)throw new AssertionError("invalid padding");
        }
        ChatLayoutModel.Fit f=ChatLayoutModel.fit(300,24,28,2);
        if(f.lineHeight!=30||f.bottomExtra!=0||f.textSize!=24)throw new AssertionError("exact fit changes size");
        f=ChatLayoutModel.fit(0,24,28,2);if(f.lineHeight!=30)throw new AssertionError("empty viewport");
        System.out.println("ChatLayoutModelTest OK");
    }
}
