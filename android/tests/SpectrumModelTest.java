package de.dl2ghm.longmyndmobil;
import java.util.*;

/** Run with plain Java 17; exercises signal lifetime and separation, not Android stubs. */
public class SpectrumModelTest {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static int f(int bin){return (int)SpectrumModel.frequency(bin,900);}
    static final int[] CENTERS={f(199),f(449)},STARTS={f(175),f(430)},ENDS={f(224),f(469)};
    static int[] frame(){
        int[] bins=new int[900];Arrays.fill(bins,10000);
        mountain(bins,155,175,224,245);mountain(bins,410,430,469,490);return bins;
    }
    static void mountain(int[] bins,int left,int topLeft,int topRight,int right){
        for(int i=left;i<=right;i++){
            double amplitude=Math.min(1,Math.min((i-left)/(double)(topLeft-left),(right-i)/(double)(right-topRight)));
            bins[i]=10000+(int)(30000*amplitude);
        }
    }
    static void feed(SpectrumModel m,long t){m.update(frame(),CENTERS,STARTS,ENDS,t,true);}
    static void localDisplay(){
        SpectrumModel.LocalDisplay display=new SpectrumModel.LocalDisplay();
        int[] raw=new int[922], filtered=null;
        for(int frame=0;frame<30;frame++){
            for(int i=0;i<raw.length;i++){
                int noise=9000+3*i+(i%2==0 ? 1 : -1)*(frame%2==0 ? 900 : -900);
                boolean beacon=i>=15&&i<185, narrow=i>=790&&i<795;
                raw[i]=noise+(beacon||narrow ? 5500 : 0);
            }
            filtered=display.update(raw,1000+350L*frame);
        }
        check(Math.abs(filtered[302]-filtered[303])<350,"local FFT average reduces alternating noise");
        check(Math.abs(filtered[744]-filtered[302])<500,"local floor removes the slope");
        check(filtered[90]>filtered[302]+4000,"wide beacon remains visible");
        check(filtered[792]>filtered[788]+4000&&filtered[798]<filtered[792]-4000,"narrow peak and flanks remain distinct");
        check(raw[792]>14000,"display filtering does not mutate detection input");
        Arrays.fill(raw,9000);
        filtered=display.update(raw,14500);
        check(filtered[90]==9000&&filtered[792]==9000,"a gap starts a fresh average");
        display.reset();
        raw[792]=14500;
        check(display.update(raw,14600)[792]==14500,"explicit reset removes old source history");
        for(int i=200;i<240;i++)raw[i]=49000;
        filtered=display.update(raw,14950);
        check(filtered[220]>12000&&filtered[220]<14000,"local trace takes a measured step after one frame");
        check(display.ceiling()==20000,"brief fluctuation leaves the local scale stable");
        for(int frame=1;frame<=35;frame++)display.update(raw,14950+350L*frame);
        float high=display.ceiling();
        check(high>40000&&high<53000,"sustained strong signal increases headroom");
        Arrays.fill(raw,9000);
        display.update(raw,14950+350L*36);
        check(display.ceiling()>high*.95,"local scale returns gradually");
        display.reset();
        check(display.ceiling()==20000,"switching sources clears local headroom");
    }
    static void rtlScale(){
        int[] dbs={-9,-6,-3,0,3,9,15};
        int[] bins=Arrays.stream(dbs).map(SpectrumModel.RtlScale::binAtDb).toArray();
        float[] fractions={0,0,.04f,.08f,.264f,.632f,1};
        for(int i=0;i<bins.length;i++){
            check(Math.abs(SpectrumModel.RtlScale.position(bins[i],15)-fractions[i])<.0001,
                  "RTL vertical positions match the browser at "+dbs[i]+" dB");
        }
        check(SpectrumModel.RtlScale.ceilingDb(new int[]{9000,21500})==15,"normal beacon uses the browser's 15 dB range");
        check(SpectrumModel.RtlScale.ceilingDb(new int[]{9000,27750})==18,"strong peak expands the range in 3 dB steps");
        check(SpectrumModel.RtlScale.PLOT_FLOOR==1500,"RTL overlays meet the bottom of the plotted range");
        check(bins[3]==9000&&bins[5]==20250,"RTL display leaves the server levels intact");
    }
    public static void main(String[] args){
        localDisplay();
        rtlScale();
        SpectrumModel m=new SpectrumModel();feed(m,0);
        check(m.peaks.size()==2,"two separate signals");
        SpectrumModel.Peak first=m.peaks.get(0),second=m.peaks.get(1);
        check(first.left<f(160)&&first.right>f(240),"colour includes both flanks down to noise");
        check(first.right<second.left,"neighbouring signals not coloured together");
        check(m.at(f(165))==first,"lower flank belongs to selected signal");
        SpectrumModel.Peak skewed=SpectrumModel.footprints(frame(),new int[]{f(180)},new int[]{f(175)},new int[]{f(224)}).get(0);
        check(Math.abs(skewed.visualCenter()-f(200))<10,"label and marker center follow both feet despite off-centre tuning");
        check(Math.abs(skewed.visualCenter()-skewed.center)>100,"display centre is independent of tuning frequency");
        m.remember(0,CENTERS[0],"A71A");feed(m,100);
        m.remember(0,CENTERS[1],"OTHER");
        check(m.labels.size()==2,"retuning retains previous callsign");
        check(m.labels.get(0).calls[0].equals("A71A"),"old mountain keeps call");
        m.remember(1,CENTERS[0],"A71A");
        check(m.labels.get(0).calls[1].equals("A71A"),"same signal on both halves");
        m.remember(0,CENTERS[0],"");
        check(m.labels.get(0).calls[0].equals("A71A"),"temporary missing service doesn't erase name");
        int[] c={CENTERS[1]},s={STARTS[1]},e={ENDS[1]};
        m.update(frame(),c,s,e,200,true);
        check(m.labels.size()==2,"one missing frame doesn't erase call");
        feed(m,1000);check(m.labels.size()==2,"brief detection dropout recovers");
        m.update(frame(),c,s,e,1200,true);m.update(frame(),c,s,e,3200,true);
        check(m.labels.size()==1&&m.labels.get(0).calls[0].equals("OTHER"),"switch-off removes only disappeared signal");
        feed(m,3300);check(m.labels.size()==1,"new transmission doesn't inherit erased name");
        m.remember(0,CENTERS[0],"NEW");
        m.update(new int[0],new int[0],new int[0],new int[0],3400,false);
        feed(m,20000);check(m.labels.size()==2,"feed outage is not a transmitter shutdown");
        int[] drift={CENTERS[0]+10,CENTERS[1]-10};
        m.update(frame(),drift,STARTS,ENDS,20100,true);
        check(m.labels.size()==2&&m.labels.get(1).missingSince<0,"small frequency jitter retains identity");
        // Adjacent signals: expansions meet at the gap midpoint at most.
        int[] close=frame();mountain(close,250,270,309,330);
        List<SpectrumModel.Peak> peaks=SpectrumModel.footprints(close,new int[]{f(199),f(289)},new int[]{f(175),f(270)},new int[]{f(224),f(309)});
        check(peaks.get(0).right<=peaks.get(1).left,"close neighbours stay separate");
        check(SpectrumModel.footprints(new int[0],new int[0],new int[0],new int[0]).isEmpty(),"empty spectrum handled");
        // Wide low grass outside the true slopes must not broaden the highlight.
        int[] noisy=frame();
        for(int i=120;i<155;i++)noisy[i]=14000;
        for(int i=246;i<280;i++)noisy[i]=14000;
        SpectrumModel.Peak clean=SpectrumModel.footprints(frame(),CENTERS,STARTS,ENDS).get(0);
        SpectrumModel.Peak grass=SpectrumModel.footprints(noisy,CENTERS,STARTS,ENDS).get(0);
        check(Math.abs(clean.left-grass.left)<10&&Math.abs(clean.right-grass.right)<10,"grass tails do not widen projected flanks");
        check(Math.abs(clean.left-f(155))<10&&Math.abs(clean.right-f(245))<10,"flanks extrapolate to theoretical feet");
        check(clean.leftAt(9000)<clean.left&&clean.rightAt(9000)>clean.right,"flanks continue as straight lines to plot baseline");
        feed(m,30000);first=m.peaks.get(0);second=m.peaks.get(1);
        check(m.pick(first.left-50,60)==first,"near miss catches correct mountain");
        check(m.pick(first.left-70,60)==null,"outside touch tolerance remains manual");
        check(m.pick(first.center,0)==first,"direct hit works without tolerance");
        double between=(first.right+second.left)/2;
        check(m.pick(between+20,2000)==second,"nearest signal chosen in overlapping capture margins");
        check(m.pick(SpectrumModel.LOW,180)==null,"empty spectrum area stays manual");
        System.out.println("PASS: flanks, neighbours, retune memory, both channels, transient loss, switch-off, restart, feed outage, drift, empty data, projected flanks, grass tails, touch margins, nearest target");
    }
}
