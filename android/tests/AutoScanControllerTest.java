package de.dl2ghm.longmyndmobil;
import java.util.*;

public class AutoScanControllerTest {
    static void check(boolean c,String m){if(!c)throw new AssertionError(m);}
    static final AutoScanController.Signal B=new AutoScanController.Signal(10491530,1500,10490800,10492250);
    static final AutoScanController.Signal A=new AutoScanController.Signal(10498270,333,10498100,10498430);
    static final AutoScanController.Signal C=new AutoScanController.Signal(10499250,125,10499190,10499310);
    static void receive(AutoScanController s,int ch,AutoScanController.Signal x,boolean lock,long t){s.receiver(ch,x.frequency,x.rate,lock,t);}
    static void frame(AutoScanController s,long t,AutoScanController.Signal... signals){s.spectrum(Arrays.asList(signals),t);}
    static void settled(AutoScanController s,long t,AutoScanController.Signal a,AutoScanController.Signal b,AutoScanController.Signal... sig){frame(s,t,sig);receive(s,0,a,true,t);receive(s,1,b,true,t);}
    public static void main(String[] args){
        AutoScanController s=new AutoScanController();settled(s,0,B,B,B,A);
        AutoScanController.Command cmd=s.next(0);
        check(cmd!=null&&cmd.frequency==A.frequency,"startup sees already transmitting station and replaces a beacon");
        settled(s,1,A,B,B,A);check(s.next(1)==null,"one station plus beacon is stable");
        settled(s,2,A,B,B,A,C);cmd=s.next(2);
        check(cmd!=null&&cmd.channel==1&&cmd.frequency==C.frequency,"second station replaces beacon, never existing station");
        settled(s,3,A,C,B,A,C);check(s.next(3)==null,"two distinct stations hold");
        settled(s,60003,A,C,B,A,C);check(s.next(60003)==null,"no timed rotation after a minute");
        frame(s,61000,B,A);receive(s,0,A,true,61000);receive(s,1,C,false,61000);
        check(s.next(61000)==null,"loss starts grace period");
        frame(s,70999,B,A);receive(s,0,A,true,70999);receive(s,1,C,false,70999);
        check(s.next(70999)==null,"hold through 9999 ms");
        frame(s,71000,B,A);receive(s,0,A,true,71000);receive(s,1,C,false,71000);cmd=s.next(71000);
        check(cmd!=null&&cmd.channel==1&&cmd.frequency==B.frequency,"lost station returns to beacon after ten seconds");
        settled(s,71001,A,B,B,A);check(s.next(71001)==null,"station and beacon hold after recovery");
        frame(s,72000,B);receive(s,0,A,false,72000);receive(s,1,B,true,72000);check(s.next(72000)==null,"last station loss waits");
        frame(s,82000,B);receive(s,0,A,false,82000);receive(s,1,B,true,82000);cmd=s.next(82000);
        check(cmd!=null&&cmd.channel==0&&cmd.frequency==B.frequency,"no station means beacon on BOTH receivers");
        settled(s,82001,B,B,B);check(s.next(82001)==null,"double beacon does not oscillate");
        settled(s,82002,A,A,B,A,C);cmd=s.next(82002);check(cmd!=null&&cmd.channel==1&&cmd.frequency==C.frequency,"duplicate station is replaced with distinct signal");
        s=new AutoScanController();settled(s,0,A,C,B,A,C);check(s.next(0)==null,"initial healthy pair");
        frame(s,100,B,A);receive(s,0,A,true,100);receive(s,1,C,false,100);check(s.next(100)==null,"brief dropout");
        settled(s,9000,A,C,B,A,C);check(s.next(9000)==null,"reappearance cancels grace period");
        check(s.status.equals("Automatik · Empfang halten"),"recovered status clears countdown");
        check(s.next(13000)==null&&s.status.contains("Spektrum"),"stale spectrum never retunes");
        s=new AutoScanController();settled(s,0,B,B,B,A);cmd=s.next(0);check(cmd!=null,"acquisition begins");
        settled(s,14000,B,B,B,A);check(s.next(14000)==null,"acquisition is not interrupted by old receiver status");
        settled(s,15000,B,B,B,A);check(s.next(15000)==null,"failed station receives cooldown");
        settled(s,16000,B,B,B,A,C);cmd=s.next(16000);check(cmd!=null&&cmd.frequency==C.frequency,"failed lock does not prevent another candidate");
        s=new AutoScanController();s.spectrum(Arrays.asList(B,A),0);check(s.next(0)==null,"no commands without receiver status");
        check(AutoScanController.unsupportedCodec("vvc1")&&AutoScanController.unsupportedCodec("H.266"),"VVC recognition follows codec, never callsign");
        check(!AutoScanController.unsupportedCodec("hevc")&&!AutoScanController.unsupportedCodec("h264"),"HEVC and H264 remain eligible");
        s=new AutoScanController();settled(s,0,A,B,B,A,C);s.unsupported(0,0);cmd=s.next(0);
        check(cmd!=null&&cmd.channel==0&&cmd.frequency==C.frequency,"known unsupported transmission is skipped for playable candidate");
        AutoScanController.Signal D=new AutoScanController.Signal(10495500,250,10495370,10495630);
        AutoScanController.Signal E=new AutoScanController.Signal(10496300,333,10496120,10496480);
        s=new AutoScanController();settled(s,100,A,C,B,A,C,D,E);
        List<AutoScanController.Command> changed=s.advance(100);
        check(changed.size()==2&&changed.get(0).frequency==D.frequency&&changed.get(1).frequency==E.frequency,"triple chooses two NEW stations, excludes current pair and beacon");
        check(s.next(101)==null,"normal scan waits while both new stations acquire");
        s=new AutoScanController();settled(s,100,A,B,B,A,C);changed=s.advance(100);
        check(changed.size()==1&&changed.get(0).channel==1&&changed.get(0).frequency==C.frequency,"one alternative replaces beacon, retains existing station");
        s=new AutoScanController();settled(s,100,A,C,B,A,C);check(s.advance(100).isEmpty(),"no alternative preserves current pair");
        AutoScanController copy=new AutoScanController(s);check(copy.receivers[0].frequency==A.frequency&&copy.signals.size()==3,"mode change retains fresh spectrum and receivers");
        check(copy.advance(4000).isEmpty(),"stale alternatives never retune");
        System.out.println("AutoScanControllerTest OK");
    }
}
