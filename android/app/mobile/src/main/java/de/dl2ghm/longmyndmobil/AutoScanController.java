package de.dl2ghm.longmyndmobil;

import java.util.*;

/** Foreground-only, two receiver scan policy. No Android or network dependencies. */
final class AutoScanController {
    static final long LOSS_MS=10000, ACQUIRE_MS=15000, RETRY_MS=30000;
    static final class Signal {
        final int frequency,rate,left,right;
        Signal(int f,int r,int l,int h){frequency=f;rate=r;left=l;right=h;}
        boolean beacon(){return Math.abs(frequency-10491550)<120&&rate>=1000;}
        boolean matches(int f,int r){return f>=left-15&&f<=right+15&&Math.abs(rate-r)<=Math.max(8,rate/5);}
    }
    static final class Command {
        final int channel,frequency,rate;
        Command(int ch,Signal s){channel=ch;frequency=s.frequency;rate=s.rate;}
    }
    static final class Receiver {
        int frequency,rate;boolean locked;long received=-100000,missing=-1;
        Signal pending;long requested;
    }
    final Receiver[] receivers={new Receiver(),new Receiver()};
    final Map<Integer,Long> failed=new HashMap<>();
    List<Signal> signals=new ArrayList<>();long spectrumAt=-100000;
    String status="Automatik · warte auf Spektrum";
    AutoScanController(){}
    AutoScanController(AutoScanController previous){
        signals=new ArrayList<>(previous.signals);spectrumAt=previous.spectrumAt;failed.putAll(previous.failed);
        for(int ch=0;ch<2;ch++){Receiver old=previous.receivers[ch],r=receivers[ch];r.frequency=old.frequency;r.rate=old.rate;r.locked=old.locked;r.received=old.received;}
    }
    List<Command> advance(long now){
        List<Command> result=new ArrayList<>();
        if(now-spectrumAt>3500||now-receivers[0].received>3500||now-receivers[1].received>3500)return result;
        failed.entrySet().removeIf(e->e.getValue()<=now);
        List<Signal> candidates=new ArrayList<>();
        for(Signal s:signals)if(!s.beacon()&&!cooling(s)&&!s.matches(receivers[0].frequency,receivers[0].rate)&&!s.matches(receivers[1].frequency,receivers[1].rate))candidates.add(s);
        candidates.sort(Comparator.comparingInt(s->s.frequency));
        int first=current(receivers[1])!=null&&current(receivers[1]).beacon()?1:0;
        for(Signal target:candidates){
            if(result.size()==2)break;
            boolean duplicate=false;for(Command c:result)if(target.matches(c.frequency,c.rate))duplicate=true;if(duplicate)continue;
            int ch=result.isEmpty()?first:1-first;Receiver r=receivers[ch];r.pending=target;r.requested=now;r.missing=-1;result.add(new Command(ch,target));
        }
        status=result.isEmpty()?"Automatik · keine anderen Stationen":"Automatik · andere Stationen abstimmen";
        return result;
    }
    void spectrum(List<Signal> values,long now){signals=new ArrayList<>(values);spectrumAt=now;}
    void receiver(int ch,int f,int rate,boolean locked,long now){
        Receiver r=receivers[ch];r.frequency=f;r.rate=rate;r.locked=locked;r.received=now;
        if(r.pending!=null&&locked&&r.pending.matches(f,rate)){r.pending=null;r.missing=-1;}
    }
    Signal current(Receiver r){
        for(Signal s:signals)if(s.matches(r.frequency,r.rate))return s;
        return null;
    }
    boolean healthy(Receiver r,long now){Signal s=current(r);return r.locked&&now-r.received<3500&&s!=null&&!cooling(s);}
    static boolean unsupportedCodec(String codec){
        if(codec==null)return false;
        String c=codec.toLowerCase(Locale.ROOT).replace(".","");
        return c.contains("vvc")||c.contains("h266")||c.equals("vvi1");
    }
    void unsupported(int ch,long now){
        Receiver r=receivers[ch];if(r.frequency<=0||r.pending!=null)return;
        failed.put(r.frequency,now+300000);r.pending=null;r.missing=now-LOSS_MS;
    }
    boolean cooling(Signal s){
        for(int f:failed.keySet())if(Math.abs(f-s.frequency)<=Math.max(15,s.rate/10))return true;
        return false;
    }
    Command next(long now){
        if(now-spectrumAt>3500){status="Automatik · warte auf Spektrum";return null;}
        for(Receiver r:receivers){
            if(now-r.received>3500){status="Automatik · warte auf Empfänger";return null;}
            if(r.pending!=null){
                if(now-r.requested<ACQUIRE_MS){status="Automatik · Signal wird gesucht";return null;}
                failed.put(r.pending.frequency,now+RETRY_MS);r.pending=null;r.missing=now-LOSS_MS;
            }
        }
        failed.entrySet().removeIf(e->e.getValue()<=now);
        List<Signal> others=new ArrayList<>();
        Signal beacon=new Signal(10491550,1500,10490800,10492300);
        for(Signal s:signals){if(s.beacon())beacon=s;else if(!cooling(s))others.add(s);}
        others.sort(Comparator.comparingInt(s->s.frequency));
        status="Automatik · Empfang halten";
        // Preserve every healthy non-beacon receiver. A new station replaces the beacon first.
        for(int ch=0;ch<2;ch++){
            Receiver r=receivers[ch],peer=receivers[1-ch];Signal here=current(r);
            boolean healthy=healthy(r,now),peerHealthy=healthy(peer,now);
            boolean duplicate=healthy&&peerHealthy&&here.matches(peer.frequency,peer.rate);
            if(healthy&&!here.beacon()&&(!duplicate||ch==0)){r.missing=-1;continue;}
            Signal target=null;
            for(Signal s:others)if(!(peerHealthy&&s.matches(peer.frequency,peer.rate))){target=s;break;}
            if(target==null)target=beacon;
            if(healthy&&here.matches(target.frequency,target.rate)){r.missing=-1;continue;}
            boolean replaceBeacon=healthy&&here.beacon()&&!target.beacon();
            if(!replaceBeacon&&!duplicate&&!(here!=null&&cooling(here))){
                if(r.missing<0)r.missing=now;
                if(now-r.missing<LOSS_MS){status="Automatik · RX "+(ch+1)+" wartet "+((LOSS_MS-(now-r.missing)+999)/1000)+" s";continue;}
            }
            r.pending=target;r.requested=now;r.missing=-1;
            status="Automatik · RX "+(ch+1)+" stimmt ab";return new Command(ch,target);
        }
        if(!status.contains("wartet"))status="Automatik · Empfang halten";
        return null;
    }
    void rejected(int ch,long now){Receiver r=receivers[ch];if(r.pending!=null)failed.put(r.pending.frequency,now+RETRY_MS);r.pending=null;r.missing=now;}
}
