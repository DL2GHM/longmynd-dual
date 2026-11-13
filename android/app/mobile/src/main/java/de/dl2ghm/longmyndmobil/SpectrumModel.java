package de.dl2ghm.longmyndmobil;

import java.util.*;

/** Spectrum footprints and calls remembered only for the lifetime of a signal. */
final class SpectrumModel {
    static final double LOW=10490500,SPAN=9000;
    static final long OFF_DELAY_MS=2000;
    /** Vertical scale of the Pi's RTL browser curve, expressed in the wire format. */
    static final class RtlScale {
        private static final int ZERO = 9000, UNITS_PER_DB = 1250;
        private static final int NEGATIVE_RANGE_DB = 6;
        private static final float SPACE_BELOW_ZERO = 0.08f;
        static final int PLOT_FLOOR = ZERO - NEGATIVE_RANGE_DB * UNITS_PER_DB;

        static int ceilingDb(int[] bins) {
            int peak = 0;
            for (int bin : bins) peak = Math.max(peak, bin);
            double peakDb = (peak - ZERO) / (double) UNITS_PER_DB;
            return Math.max(15, 3 * (int) Math.ceil((peakDb + 1) / 3));
        }

        static float position(float bin, int ceilingDb) {
            float db = (bin - ZERO) / UNITS_PER_DB;
            if (db < 0) {
                return SPACE_BELOW_ZERO * (1 + Math.max(-NEGATIVE_RANGE_DB, db) / NEGATIVE_RANGE_DB);
            }
            return Math.min(1, SPACE_BELOW_ZERO + (1 - SPACE_BELOW_ZERO) * db / ceilingDb);
        }

        static int binAtDb(int db) {
            return ZERO + db * UNITS_PER_DB;
        }
    }

    /** Local display correction with temporal averaging and a stable vertical scale. */
    static final class LocalDisplay {
        private static final int BASELINE_WINDOW = 270, BASELINE_STEP = 32, FLOOR = 9000;
        static final int PLOT_FLOOR = FLOOR - 1000;
        private static final int MIN_PLOT_CEILING = PLOT_FLOOR + 12000;
        private static final double TIME_CONSTANT_MS = 3500;
        private float[] average = new float[0];
        private final float[] window = new float[BASELINE_WINDOW + 1];
        private float ceiling = MIN_PLOT_CEILING;
        private long updated;

        void reset() {
            average = new float[0];
            ceiling = MIN_PLOT_CEILING;
            updated = 0;
        }

        float ceiling() {
            return ceiling;
        }

        int[] update(int[] bins, long now) {
            int count = bins.length;
            if (count < 2) {
                reset();
                return bins;
            }
            long elapsed = now - updated;
            boolean initialize = average.length != count || elapsed >= 3000;
            if (average.length != count) average = new float[count];
            double weight = initialize ? 1 : 1 - Math.exp(-elapsed / TIME_CONSTANT_MS);
            for (int i = 0; i < count; i++) {
                int value = bins[i];
                if (initialize || value == 0 || average[i] == 0) average[i] = value;
                else average[i] += weight * (value - average[i]);
            }
            updated = now;

            int[] result = new int[count];
            int left = 0;
            float leftFloor = localFloor(left);
            while (left < count - 1) {
                int right = Math.min(left + BASELINE_STEP, count - 1);
                float rightFloor = localFloor(right);
                float slope = (rightFloor - leftFloor) / (right - left);
                for (int i = left; i <= right; i++) {
                    float baseline = leftFloor + (i - left) * slope;
                    if (average[i] > 0) result[i] = Math.round(average[i] - baseline + FLOOR);
                }
                left = right;
                leftFloor = rightFloor;
            }
            int[] levels = result.clone();
            Arrays.sort(levels);
            int target = Math.max(MIN_PLOT_CEILING, levels[(count - 1) * 99 / 100] + 2500);
            ceiling += weight * (target - ceiling);
            return result;
        }

        private float localFloor(int bin) {
            int half = BASELINE_WINDOW / 2;
            int from = Math.max(0, bin - half);
            int to = Math.min(average.length, Math.max(BASELINE_WINDOW, bin + half + 1));
            int length = 0;
            for (int i = from; i < to; i++) {
                if (average[i] > 0) window[length++] = average[i];
            }
            if (length == 0) return FLOOR;
            Arrays.sort(window, 0, length);
            return window[(length - 1) / 5];
        }
    }
    static final class Peak {
        final double center,left,right,shoulderLevel,leftShoulder,rightShoulder;
        final double leftSlope,rightSlope,minFrequency,maxFrequency;
        Peak(double c,double level,double ls,double rs,double lm,double rm,double min,double max,double noise){
            center=c;shoulderLevel=level;leftShoulder=ls;rightShoulder=rs;
            leftSlope=lm;rightSlope=rm;minFrequency=min;maxFrequency=max;
            left=leftAt(noise);right=rightAt(noise);
        }
        double leftAt(double level){return Math.max(minFrequency,Math.min(center,leftShoulder+leftSlope*(Math.min(level,shoulderLevel)-shoulderLevel)));}
        double rightAt(double level){return Math.min(maxFrequency,Math.max(center,rightShoulder+rightSlope*(Math.min(level,shoulderLevel)-shoulderLevel)));}
        double visualCenter(){return (left+right)/2;}
        boolean contains(double f){return f>=left&&f<=right;}
    }
    static final class Label {
        Peak peak;
        final String[] calls={"",""};
        long missingSince=-1;
        Label(Peak p){peak=p;}
    }
    List<Peak> peaks=new ArrayList<>();
    final List<Label> labels=new ArrayList<>();
    boolean fresh=false;

    static double frequency(double index,int count){return LOW+(index+1)*SPAN/count;}
    static int index(double f,int count){return Math.max(0,Math.min(count-1,(int)Math.round((f-LOW)*count/SPAN-1)));}

    /** Find a flank crossing above the grass, interpolated between adjacent FFT bins. */
    static double crossing(double[] smooth,int peak,int limit,int direction,double level){
        int inner=peak;
        while(inner!=limit){
            int outer=inner+direction;
            if(smooth[outer]<=level){
                double difference=smooth[inner]-smooth[outer];
                return difference>0?outer+(inner-outer)*(level-smooth[outer])/difference:outer;
            }
            inner=outer;
        }
        return limit;
    }

    /** Extrapolate the 30–70% flanks; low-amplitude grass must not widen the selection. */
    static List<Peak> footprints(int[] bins,int[] centers,int[] starts,int[] ends){
        List<Peak> result=new ArrayList<>();
        if(bins.length<3)return result;
        int[] ordered=bins.clone();Arrays.sort(ordered);
        double noise=ordered[ordered.length/5];
        double[] smooth=new double[bins.length];
        for(int i=0;i<bins.length;i++){
            int a=Math.max(0,i-1),b=Math.min(bins.length-1,i+1);
            for(int j=a;j<=b;j++)smooth[i]+=bins[j];
            smooth[i]/=b-a+1;
        }
        for(int s=0;s<centers.length;s++){
            int left=index(starts[s],bins.length),right=index(ends[s],bins.length);
            if(right<left)continue;
            double strength=noise;int peak=index(centers[s],bins.length);
            for(int i=left;i<=right;i++)if(smooth[i]>strength){strength=smooth[i];peak=i;}
            if(strength<=noise+250)continue;
            int min=0,max=bins.length-1;double minFrequency=LOW,maxFrequency=LOW+SPAN;
            for(int other=0;other<centers.length;other++){
                if(other==s)continue;
                if(centers[other]<centers[s]){double boundary=(ends[other]+starts[s])/2.0;minFrequency=Math.max(minFrequency,boundary);min=Math.max(min,index(boundary,bins.length));}
                if(centers[other]>centers[s]){double boundary=(ends[s]+starts[other])/2.0;maxFrequency=Math.min(maxFrequency,boundary);max=Math.min(max,index(boundary,bins.length));}
            }
            double lowerLevel=noise+(strength-noise)*.30,upperLevel=noise+(strength-noise)*.70;
            double leftLow=crossing(smooth,peak,min,-1,lowerLevel),leftHigh=crossing(smooth,peak,min,-1,upperLevel);
            double rightLow=crossing(smooth,peak,max,1,lowerLevel),rightHigh=crossing(smooth,peak,max,1,upperLevel);
            double leftSlope=(frequency(leftHigh,bins.length)-frequency(leftLow,bins.length))/(upperLevel-lowerLevel);
            double rightSlope=(frequency(rightHigh,bins.length)-frequency(rightLow,bins.length))/(upperLevel-lowerLevel);
            result.add(new Peak(centers[s],upperLevel,frequency(leftHigh,bins.length),frequency(rightHigh,bins.length),
                leftSlope,rightSlope,minFrequency,maxFrequency,noise));
        }
        return result;
    }
    void update(int[] bins,int[] centers,int[] starts,int[] ends,long now,boolean available){
        fresh=available&&bins.length>=3;
        if(!fresh){peaks=new ArrayList<>();for(Label l:labels)l.missingSince=-1;return;}
        peaks=footprints(bins,centers,starts,ends);
        Set<Peak> used=Collections.newSetFromMap(new IdentityHashMap<Peak,Boolean>());
        for(Iterator<Label> it=labels.iterator();it.hasNext();){
            Label l=it.next();Peak match=null;double distance=Double.MAX_VALUE;
            for(Peak p:peaks){
                double tolerance=Math.max(20,Math.min(80,(l.peak.right-l.peak.left)*.12));
                double delta=Math.abs(l.peak.center-p.center);
                if(!used.contains(p)&&delta<=tolerance&&delta<distance){match=p;distance=delta;}
            }
            if(match!=null){l.peak=match;l.missingSince=-1;used.add(match);}
            else if(l.missingSince<0)l.missingSince=now;
            else if(now-l.missingSince>=OFF_DELAY_MS)it.remove();
        }
    }
    Peak at(double frequency){
        Peak best=null;
        for(Peak p:peaks)if(p.contains(frequency)&&(best==null||Math.abs(p.center-frequency)<Math.abs(best.center-frequency)))best=p;
        return best;
    }
    Peak pick(double frequency,double tolerance){
        Peak hit=at(frequency);if(hit!=null)return hit;
        Peak best=null;double distance=Double.MAX_VALUE;
        for(Peak p:peaks){
            double gap=Math.max(p.left-frequency,frequency-p.right);
            if(gap<=tolerance&&(gap<distance||(gap==distance&&best!=null&&Math.abs(p.center-frequency)<Math.abs(best.center-frequency)))){
                best=p;distance=gap;
            }
        }
        return best;
    }
    void remember(int channel,double frequency,String call){
        if(!fresh||call==null||call.trim().isEmpty())return;
        Peak p=at(frequency);if(p==null)return;
        Label label=null;
        for(Label l:labels)if(l.peak==p){label=l;break;}
        if(label==null){label=new Label(p);labels.add(label);}
        // One identity per signal. If a service changes while on air, update both copies.
        for(int ch=0;ch<2;ch++)if(!label.calls[ch].isEmpty())label.calls[ch]=call.trim();
        label.calls[channel]=call.trim();
    }
}
