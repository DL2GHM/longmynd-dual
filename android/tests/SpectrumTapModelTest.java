package de.dl2ghm.longmyndmobil;
public class SpectrumTapModelTest {
    static void check(boolean c,String m){if(!c)throw new AssertionError(m);}
    static SpectrumTapModel model(){return new SpectrumTapModel(300,500,8,80);}
    static int tap(SpectrumTapModel m,long t){m.down(t,40,50);return m.up(t+30,40,50);}
    public static void main(String[] args){
        SpectrumTapModel m=model();tap(m,0);check(m.commit(329)==0,"single waits for next tap");check(m.commit(330)==1,"single commits once");check(m.commit(1000)==0,"no repeat");
        m=model();tap(m,0);tap(m,130);check(m.commit(459)==0,"double waits for third");check(m.commit(460)==2,"double commits after third-tap window");
        m=model();tap(m,0);tap(m,130);check(tap(m,260)==3,"triple commits on third release");check(m.commit(1000)==0,"triple does not also emit double or single");
        m=model();tap(m,0);m.down(130,40,50);m.move(60,50);m.up(160,60,50);check(m.commit(1000)==0,"drag is not a double");
        m=model();m.down(0,40,50);m.up(550,40,50);check(m.commit(1000)==0,"long press is not tap");
        m=model();tap(m,0);m.cancel();check(m.commit(1000)==0,"pinch and cancellation discard pending tap");
        m=model();tap(m,0);m.down(130,400,50);m.up(160,400,50);check(m.commit(460)==1,"distant taps are not double");
        m=model();m.down(0,40,50);check(!m.hold(1999,2000),"source waits two seconds");
        check(m.hold(2000,2000),"source opens at two seconds");
        m.up(2200,40,50);check(!m.hold(2200,500)&&m.commit(3000)==0,"source does not also tune on release");
        m=model();m.down(0,40,50);check(!m.hold(900,2000)&&m.hold(900,500),"shorter hold can select manual rate");
        m=model();m.down(0,40,50);m.move(60,50);check(!m.hold(2000,2000),"drag cancels source hold");
        m=model();m.down(0,40,50);m.cancel();check(!m.hold(2000,2000),"pinch or cancellation cancels source hold");
        System.out.println("SpectrumTapModelTest OK");
    }
}
