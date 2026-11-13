package de.dl2ghm.longmyndmobil;
public class VideoLayoutModelTest {
    static void near(float a,float b){if(Math.abs(a-b)>.01)throw new AssertionError(a+" != "+b);}
    public static void main(String[] args){
        VideoLayoutModel.Frame f=VideoLayoutModel.fit(1000,1000,16f/9);near(f.left,0);near(f.top,218.75f);near(f.width,1000);near(f.height,562.5f);
        f=VideoLayoutModel.fit(1000,400,16f/9);near(f.top,0);near(f.height,400);near(f.left,(1000-400*16f/9)/2);
        f=VideoLayoutModel.fit(400,800,9f/16);near(f.left,0);near(f.top,(800-400*16f/9)/2);
        f=VideoLayoutModel.fit(800,600,4f/3);near(f.width,800);near(f.top,0);
        f=VideoLayoutModel.fit(0,600,0);near(f.width,0);
        near(VideoLayoutModel.scale(480,1),1);near(VideoLayoutModel.scale(960,2),1);
        near(VideoLayoutModel.scale(10,1),.65f);near(VideoLayoutModel.scale(2000,1),1.35f);
        for(int preferred=0;preferred<=2;preferred++){
            int chatVideo=VideoLayoutModel.selection(-1,preferred,true);
            if(chatVideo!=1)throw new AssertionError("typing always shows K1");
            if(VideoLayoutModel.playbackSelection(chatVideo,preferred,true)!=(preferred==1?1:0))
                throw new AssertionError("typing keeps the previously active K2 audio");
            int normalVideo=VideoLayoutModel.selection(-1,preferred,false);
            if(normalVideo!=preferred)throw new AssertionError("closing keyboard restores the selected view");
            if(VideoLayoutModel.playbackSelection(normalVideo,preferred,false)!=preferred)
                throw new AssertionError("closing keyboard restores the selected audio channels");
        }
        if(VideoLayoutModel.selection(1,0,true)!=2)throw new AssertionError("fullscreen wins over keyboard and saved mode");
        near(VideoLayoutModel.portraitVideoHeight(700,400,new float[]{16f/9,16f/9},45),450);
        near(VideoLayoutModel.portraitVideoHeight(400,400,new float[]{16f/9,16f/9},45),355);
        near(VideoLayoutModel.portraitVideoHeight(700,400,new float[]{16f/9},45),225);
        near(VideoLayoutModel.portraitVideoHeight(800,400,new float[]{4f/3,16f/9},45),525);
        near(VideoLayoutModel.portraitVideoHeight(20,400,new float[]{16f/9},45),0);
        near(VideoLayoutModel.portraitVideoHeight(700,400,new float[]{16f/9,16f/9},45,7),457);
        near(VideoLayoutModel.portraitVideoHeight(700,400,new float[]{16f/9},45,7),225);
        near(VideoLayoutModel.portraitVideoHeight(400,400,new float[]{16f/9,16f/9},45,7),355);
        f=VideoLayoutModel.fitInset(404,229,16f/9,2);near(f.left,2);near(f.top,2);near(f.width,400);near(f.height,225);
        near(VideoLayoutModel.portraitVideoHeight(700,404,new float[]{16f/9,16f/9},45,7,2),465);
        f=VideoLayoutModel.fitInset(804,454,4f/3,2);near(f.left,102);near(f.top,2);near(f.width,600);near(f.height,450);
        near(VideoLayoutModel.landscapeVideoHeight(700,1280,new float[]{16f/9,16f/9},45,8,2),360);
        near(VideoLayoutModel.landscapeVideoHeight(700,1280,new float[]{4f/3,16f/9},45,8,2),478);
        near(VideoLayoutModel.landscapeVideoHeight(300,1280,new float[]{16f/9,16f/9},45,8,2),255);
        near(VideoLayoutModel.landscapeVideoHeight(1000,800,new float[]{16f/9},45,8,2),452);
        near(VideoLayoutModel.landscapeVideoHeight(700,1280,new float[]{},45,8,2),0);
        // Keyboard viewport after reserving the entry row, padding and dividers.
        int typingHeight=390,typingWidth=404,historyMinimum=54;
        int videoHeight=VideoLayoutModel.portraitVideoHeight(typingHeight,typingWidth,new float[]{16f/9},historyMinimum,0,2);
        near(videoHeight,229);near(typingHeight-videoHeight,161);
        f=VideoLayoutModel.fitInset(typingWidth,videoHeight,16f/9,2);
        near(f.top,2);near(f.height,225);
        videoHeight=VideoLayoutModel.landscapeVideoHeight(145,800,new float[]{16f/9},38,0,2);
        near(videoHeight,107);near(145-videoHeight,38);
        System.out.println("VideoLayoutModelTest OK");
    }
}
