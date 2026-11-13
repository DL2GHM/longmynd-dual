package de.dl2ghm.longmyndmobil;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.Configuration;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import android.net.Uri;
import java.util.ArrayList;
import java.util.Arrays;
import org.json.*;
import java.net.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    final Handler ui=new Handler(Looper.getMainLooper());
    final ScheduledExecutorService network=Executors.newScheduledThreadPool(3);
    final MediaPlayer[] players=new MediaPlayer[2];
    LibVLC vlc;
    final LinearLayout[] panels=new LinearLayout[2];
    final VLCVideoLayout[] views=new VLCVideoLayout[2];
    final TextView[] info=new TextView[2];
    final TextView[] playback=new TextView[2];
    final boolean[] playingRequested=new boolean[2];
    final EditText[] freq=new EditText[2],sr=new EditText[2];
    EditText address;TextView connection;LinearLayout videoArea;Spinner mode;CheckBox hardware;
    SpectrumView spectrum;TextView spectrumStatus;LinearLayout spectrumBlock;
    volatile boolean spectrumEnabled=true;int spectrumChannel=0;
    volatile String base="";volatile int generation=0;volatile boolean connected=false;
    boolean foreground=false;int audioChannel=0;
    final boolean[] wasLocked=new boolean[2];
    final String[] lastService={"",""};
    final int[] lastFrequency=new int[2],lastRate=new int[2];
    final int GREEN=Color.rgb(113,219,143),BG=Color.rgb(16,25,20);

    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    TextView label(String text){TextView v=new TextView(this);v.setText(text);v.setTextColor(GREEN);v.setTextSize(15);v.setPadding(dp(5),dp(4),dp(5),dp(4));return v;}
    Button button(String text){Button b=new Button(this);b.setText(text);b.setTextColor(GREEN);return b;}
    EditText field(String text){EditText e=new EditText(this);e.setText(text);e.setTextColor(Color.WHITE);e.setSingleLine();e.setTextSize(14);return e;}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);vlc=new LibVLC(this,new ArrayList<>(Arrays.asList("--network-caching=700","--no-video-title-show")));getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);root.setFitsSystemWindows(true);
        root.addView(label("DL2GHM · Longmynd Mobil · Beta 3"));
        LinearLayout bar=new LinearLayout(this);
        address=field(getPreferences(0).getString("server","http://raspberrypi.local:8090"));
        address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        bar.addView(address,new LinearLayout.LayoutParams(0,dp(52),1));
        Button connect=button("Verbinden");bar.addView(connect);connect.setOnClickListener(v->connect());root.addView(bar);
        connection=label("Pi-Adresse eingeben. Lokal oder ueber dein VPN.");root.addView(connection);
        hardware=new CheckBox(this);hardware.setText("Hardware-Decodierung (aus = Software)");hardware.setTextColor(GREEN);
        hardware.setChecked(getPreferences(0).getBoolean("hardware",true));root.addView(hardware);
        hardware.setOnCheckedChangeListener((toggle,checked)->{getPreferences(0).edit().putBoolean("hardware",checked).apply();if(connected){for(int ch=0;ch<2;ch++)releasePlayer(ch);layoutChannels();}});
        mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Beide Kanaele","Kanal 1 · Vollbildbereich","Kanal 2 · Vollbildbereich"}));root.addView(mode);
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){layoutChannels();}});
        CheckBox showSpectrum=new CheckBox(this);showSpectrum.setText("QO-100 Spektrum anzeigen");showSpectrum.setTextColor(GREEN);
        spectrumEnabled=getPreferences(0).getBoolean("spectrum",true);showSpectrum.setChecked(spectrumEnabled);root.addView(showSpectrum);
        spectrumBlock=new LinearLayout(this);spectrumBlock.setOrientation(1);root.addView(spectrumBlock);
        LinearLayout spectrumBar=new LinearLayout(this);spectrumBar.addView(label("Tippen schaltet:"));
        RadioGroup target=new RadioGroup(this);target.setOrientation(0);
        for(int ch=0;ch<2;ch++){RadioButton radio=new RadioButton(this);radio.setText("Kanal "+(ch+1));radio.setTextColor(ch==0?GREEN:Color.rgb(255,198,105));radio.setId(ch+100);target.addView(radio);}
        target.check(100);target.setOnCheckedChangeListener((group,id)->spectrumChannel=id-100);spectrumBar.addView(target);spectrumBlock.addView(spectrumBar);
        spectrumStatus=label("Internet-Spektrum von BATC / AMSAT-UK");spectrumStatus.setTextSize(11);spectrumBlock.addView(spectrumStatus);
        spectrum=new SpectrumView(this);spectrum.listener=(frequency,rate,manual)->selectSpectrumSignal(frequency,rate,manual);
        spectrumBlock.addView(spectrum,new LinearLayout.LayoutParams(-1,dp(135)));
        spectrumBlock.setVisibility(spectrumEnabled?View.VISIBLE:View.GONE);
        showSpectrum.setOnCheckedChangeListener((toggle,enabled)->{spectrumEnabled=enabled;getPreferences(0).edit().putBoolean("spectrum",enabled).apply();spectrumBlock.setVisibility(enabled?View.VISIBLE:View.GONE);});
        videoArea=new LinearLayout(this);root.addView(videoArea,new LinearLayout.LayoutParams(-1,0,1));
        for(int ch=0;ch<2;ch++){buildChannel(ch);videoArea.addView(panels[ch]);}
        root.addView(label("Gemeinsame Empfaenger: Umschalten gilt auch am PC. Ton per Kanal waehlen."));
        setContentView(root);layoutChannels();
        network.scheduleWithFixedDelay(()->pollStatus(),0,1,TimeUnit.SECONDS);
        network.scheduleWithFixedDelay(()->pollSpectrum(),0,350,TimeUnit.MILLISECONDS);
    }
    void buildChannel(int ch){
        panels[ch]=new LinearLayout(this);panels[ch].setOrientation(1);panels[ch].setPadding(dp(3),dp(3),dp(3),dp(3));
        LinearLayout title=new LinearLayout(this);title.addView(label("KANAL "+(ch+1)),new LinearLayout.LayoutParams(0,-2,1));
        Button audio=button("Ton "+(ch+1));title.addView(audio);audio.setOnClickListener(v->{audioChannel=ch;updateAudio();});panels[ch].addView(title);
        info[ch]=label("Nicht verbunden");info[ch].setTextSize(13);panels[ch].addView(info[ch]);
        playback[ch]=label("Wiedergabe nicht gestartet");playback[ch].setTextSize(12);panels[ch].addView(playback[ch]);
        views[ch]=new VLCVideoLayout(this);views[ch].setKeepScreenOn(true);
        // VLCVideoLayout changes its own LayoutParams to MATCH_PARENT when
        // attached. Only the outer slot owns the LinearLayout weight.
        FrameLayout slot=new FrameLayout(this);
        slot.addView(views[ch],new FrameLayout.LayoutParams(-1,-1));
        panels[ch].addView(slot,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout fields=new LinearLayout(this);
        freq[ch]=field("10491.538");freq[ch].setHint("MHz");freq[ch].setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        sr[ch]=field("1500");sr[ch].setHint("kS/s");sr[ch].setInputType(InputType.TYPE_CLASS_NUMBER);
        fields.addView(freq[ch],new LinearLayout.LayoutParams(0,dp(48),2));fields.addView(sr[ch],new LinearLayout.LayoutParams(0,dp(48),1));
        LinearLayout manual=new LinearLayout(this);manual.setOrientation(1);manual.setVisibility(View.GONE);panels[ch].addView(manual);
        Button expand=button("Manuell");expand.setTextSize(11);title.addView(expand);expand.setOnClickListener(v->manual.setVisibility(manual.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        Button tune=button("Setzen");fields.addView(tune);tune.setOnClickListener(v->tune(ch));manual.addView(fields);
        Button beacon=button("Bake · 10491.538 MHz / 1500 kS/s");beacon.setTextSize(11);
        beacon.setOnClickListener(v->{freq[ch].setText("10491.538");sr[ch].setText("1500");tune(ch);});manual.addView(beacon);
    }
    void layoutChannels(){
        if(videoArea==null||panels[1]==null)return;
        boolean wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        videoArea.setOrientation(wide?0:1);
        int selected=mode.getSelectedItemPosition();
        for(int ch=0;ch<2;ch++){
            panels[ch].setVisibility(selected==0||selected==ch+1?View.VISIBLE:View.GONE);
            panels[ch].setLayoutParams(new LinearLayout.LayoutParams(wide?0:-1,wide?-1:0,1));
        }
        if(selected>0)audioChannel=selected-1;
        updateAudio();
        for(int ch=0;ch<2;ch++){
            boolean show=connected&&foreground&&(selected==0||selected==ch+1);
            if(show){
                if(players[ch]==null)startPlayer(ch);
                if(!playingRequested[ch]){playingRequested[ch]=true;players[ch].play();}
            }else releasePlayer(ch);
        }
    }
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);layoutChannels();}
    void connect(){
        String candidate=address.getText().toString().trim();if(!candidate.contains("://"))candidate="http://"+candidate;
        try{URL u=new URL(candidate);if(!(u.getProtocol().equals("http")||u.getProtocol().equals("https"))||u.getHost().isEmpty()||u.getUserInfo()!=null||u.getQuery()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new Exception();}
        catch(Exception e){Toast.makeText(this,"Bitte nur http://Pi-IP:8090 eingeben",Toast.LENGTH_LONG).show();return;}
        base=candidate.replaceAll("/+$","");generation++;connected=true;
        getPreferences(0).edit().putString("server",base).apply();
        for(int ch=0;ch<2;ch++){wasLocked[ch]=false;lastService[ch]="";lastFrequency[ch]=0;lastRate[ch]=0;releasePlayer(ch);}layoutChannels();connection.setText("Verbinde mit "+base);
    }
    void releasePlayer(int ch){
        MediaPlayer old=players[ch];players[ch]=null;playingRequested[ch]=false;
        if(old!=null){old.setEventListener(null);old.stop();old.detachViews();old.release();}
    }
    void restartPlayer(int ch){releasePlayer(ch);layoutChannels();}
    void startPlayer(int ch){
        releasePlayer(ch);
        int gen=generation;
        MediaPlayer player=new MediaPlayer(vlc);players[ch]=player;
        // TextureView keeps each video's composition inside its own panel.
        player.attachViews(views[ch],null,false,true);
        Media media=new Media(vlc,Uri.parse(base+"/live/"+ch+".ts"));
        media.setHWDecoderEnabled(hardware.isChecked(),false);media.addOption(":network-caching=700");media.addOption(":http-reconnect");
        player.setMedia(media);media.release();
        playback[ch].setText("Verbinde Video "+(ch+1)+" · "+(hardware.isChecked()?"Hardware":"Software"));
        player.setEventListener(event->{
            String message=null;
            if(event.type==MediaPlayer.Event.Opening)message="Stream wird geoeffnet";
            else if(event.type==MediaPlayer.Event.Buffering)message=String.format(Locale.GERMAN,"Videopuffer %.0f %%",event.getBuffering());
            else if(event.type==MediaPlayer.Event.Playing)message="Wiedergabe laeuft";
            else if(event.type==MediaPlayer.Event.Vout)message="Aktive Videoausgaben: "+event.getVoutCount();
            if(message!=null){String value=message;ui.post(()->{if(players[ch]==player)playback[ch].setText(value);});}
            if(event.type==MediaPlayer.Event.EncounteredError||event.type==MediaPlayer.Event.EndReached){
                ui.post(()->{if(players[ch]==player)playback[ch].setText("Stream unterbrochen · neuer Versuch in 3 s");});
                ui.postDelayed(()->{if(connected&&foreground&&gen==generation&&players[ch]==player)restartPlayer(ch);},3000);
            }
        });
        updateAudio();
    }
    void updateAudio(){for(int ch=0;ch<2;ch++)if(players[ch]!=null)players[ch].setVolume(ch==audioChannel?100:0);}
    void selectSpectrumSignal(int frequency,int rate,boolean manual){
        if(!connected)return;final int ch=spectrumChannel;
        if(manual){
            final int[] rates={33,66,125,250,333,500,1000,1500,2000};String[] labels=new String[rates.length];
            for(int i=0;i<rates.length;i++)labels[i]=rates[i]+" kS/s";
            new AlertDialog.Builder(this).setTitle(String.format(Locale.GERMAN,"Kanal %d · %.3f MHz",ch+1,frequency/1000.0))
                .setItems(labels,(dialog,which)->applySpectrumTune(ch,frequency,rates[which])).setNegativeButton("Abbrechen",null).show();
        }else applySpectrumTune(ch,frequency,rate);
    }
    void applySpectrumTune(int ch,int frequency,int rate){
        freq[ch].setText(String.format(Locale.US,"%.3f",frequency/1000.0));sr[ch].setText(Integer.toString(rate));
        if(mode.getSelectedItemPosition()>0&&mode.getSelectedItemPosition()!=ch+1)mode.setSelection(ch+1);
        Toast.makeText(this,String.format(Locale.GERMAN,"Kanal %d: %.3f MHz / %d kS/s",ch+1,frequency/1000.0,rate),Toast.LENGTH_SHORT).show();tune(ch);
    }
    void pollSpectrum(){
        if(!connected||!foreground||!spectrumEnabled)return;int gen=generation;
        try{
            JSONObject data=new JSONObject(request("/api/spectrum",null));
            ui.post(()->{if(gen!=generation||!connected)return;try{spectrum.setData(data);spectrumStatus.setText(data.getBoolean("online")?"BATC / AMSAT-UK · Symbolrate geschaetzt · Zwei Finger: Zoom, Doppeltipp: alles":"BATC-Spektrum offline · Pi-Internetzugang pruefen");}catch(JSONException e){spectrumStatus.setText("Spektrumformat unbekannt");}});
        }catch(Exception e){ui.post(()->{if(gen==generation)spectrumStatus.setText(e.getMessage()!=null&&e.getMessage().contains("404")?"Spektrum benoetigt den neuen Pi-Installer":"Spektrum voruebergehend nicht erreichbar");});}
    }
    String request(String path,String body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();
        c.setConnectTimeout(3000);c.setReadTimeout(3000);c.setInstanceFollowRedirects(false);
        try{
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}
            if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());
            try(InputStream i=c.getInputStream();ByteArrayOutputStream o=new ByteArrayOutputStream()){
                byte[] b=new byte[4096];int n;while((n=i.read(b))!=-1){o.write(b,0,n);if(o.size()>262144)throw new IOException("Antwort zu gross");}return o.toString("UTF-8");
            }
        }finally{c.disconnect();}
    }
    void pollStatus(){
        if(!connected||!foreground)return;int gen=generation;
        try{
            JSONArray channels=new JSONObject(request("/api/status",null)).getJSONArray("channels");
            ui.post(()->{if(gen!=generation||!connected)return;connection.setText("Verbunden · "+base);
                for(int ch=0;ch<2;ch++)try{
                    JSONObject p=channels.getJSONObject(ch);String margin=p.isNull("margin_db")?"—":String.format(Locale.GERMAN,"%.1f",p.getDouble("margin_db"));
                    String mer=p.isNull("mer_db")?"—":String.format(Locale.GERMAN,"%.1f",p.getDouble("mer_db"));
                    boolean locked=p.getBoolean("locked");String service=p.getString("service");
                    int currentFrequency=p.getInt("frequency_khz"),currentRate=p.getInt("symbolrate_ksps");
                    boolean changedService=!lastService[ch].isEmpty()&&!service.isEmpty()&&!lastService[ch].equals(service);
                    boolean changedTune=lastFrequency[ch]>0&&(Math.abs(currentFrequency-lastFrequency[ch])>20||Math.abs(currentRate-lastRate[ch])>Math.max(5,lastRate[ch]/20));
                    if(locked&&(changedService||changedTune||(!wasLocked[ch]&&lastFrequency[ch]>0)))restartPlayer(ch);
                    wasLocked[ch]=locked;if(locked){lastFrequency[ch]=currentFrequency;lastRate[ch]=currentRate;if(!service.isEmpty())lastService[ch]=service;}
                    spectrum.setTuner(ch,currentFrequency,service,locked);
                    info[ch].setText((p.getBoolean("locked")?p.getString("service"):p.getBoolean("online")?"Suche Signal":"Empfaenger offline")+" · MER "+mer+" dB · D "+margin+" dB\n"+
                        String.format(Locale.GERMAN,"%.3f MHz · %d kS/s",p.getInt("frequency_khz")/1000.0,p.getInt("symbolrate_ksps")));
                }catch(JSONException e){info[ch].setText("Statusformat nicht erkannt");}
            });
        }catch(Exception e){ui.post(()->{if(gen==generation)connection.setText("Keine Statusverbindung · "+e.getMessage());});}
    }
    void tune(int ch){
        if(!connected){Toast.makeText(this,"Zuerst verbinden",Toast.LENGTH_SHORT).show();return;}
        try{
            int khz=new BigDecimal(freq[ch].getText().toString().replace(',','.')).multiply(new BigDecimal(1000)).intValueExact();
            int rate=Integer.parseInt(sr[ch].getText().toString());
            if(khz<9894000||khz>12200000||rate<33||rate>27500)throw new Exception();
            String body=new JSONObject().put("frequency_khz",khz).put("symbolrate_ksps",rate).toString();
            int gen=generation;
            network.execute(()->{try{request("/api/tune/"+ch,body);ui.post(()->{if(gen==generation&&connected)restartPlayer(ch);});}catch(Exception e){ui.post(()->Toast.makeText(this,"Umschalten: "+e.getMessage(),Toast.LENGTH_LONG).show());}});
        }catch(Exception e){Toast.makeText(this,"Frequenz in MHz und Symbolrate in kS/s pruefen",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onStart(){super.onStart();foreground=true;if(!connected&&getPreferences(0).contains("server"))connect();else layoutChannels();}
    @Override protected void onStop(){foreground=false;for(int ch=0;ch<2;ch++)releasePlayer(ch);super.onStop();}
    @Override protected void onDestroy(){connected=false;generation++;network.shutdownNow();ui.removeCallbacksAndMessages(null);for(int ch=0;ch<2;ch++)releasePlayer(ch);if(vlc!=null)vlc.release();super.onDestroy();}
}
