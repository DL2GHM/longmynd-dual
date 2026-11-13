package de.dl2ghm.longmyndmobil;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.Configuration;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.view.*;
import android.text.TextUtils;
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
    ChatClient chat;
    LinearLayout chatBlock,chatEntry;
    View videoDivider,videoEndDivider,spectrumEndDivider,chatDivider,topDivider,bottomDivider;
    boolean framedFullscreen=false;
    ChatScrollView chatScroll;
    FrameLayout chatFrame;
    TextView chatMessages,chatConnection;
    EditText chatInput,chatCallsign;
    CheckBox chatAutomatic;
    Button chatSend,chatLogin,chatButton;
    boolean chatVisible=true;
    boolean chatOnline=false,chatSending=false,keyboardVisible=false,chatEditing=false;
    String chatNickname="";
    final java.util.LinkedHashMap<String,CharSequence> chatLines=new java.util.LinkedHashMap<>();
    final FrameLayout[] panels=new FrameLayout[2];
    final TextView[] badges=new TextView[2],volumeHints=new TextView[2];
    final int[] volumes={100,100};
    boolean statusVisible=true,diagnosticsVisible=false;int viewMode=0;
    int fullscreenChannel=-1; // Temporary overlay; normal view preferences are never changed.
    Button statusButton,spectrumButton,audioButton,viewButton;
    CheckBox spectrumSetting,statusSetting,diagnosticsSetting;
    final RoundedVideoLayout[] views=new RoundedVideoLayout[2];
    final TextView[] info=new TextView[2],modeBadges=new TextView[2];
    final LinearLayout[] dataRows=new LinearLayout[2];
    final TextView[] playback=new TextView[2];
    final boolean[] playingRequested=new boolean[2];
    final EditText[] freq=new EditText[2],sr=new EditText[2];
    EditText address;TextView connection;LinearLayout videoArea,toolbar;Spinner mode;CheckBox hardware;
    SpectrumView spectrum;TextView spectrumStatus;LinearLayout spectrumBlock;
    volatile boolean spectrumEnabled=true;
    volatile boolean localSpectrum;
    volatile String spectrumAddress="";
    volatile int spectrumGeneration;
    volatile String base="";volatile int generation=0;volatile boolean connected=false;
    volatile boolean foreground=false;boolean mixMuted=false;boolean relayOnline=false;
    volatile boolean automatic=true;volatile AutoScanController scan=new AutoScanController();
    FrameLayout screen;LinearLayout root;Button gear;PopupWindow settingsPopup;
    TextView scanLabel;int popupNaturalHeight=0,popupX=-1,popupY=-1,popupW=-1,popupH=-1;float popupScale=0;
    final ExecutorService tuningNetwork=Executors.newSingleThreadExecutor();
    final android.graphics.RectF[] picture={new android.graphics.RectF(),new android.graphics.RectF()};
    final boolean[] wasLocked=new boolean[2];
    final String[] lastService={"",""};
    final int[] lastFrequency=new int[2],lastRate=new int[2];
    final int GREEN=Color.rgb(113,219,143),BG=Color.rgb(16,25,20),BORDER=Color.rgb(120,139,70);

    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    TextView label(String text){TextView v=new TextView(this);v.setText(text);v.setTextColor(GREEN);v.setTextSize(15);v.setPadding(dp(5),dp(4),dp(5),dp(4));return v;}
    Button button(String text){Button b=new Button(this);b.setText(text);b.setTextColor(GREEN);return b;}
    EditText field(String text){EditText e=new EditText(this);e.setText(text);e.setTextColor(Color.WHITE);e.setSingleLine();e.setTextSize(14);return e;}
    Button tool(String text,String description){
        Button b=button(text);b.setTextSize(12);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(4),0,dp(4),0);
        b.setContentDescription(description);return b;
    }
    void roundedCard(View v,int fill,boolean framed,int inset){
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setColor(fill);background.setCornerRadius(framed?dp(10):0);v.setBackground(background);
        v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);v.setClipToOutline(framed);
        android.graphics.drawable.GradientDrawable border=new android.graphics.drawable.GradientDrawable();
        border.setColor(Color.TRANSPARENT);border.setCornerRadius(framed?dp(10):0);
        if(framed)border.setStroke(Math.max(1,dp(1)),BORDER);v.setForeground(border);
        v.setPadding(inset,inset,inset,inset);
    }
    void updateCardFrames(boolean fullscreen){
        if(framedFullscreen==fullscreen)return;framedFullscreen=fullscreen;
        int gap=fullscreen?0:dp(4);root.setPadding(gap,gap,gap,gap);
        for(RoundedVideoLayout video:views)if(video!=null)video.setRounded(!fullscreen);
    }
    View separator(){
        View v=new View(this);android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
        shape.setColor(Color.rgb(22,34,26));shape.setCornerRadius(dp(4));v.setBackground(shape);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;
    }
    int dividerHeight(View v){return v!=null&&v.getVisibility()==View.VISIBLE?Math.max(0,v.getLayoutParams().height):0;}
    void updateSeparators(){
        if(videoDivider==null)return;
        boolean normal=fullscreenChannel<0;
        if(topDivider!=null)topDivider.setVisibility(normal?View.VISIBLE:View.GONE);
        if(bottomDivider!=null)bottomDivider.setVisibility(normal?View.VISIBLE:View.GONE);
        boolean both=panels[0].getVisibility()==View.VISIBLE&&panels[1].getVisibility()==View.VISIBLE;
        videoDivider.setVisibility(normal&&both?View.VISIBLE:View.GONE);
        boolean wide=videoArea.getOrientation()==LinearLayout.HORIZONTAL;
        linearSize(videoDivider,wide?dp(7):-1,wide?-1:dp(7),0);
        boolean showSpectrum=spectrumBlock.getVisibility()==View.VISIBLE,showChat=chatBlock.getVisibility()==View.VISIBLE;
        videoEndDivider.setVisibility(normal&&(showSpectrum||showChat)?View.VISIBLE:View.GONE);
        spectrumEndDivider.setVisibility(normal&&showSpectrum&&showChat?View.VISIBLE:View.GONE);
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);vlc=new LibVLC(this,new ArrayList<>(Arrays.asList("--network-caching=700","--no-video-title-show")));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(android.os.Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        immerse();
        chatVisible=getPreferences(0).getBoolean("chatVisible",true);
        statusVisible=getPreferences(0).getBoolean("status",true);
        diagnosticsVisible=getPreferences(0).getBoolean("diagnostics",false);
        mixMuted=getPreferences(0).getBoolean("mixMuted",false);
        for(int ch=0;ch<2;ch++)volumes[ch]=Math.max(0,Math.min(100,getPreferences(0).getInt("volume"+ch,50)));
        screen=new FrameLayout(this);screen.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);root.setPadding(dp(4),dp(4),dp(4),dp(4));root.setFocusableInTouchMode(true);
        screen.addView(root,new FrameLayout.LayoutParams(-1,-1));
        // These controls live only on the separate configuration page.
        address=field(getPreferences(0).getString("server","http://raspberrypi.local:8090"));
        address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        connection=label("Verbindung wird aufgebaut");
        hardware=new CheckBox(this);hardware.setText("Hardware-Decodierung (aus = Software)");hardware.setTextColor(GREEN);
        hardware.setChecked(getPreferences(0).getBoolean("hardware",true));
        hardware.setOnCheckedChangeListener((toggle,checked)->{getPreferences(0).edit().putBoolean("hardware",checked).apply();if(connected){for(int ch=0;ch<2;ch++)releasePlayer(ch);layoutChannels();}});
        mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Beide Kanaele","Kanal 1 gross","Kanal 2 gross"}));
        viewMode=Math.max(0,Math.min(2,getPreferences(0).getInt("viewMode",0)));mode.setSelection(viewMode);
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(viewMode!=pos)setViewMode(pos);}});
        topDivider=separator();root.addView(topDivider,new LinearLayout.LayoutParams(-1,Math.max(1,Math.round(dp(7)/2f))));
        videoArea=new LinearLayout(this);root.addView(videoArea,new LinearLayout.LayoutParams(-1,0,1));
        for(int ch=0;ch<2;ch++){buildChannel(ch);videoArea.addView(panels[ch]);if(ch==0){videoDivider=separator();videoArea.addView(videoDivider,new LinearLayout.LayoutParams(-1,dp(7)));}}
        videoEndDivider=separator();root.addView(videoEndDivider,new LinearLayout.LayoutParams(-1,dp(7)));
        spectrumBlock=new LinearLayout(this);spectrumBlock.setOrientation(1);roundedCard(spectrumBlock,Color.rgb(10,22,16),true,dp(2));root.addView(spectrumBlock);
        spectrumStatus=label("BATC / AMSAT-UK"); // detailed state is on configuration page
        spectrum=new SpectrumView(this);spectrum.listener=(channel,frequency,rate,manual)->selectSpectrumSignal(channel,frequency,rate,manual);
        spectrum.onAutomatic=()->{setAutomatic(true);Toast.makeText(this,"Automatik eingeschaltet",Toast.LENGTH_SHORT).show();};
        spectrum.onAdvance=()->advanceStations();
        spectrum.onSource=this::chooseSpectrumSource;
        spectrumBlock.addView(spectrum,new LinearLayout.LayoutParams(-1,dp(spectrumHeight())));
        spectrumEndDivider=separator();root.addView(spectrumEndDivider,new LinearLayout.LayoutParams(-1,dp(7)));
        buildChat(root);
        bottomDivider=separator();root.addView(bottomDivider,new LinearLayout.LayoutParams(-1,Math.max(1,Math.round(dp(7)/2f))));
        spectrumEnabled=getPreferences(0).getBoolean("spectrum",true);spectrumBlock.setVisibility(spectrumEnabled?View.VISIBLE:View.GONE);
        localSpectrum=getPreferences(0).getBoolean("localSpectrum",false);
        spectrumAddress=getPreferences(0).getString("spectrumAddress","");
        spectrumSetting=new CheckBox(this);spectrumSetting.setText("Spektrum anzeigen");spectrumSetting.setTextColor(GREEN);spectrumSetting.setChecked(spectrumEnabled);
        spectrumSetting.setOnCheckedChangeListener((toggle,enabled)->{spectrumEnabled=enabled;getPreferences(0).edit().putBoolean("spectrum",enabled).apply();updateTools();});
        statusSetting=new CheckBox(this);statusSetting.setText("Empfangsdaten durchscheinend im Video");statusSetting.setTextColor(GREEN);statusSetting.setChecked(statusVisible);
        statusSetting.setOnCheckedChangeListener((toggle,enabled)->{statusVisible=enabled;getPreferences(0).edit().putBoolean("status",enabled).apply();updateTools();});
        diagnosticsSetting=new CheckBox(this);diagnosticsSetting.setText("Zusaetzliche Player-Diagnose");diagnosticsSetting.setTextColor(GREEN);diagnosticsSetting.setChecked(diagnosticsVisible);
        diagnosticsSetting.setOnCheckedChangeListener((toggle,enabled)->{diagnosticsVisible=enabled;getPreferences(0).edit().putBoolean("diagnostics",enabled).apply();updateTools();});
        gear=tool("⚙","Einstellungen öffnen");gear.setTextSize(23);gear.setAlpha(.32f);
        android.graphics.drawable.GradientDrawable gearBackground=new android.graphics.drawable.GradientDrawable();
        gearBackground.setColor(0xA0101914);gearBackground.setCornerRadius(dp(8));gearBackground.setStroke(dp(1),BORDER);gear.setBackground(gearBackground);
        screen.addView(gear,new FrameLayout.LayoutParams(dp(38),dp(38),Gravity.TOP|Gravity.LEFT));
        gear.setOnClickListener(v->showQuickSettings());
        gear.setOnHoverListener((v,e)->{gear.setAlpha(e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT?.32f:1f);if(e.getActionMasked()==MotionEvent.ACTION_HOVER_ENTER)showQuickSettings();return true;});
        setContentView(screen);root.requestFocus();layoutChannels();updateTools();
        if(android.os.Build.VERSION.SDK_INT>=30){
            screen.setOnApplyWindowInsetsListener((v,insets)->{
                int bottom=insets.getInsets(WindowInsets.Type.ime()).bottom;
                if(screen.getPaddingBottom()!=bottom)screen.setPadding(0,0,0,bottom);
                updateKeyboardVisibility(insets.isVisible(WindowInsets.Type.ime())||bottom>0);
                return insets;
            });
            screen.requestApplyInsets();
        }
        screen.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            if(android.os.Build.VERSION.SDK_INT<30){
                android.graphics.Rect visible=new android.graphics.Rect();screen.getWindowVisibleDisplayFrame(visible);
                boolean shown=screen.getRootView().getHeight()-visible.bottom>dp(150);
                updateKeyboardVisibility(shown);
            }
            allocateSpace();positionOverlays();
        });
        ui.postDelayed(new Runnable(){public void run(){if(isDestroyed())return;if(foreground)runScan();ui.postDelayed(this,500);}},500);
        network.scheduleWithFixedDelay(()->pollStatus(),0,1,TimeUnit.SECONDS);
        network.scheduleWithFixedDelay(()->pollSpectrum(),0,350,TimeUnit.MILLISECONDS);
    }
    void buildChat(LinearLayout root){
        chatCallsign=field(getPreferences(0).getString("chatCallsign",""));chatCallsign.setHint("z. B. DL2GHM");
        chatCallsign.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20)});
        chatCallsign.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        chatCallsign.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence t,int st,int count,int after){}
            public void onTextChanged(CharSequence t,int st,int before,int count){getPreferences(0).edit().putString("chatCallsign",t.toString().trim()).apply();}
            public void afterTextChanged(android.text.Editable e){}
        });
        chatAutomatic=new CheckBox(this);chatAutomatic.setTextColor(GREEN);chatAutomatic.setText("Chat beim App-Start automatisch anmelden");
        chatAutomatic.setChecked(getPreferences(0).getBoolean("chatAutomatic",false));
        chatAutomatic.setOnCheckedChangeListener((v,on)->getPreferences(0).edit().putBoolean("chatAutomatic",on).apply());
        chatConnection=label("Chat wird verbunden …");chatLogin=button("Jetzt anmelden");
        chatLogin.setOnClickListener(v->{
            if(!chatNickname.isEmpty()){chat.login("");return;}
            String nickname=chatCallsign.getText().toString().trim().toUpperCase(Locale.ROOT);
            if(!nickname.matches("[A-Z0-9][A-Z0-9/ -]{1,19}")){Toast.makeText(this,"Bitte ein Rufzeichen eingeben (2–20 Zeichen)",Toast.LENGTH_LONG).show();return;}
            chatCallsign.setText(nickname);chat.login(nickname);chat.start();
        });
        chatBlock=new LinearLayout(this);chatBlock.setOrientation(1);chatBlock.setPadding(dp(4),dp(4),dp(4),dp(4));chatBlock.setBackgroundColor(BG);root.addView(chatBlock);
        chatFrame=new FrameLayout(this);roundedCard(chatFrame,Color.rgb(12,22,16),true,0);chatBlock.addView(chatFrame,new LinearLayout.LayoutParams(-1,dp(54)));
        chatScroll=new ChatScrollView(this);chatScroll.setFillViewport(true);chatScroll.setPadding(dp(4),dp(4),dp(4),dp(4));chatFrame.addView(chatScroll,new FrameLayout.LayoutParams(-1,-1));
        chatMessages=label("Chat wird verbunden …");chatMessages.setTextSize(12);chatMessages.setTextColor(GREEN);chatMessages.setIncludeFontPadding(false);chatMessages.setLineSpacing(dp(1),1f);chatMessages.setPadding(dp(3),0,dp(3),0);chatMessages.setTypeface(android.graphics.Typeface.MONOSPACE);chatMessages.setTextIsSelectable(true);chatScroll.addView(chatMessages);
        chatDivider=separator();chatBlock.addView(chatDivider,new LinearLayout.LayoutParams(-1,dp(7)));
        LinearLayout entry=new LinearLayout(this);chatEntry=entry;entry.setGravity(Gravity.CENTER_VERTICAL);roundedCard(entry,Color.rgb(12,22,16),true,dp(2));chatBlock.addView(entry,new LinearLayout.LayoutParams(-1,dp(36)));
        chatInput=field("");chatInput.setHint("Zum Schreiben in den Einstellungen anmelden");chatInput.setHintTextColor(Color.GRAY);
        chatInput.setTextSize(13);chatInput.setPadding(dp(5),0,dp(5),0);chatInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        chatInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        chatInput.setOnFocusChangeListener((view,focused)->setChatEditing(focused));
        chatInput.setOnClickListener(view->setChatEditing(true));
        entry.addView(chatInput,new LinearLayout.LayoutParams(0,-1,1));chatSend=tool("Senden","Chatnachricht senden");entry.addView(chatSend,new LinearLayout.LayoutParams(dp(70),-1));chatSend.setEnabled(false);
        chatSend.setOnClickListener(v->sendChat());chatInput.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){sendChat();return true;}return false;});
        chat=new ChatClient(new ChatClient.Listener(){
            public void state(boolean online,String nickname,String detail){ui.post(()->{
                if(isDestroyed()||!foreground)return;
                chatOnline=online;chatNickname=nickname;chatConnection.setText(detail);chatLogin.setText(nickname.isEmpty()?"Jetzt anmelden":"Abmelden");
                chatInput.setHint(!online?"Chat offline · Entwurf bleibt erhalten":nickname.isEmpty()?"Zum Schreiben in den Einstellungen anmelden":"Nachricht als "+nickname+" schreiben …");
                chatSend.setEnabled(online&&!nickname.isEmpty()&&!chatSending);
                if(chatLines.isEmpty())chatMessages.setText(detail);
            });}
            public void message(JSONObject message,boolean history){ui.post(()->{if(!isDestroyed()&&foreground)appendChat(message);});}
            public void sent(String text,boolean success){ui.post(()->{
                if(isDestroyed())return;chatSending=false;chatSend.setEnabled(chat.canSend());
                if(success){if(chatInput.getText().toString().trim().equals(text))chatInput.setText("");}
                else Toast.makeText(MainActivity.this,"Senden nicht bestätigt · Entwurf bleibt erhalten. Vor erneutem Senden den Chat prüfen.",Toast.LENGTH_LONG).show();
            });}
        });
        if(chatAutomatic.isChecked()){
            String nickname=chatCallsign.getText().toString().trim().toUpperCase(Locale.ROOT);
            if(nickname.matches("[A-Z0-9][A-Z0-9/ -]{1,19}"))chat.login(nickname);
        }
    }
    void sendChat(){
        String text=chatInput.getText().toString().trim();if(text.isEmpty()||chatSending)return;
        if(!chat.canSend()){Toast.makeText(this,"Bitte den Chat in den Einstellungen anmelden",Toast.LENGTH_SHORT).show();return;}
        chatSending=true;chatSend.setEnabled(false);chat.send(text);
    }
    void appendChat(JSONObject message){
        String timestamp=message.optString("time"),name=message.optString("name"),text=message.optString("message");
        String key=timestamp+"\u0000"+name+"\u0000"+text;if(chatLines.containsKey(key))return;
        String time="";
        try{time=java.time.Instant.parse(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));}catch(Exception ignored){}
        String call=name.replaceAll("[\\r\\n]"," ");
        String body=text.replaceAll("[\\r\\n]"," ");
        if(call.length()>100)call=call.substring(0,100)+"…";
        if(body.length()>600)body=body.substring(0,600)+"…";
        android.text.SpannableStringBuilder line=new android.text.SpannableStringBuilder();
        if(!time.isEmpty())appendChatColor(line,time+"  ",Color.GRAY);
        appendChatColor(line,call+": ",Color.rgb(255,222,45));
        appendChatColor(line,body,GREEN);
        chatLines.put(key,line);while(chatLines.size()>100)chatLines.remove(chatLines.keySet().iterator().next());
        android.text.SpannableStringBuilder transcript=new android.text.SpannableStringBuilder();
        for(CharSequence entry:chatLines.values()){
            if(transcript.length()>0)transcript.append('\n');transcript.append(entry);
        }
        // Tail-following belongs to the ScrollView, not a measurement made before setText.
        chatMessages.setText(transcript);
    }
    void appendChatColor(android.text.SpannableStringBuilder builder,String text,int color){
        int start=builder.length();builder.append(text);
        builder.setSpan(new android.text.style.ForegroundColorSpan(color),start,builder.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    int chatHistoryHeight(){
        int lines=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?2:3;
        return chatScroll.preferredLineHeight()*lines+chatScroll.preferredPadding();
    }
    void setChatEditing(boolean editing){
        if(chatEditing==editing)return;
        chatEditing=editing;
        ui.post(this::layoutChannels);
    }
    void updateKeyboardVisibility(boolean shown){
        if(keyboardVisible==shown)return;
        keyboardVisible=shown;
        setChatEditing(shown&&chatInput.hasFocus());
    }
    boolean composingChat(){
        return chatEditing&&chatVisible&&fullscreenChannel<0;
    }
    void hideChatKeyboard(){
        setChatEditing(false);
        if(chatInput==null||!chatInput.hasFocus())return;
        chatInput.clearFocus();
        android.view.inputmethod.InputMethodManager input=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if(input!=null)input.hideSoftInputFromWindow(chatInput.getWindowToken(),0);
    }
    void immerse(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    int spectrumHeight(){return getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?64:96;}
    void addSetting(LinearLayout parent,View view){if(view.getParent()!=null)((ViewGroup)view.getParent()).removeView(view);parent.addView(view,new LinearLayout.LayoutParams(-1,-2));}
    void showSettings(){
        Dialog page=new Dialog(this,android.R.style.Theme_Material_NoActionBar);
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(1);outer.setBackgroundColor(BG);outer.setPadding(dp(12),dp(8),dp(12),dp(8));
        LinearLayout heading=new LinearLayout(this);heading.addView(label("Verbindung und Chat · Beta 39"),new LinearLayout.LayoutParams(0,-2,1));
        Button close=button("Zurueck");heading.addView(close);close.setOnClickListener(v->page.dismiss());outer.addView(heading);
        ScrollView scroll=new ScrollView(this);LinearLayout form=new LinearLayout(this);form.setOrientation(1);scroll.addView(form);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        form.addView(label("Pi-Adresse (Heimnetz oder VPN)"));addSetting(form,address);addSetting(form,connection);
        Button reconnect=button("Verbinden / erneut verbinden");form.addView(reconnect);reconnect.setOnClickListener(v->{connect();page.dismiss();});
        form.addView(label("QO-100-Chat · BATC / AMSAT-UK"));
        form.addView(label("Rufzeichen für den Chat"));addSetting(form,chatCallsign);
        addSetting(form,chatAutomatic);addSetting(form,chatConnection);addSetting(form,chatLogin);
        form.addView(label("Ohne automatische Anmeldung kannst du mitlesen und dich über den Button anmelden."));
        addSetting(form,hardware);
        addSetting(form,diagnosticsSetting);
        for(int ch=0;ch<2;ch++){final int channel=ch;Button manual=button("Kanal "+(ch+1)+" manuell abstimmen");form.addView(manual);manual.setOnClickListener(v->showManual(channel));}
        page.setContentView(outer);page.setOnDismissListener(d->immerse());page.show();page.getWindow().setLayout(-1,-1);
    }
    void setAutomatic(boolean enabled){
        automatic=enabled;scan=new AutoScanController(scan);
        for(TextView badge:modeBadges)if(badge!=null){badge.setText(enabled?"A":"M");badge.setContentDescription(enabled?"Automatikmodus":"Manueller Modus");}
        if(scanLabel!=null)scanLabel.setText(enabled?"Automatik · warte auf Empfänger":"Manuelle Steuerung");
    }
    boolean unsupportedVideo(int ch){
        if(players[ch]==null)return false;
        org.videolan.libvlc.interfaces.IMedia media=players[ch].getMedia();
        if(media==null)return false;
        try{for(int i=0;i<media.getTrackCount();i++){
            org.videolan.libvlc.interfaces.IMedia.Track t=media.getTrack(i);
            if(t instanceof org.videolan.libvlc.interfaces.IMedia.VideoTrack&&(AutoScanController.unsupportedCodec(t.codec)||AutoScanController.unsupportedCodec(t.originalCodec)))return true;
        }}finally{media.release();}
        return false;
    }
    void runScan(){
        if(!automatic||!foreground||!connected)return;
        for(int ch=0;ch<2;ch++)if(unsupportedVideo(ch))scan.unsupported(ch,android.os.SystemClock.elapsedRealtime());
        AutoScanController.Command command=scan.next(android.os.SystemClock.elapsedRealtime());
        if(scanLabel!=null)scanLabel.setText(scan.status);
        if(command==null)return;
        sendScanCommand(command);
    }
    void advanceStations(){
        if(!connected)return;
        setAutomatic(true);
        java.util.List<AutoScanController.Command> commands=scan.advance(android.os.SystemClock.elapsedRealtime());
        for(AutoScanController.Command command:commands)sendScanCommand(command);
        if(scanLabel!=null)scanLabel.setText(scan.status);
        Toast.makeText(this,commands.isEmpty()?"Keine anderen Stationen verfügbar":commands.size()==1?"Eine andere Station gefunden":"Zwei andere Stationen gefunden",Toast.LENGTH_SHORT).show();
    }
    void sendScanCommand(AutoScanController.Command command){
        int gen=generation;AutoScanController controller=scan;
        tuningNetwork.execute(()->{
            try{
                if(!automatic||!foreground||gen!=generation||controller!=scan)return;
                String body=new JSONObject().put("frequency_khz",command.frequency).put("symbolrate_ksps",command.rate).toString();
                request("/api/tune/"+command.channel,body);
                ui.post(()->{if(gen==generation&&controller==scan&&connected)restartPlayer(command.channel);});
            }catch(Exception e){ui.post(()->{if(controller==scan)scan.rejected(command.channel,android.os.SystemClock.elapsedRealtime());});}
        });
    }
    float menuScale(){
        int ch=overlayChannel();return VideoLayoutModel.scale(picture[ch].width(),getResources().getDisplayMetrics().density);
    }
    int overlayChannel(){
        if(fullscreenChannel>=0)return fullscreenChannel;
        if(panels[1].getVisibility()==View.VISIBLE&&videoArea.getOrientation()==LinearLayout.HORIZONTAL)return 1;
        return panels[0].getVisibility()==View.VISIBLE?0:1;
    }
    void place(View view,int x,int y,int width,int height){
        for(FrameLayout panel:panels)if(panel!=null&&view.getParent()==panel){x-=panel.getPaddingLeft();y-=panel.getPaddingTop();break;}
        FrameLayout.LayoutParams old=(FrameLayout.LayoutParams)view.getLayoutParams();
        if(old.leftMargin==x&&old.topMargin==y&&old.width==width&&old.height==height&&old.gravity==(Gravity.TOP|Gravity.LEFT))return;
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,height,Gravity.TOP|Gravity.LEFT);p.leftMargin=x;p.topMargin=y;view.setLayoutParams(p);
    }
    void positionOverlays(){
        if(gear==null||screen.getWidth()==0)return;
        for(int ch=0;ch<2;ch++){
            if(panels[ch].getVisibility()!=View.VISIBLE)continue;
            float aspect=videoAspect(ch);
            VideoLayoutModel.Frame f=VideoLayoutModel.fitInset(panels[ch].getWidth(),panels[ch].getHeight(),aspect,fullscreenChannel>=0?0:dp(2));
            picture[ch].set(f.left,f.top,f.left+f.width,f.top+f.height);
            // Frame and clip the VLC picture itself, including when the panel is letterboxed.
            place(views[ch],Math.round(f.left),Math.round(f.top),Math.max(1,Math.round(f.width)),Math.max(1,Math.round(f.height)));
            float scale=VideoLayoutModel.scale(f.width,getResources().getDisplayMetrics().density);
            int pad=Math.max(dp(3),Math.round(dp(7)*scale)),h=Math.round(dp(27)*scale);
            info[ch].setTextSize(11*scale);info[ch].setPadding(pad,0,pad,0);info[ch].setGravity(Gravity.CENTER_VERTICAL);
            modeBadges[ch].setTextSize(11*scale);modeBadges[ch].setPadding(pad,0,0,0);
            int available=Math.max(dp(30),Math.round(f.width)-2*pad-(ch==overlayChannel()?Math.round(dp(43)*scale):0));
            int dataWidth=Math.min(available,Math.round(info[ch].getPaint().measureText(info[ch].getText().toString()))+pad*4+Math.round(dp(20)*scale));
            place(dataRows[ch],Math.round(f.left)+pad,Math.round(f.top)+pad,dataWidth,h);
            playback[ch].setTextSize(10*scale);playback[ch].setSingleLine();playback[ch].setEllipsize(TextUtils.TruncateAt.END);
            int bottom=Math.round(f.top+f.height)-pad;
            place(playback[ch],Math.round(f.left)+pad,Math.max(Math.round(f.top),bottom-h),Math.max(1,Math.round(f.width)-2*pad),h);
            volumeHints[ch].setTextSize(12*scale);volumeHints[ch].setPadding(pad,0,pad,0);
            int hintWidth=Math.min(Math.round(f.width)-2*pad,Math.round(dp(150)*scale));
            place(volumeHints[ch],Math.round(f.left+f.width)-hintWidth-pad,Math.max(Math.round(f.top),bottom-h),hintWidth,h);
        }
        allocateSpace();
        int ch=overlayChannel();android.graphics.RectF f=picture[ch];float scale=menuScale();
        int[] panelLocation=new int[2],screenLocation=new int[2];panels[ch].getLocationInWindow(panelLocation);screen.getLocationInWindow(screenLocation);
        int size=Math.max(dp(28),Math.round(dp(38)*scale)),pad=Math.round(dp(7)*scale);
        int x=panelLocation[0]-screenLocation[0]+Math.round(f.right)-size-pad,y=panelLocation[1]-screenLocation[1]+Math.round(f.top)+pad;
        place(gear,Math.max(0,x),Math.max(0,y),size,size);gear.setTextSize(23*scale);
        gear.setVisibility(fullscreenChannel>=0?View.GONE:View.VISIBLE);
        if(settingsPopup!=null&&settingsPopup.isShowing()){
            if(Math.abs(scale-popupScale)>.04f){settingsPopup.dismiss();ui.post(()->showQuickSettings());return;}
            int width=Math.min(screen.getWidth()-dp(12),Math.round(dp(310)*scale));
            int maxHeight=Math.max(dp(60),screen.getHeight()-screen.getPaddingBottom()-Math.max(0,y)-dp(6));
            int px=Math.max(0,x+size-width)+screenLocation[0],py=Math.max(0,y)+screenLocation[1],height=Math.min(popupNaturalHeight,maxHeight);
            if(px!=popupX||py!=popupY||width!=popupW||height!=popupH){popupX=px;popupY=py;popupW=width;popupH=height;settingsPopup.update(px,py,width,height);}
        }
    }
    CheckBox quickToggle(String text,boolean checked,float scale,android.widget.CompoundButton.OnCheckedChangeListener listener){
        CheckBox box=new CheckBox(this);box.setText(text);box.setTextColor(GREEN);box.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));box.setTextSize(14*scale);box.setMinHeight(Math.max(dp(40),Math.round(dp(42)*scale)));box.setChecked(checked);box.setOnCheckedChangeListener(listener);return box;
    }
    void showQuickSettings(){
        if(settingsPopup!=null&&settingsPopup.isShowing())return;
        float scale=menuScale();popupScale=scale;LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(8),dp(3),dp(8),dp(8));
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Einstellungen · Beta 28");title.setTextSize(14*scale);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button close=tool("⚙","Menü schließen");close.setTextSize(23*scale);heading.addView(close,new LinearLayout.LayoutParams(Math.max(dp(38),Math.round(dp(40)*scale)),dp(40)));form.addView(heading);
        scanLabel=label(automatic?scan.status:"Manuelle Steuerung");scanLabel.setTextSize(11*scale);form.addView(scanLabel);
        form.addView(quickToggle("Automatik",automatic,scale,(v,on)->setAutomatic(on)));
        form.addView(quickToggle("Empfangsdaten",statusVisible,scale,(v,on)->statusSetting.setChecked(on)));
        form.addView(quickToggle("Spektrum",spectrumEnabled,scale,(v,on)->spectrumSetting.setChecked(on)));
        form.addView(quickToggle("Chat",chatVisible,scale,(v,on)->{chatVisible=on;getPreferences(0).edit().putBoolean("chatVisible",on).apply();if(!on)hideChatKeyboard();layoutChannels();}));
        form.addView(quickToggle("Ton stumm",mixMuted,scale,(v,on)->{mixMuted=on;updateAudio();}));
        Spinner choice=new Spinner(this);choice.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Beide Empfänger","RX 1 groß","RX 2 groß"}){
            @Override public View getView(int pos,View reused,ViewGroup parent){TextView v=(TextView)super.getView(pos,reused,parent);v.setTextColor(GREEN);v.setTextSize(14*scale);return v;}
            @Override public View getDropDownView(int pos,View reused,ViewGroup parent){TextView v=(TextView)super.getDropDownView(pos,reused,parent);v.setTextColor(GREEN);v.setTextSize(14*scale);return v;}
        });choice.setSelection(viewMode);
        choice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(pos!=viewMode)setViewMode(pos);}});form.addView(choice);
        Button connectionSettings=tool("Verbindung und Chat","Verbindung und Chat konfigurieren");connectionSettings.setTextSize(13*scale);form.addView(connectionSettings,new LinearLayout.LayoutParams(-1,dp(44)));
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        FrameLayout content=new FrameLayout(this){@Override public boolean dispatchHoverEvent(MotionEvent e){
            boolean result=super.dispatchHoverEvent(e);
            if(e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT&&(e.getX()<0||e.getY()<0||e.getX()>=getWidth()||e.getY()>=getHeight()))if(settingsPopup!=null)settingsPopup.dismiss();
            return result;
        }};content.addView(scroll);android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(0xEF101914);background.setCornerRadius(dp(9));background.setStroke(dp(1),BORDER);content.setBackground(background);
        int width=Math.min(screen.getWidth()-dp(12),Math.round(dp(310)*scale));
        content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(screen.getHeight()-dp(12),View.MeasureSpec.AT_MOST));
        popupNaturalHeight=content.getMeasuredHeight();popupX=popupY=popupW=popupH=-1;
        settingsPopup=new PopupWindow(content,width,popupNaturalHeight,true);settingsPopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));settingsPopup.setOutsideTouchable(true);settingsPopup.setElevation(dp(6));settingsPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        close.setOnClickListener(v->settingsPopup.dismiss());connectionSettings.setOnClickListener(v->{settingsPopup.dismiss();showSettings();});
        settingsPopup.setOnDismissListener(()->{gear.setAlpha(.32f);scanLabel=null;immerse();});gear.setAlpha(1f);
        int[] location=new int[2];gear.getLocationInWindow(location);settingsPopup.showAtLocation(screen,Gravity.TOP|Gravity.LEFT,Math.max(0,location[0]+gear.getWidth()-width),location[1]);
        positionOverlays();
    }
    void showManual(int ch){
        LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(12),dp(8),dp(12),dp(8));
        form.addView(label("Frequenz in MHz"));addSetting(form,freq[ch]);form.addView(label("Symbolrate in kS/s"));addSetting(form,sr[ch]);
        new AlertDialog.Builder(this).setTitle("Kanal "+(ch+1)+" manuell").setView(form)
            .setPositiveButton("Abstimmen",(d,w)->tune(ch)).setNeutralButton("Bake",(d,w)->applySpectrumTune(ch,10491538,1500)).setNegativeButton("Abbrechen",null).show();
    }
    void setViewMode(int selected){
        fullscreenChannel=-1;
        viewMode=Math.max(0,Math.min(2,selected));getPreferences(0).edit().putInt("viewMode",viewMode).apply();
        mode.setSelection(viewMode);layoutChannels();
    }
    void toggleVideoFullscreen(int ch){
        if(fullscreenChannel>=0)fullscreenChannel=-1;
        else {fullscreenChannel=ch;hideChatKeyboard();}
        for(TextView hint:volumeHints)if(hint!=null)hint.setVisibility(View.GONE);
        layoutChannels();immerse();
    }
    void updateTools(){
        boolean fullscreen=fullscreenChannel>=0;
        updateCardFrames(fullscreen);
        if(toolbar!=null)toolbar.setVisibility(View.GONE);
        boolean showChat=chatVisible&&!fullscreen;
        if(spectrumBlock!=null)spectrumBlock.setVisibility(!fullscreen&&!composingChat()&&spectrumEnabled?View.VISIBLE:View.GONE);
        if(videoArea!=null)videoArea.setVisibility(View.VISIBLE);
        if(chatBlock!=null){
            chatBlock.setVisibility(showChat?View.VISIBLE:View.GONE);

        }
        if(chatButton!=null){chatButton.setTextColor(chatVisible?GREEN:Color.GRAY);chatButton.setContentDescription(chatVisible?"Chat ausblenden":"Chat einblenden");}
        if(statusButton!=null)statusButton.setTextColor(statusVisible?GREEN:Color.GRAY);
        if(spectrumButton!=null)spectrumButton.setTextColor(spectrumEnabled?GREEN:Color.GRAY);
        if(viewButton!=null)viewButton.setText(viewMode==0?"1+2":Integer.toString(viewMode));
        for(int ch=0;ch<2;ch++){if(dataRows[ch]!=null)dataRows[ch].setVisibility(!fullscreen&&statusVisible?View.VISIBLE:View.GONE);if(playback[ch]!=null)playback[ch].setVisibility(!fullscreen&&(diagnosticsVisible||!relayOnline)?View.VISIBLE:View.GONE);if(badges[ch]!=null)badges[ch].setVisibility(View.GONE);}
    }
    void linearSize(View v,int width,int height,float weight){
        LinearLayout.LayoutParams old=(LinearLayout.LayoutParams)v.getLayoutParams();
        if(old.width!=width||old.height!=height||Math.abs(old.weight-weight)>.001f)v.setLayoutParams(new LinearLayout.LayoutParams(width,height,weight));
    }
    float videoAspect(int ch){
        float aspect=16f/9;
        if(players[ch]!=null){
            org.videolan.libvlc.interfaces.IMedia.VideoTrack t=players[ch].getCurrentVideoTrack();
            if(t!=null&&t.width>0&&t.height>0){aspect=(float)t.width/t.height;if(t.sarNum>0&&t.sarDen>0)aspect*=t.sarNum/(float)t.sarDen;if(t.orientation>=4)aspect=1/aspect;}
        }
        return aspect;
    }
    void allocateSpace(){
        if(chatBlock==null||root.getWidth()==0||root.getHeight()==0)return;
        updateSeparators();
        boolean wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        boolean expand=chatVisible&&fullscreenChannel<0;
        if(expand){
            int spectrumPixels=spectrumBlock.getVisibility()==View.VISIBLE?spectrumBlock.getMeasuredHeight():0;
            View entry=chatEntry;
            int inputPixels=Math.max(dp(36),entry.getMeasuredHeight())+chatBlock.getPaddingTop()+chatBlock.getPaddingBottom()+dividerHeight(chatDivider);
            ArrayList<Float> aspects=new ArrayList<>();
            for(int ch=0;ch<2;ch++)if(panels[ch].getVisibility()==View.VISIBLE)aspects.add(videoAspect(ch));
            float[] ratios=new float[aspects.size()];for(int i=0;i<ratios.length;i++)ratios[i]=aspects.get(i);
            int available=root.getHeight()-root.getPaddingTop()-root.getPaddingBottom()-spectrumPixels-inputPixels-dividerHeight(videoEndDivider)-dividerHeight(spectrumEndDivider)-dividerHeight(topDivider)-dividerHeight(bottomDivider);
            int width=root.getWidth()-root.getPaddingLeft()-root.getPaddingRight();
            int gap=videoDivider.getVisibility()==View.VISIBLE?dp(7):0;
            int height=wide?VideoLayoutModel.landscapeVideoHeight(available,width,ratios,chatHistoryHeight(),gap,dp(2)):
                VideoLayoutModel.portraitVideoHeight(available,width,ratios,chatHistoryHeight(),gap,dp(2));
            linearSize(videoArea,-1,height,0);
            for(int ch=0;ch<2;ch++)if(panels[ch].getVisibility()==View.VISIBLE)linearSize(panels[ch],wide?0:-1,wide?-1:0,wide?1:1/videoAspect(ch));
            linearSize(chatBlock,-1,0,1);linearSize(chatFrame,-1,0,1);
        }else{
            linearSize(videoArea,-1,0,1);linearSize(chatBlock,-1,-2,0);
            linearSize(chatFrame,-1,chatHistoryHeight(),0);
        }
    }
    void buildChannel(int ch){
        panels[ch]=new FrameLayout(this);panels[ch].setBackgroundColor(BG);
        views[ch]=new RoundedVideoLayout(this,BG,BORDER,dp(10),Math.max(1,dp(1)));views[ch].setKeepScreenOn(true);
        panels[ch].addView(views[ch],new FrameLayout.LayoutParams(-1,-1));
        info[ch]=label("K"+(ch+1)+" · Verbinde");info[ch].setTextSize(12);info[ch].setSingleLine();info[ch].setEllipsize(TextUtils.TruncateAt.END);info[ch].setBackgroundColor(0x30000000);info[ch].setShadowLayer(dp(2),0,dp(1),Color.BLACK);info[ch].setTextColor(ch==0?GREEN:Color.rgb(255,198,105));
        info[ch].setBackgroundColor(Color.TRANSPARENT);
        dataRows[ch]=new LinearLayout(this);dataRows[ch].setGravity(Gravity.CENTER_VERTICAL);dataRows[ch].setBackgroundColor(0x60000000);
        modeBadges[ch]=label("A");modeBadges[ch].setTextColor(info[ch].getCurrentTextColor());modeBadges[ch].setGravity(Gravity.CENTER);modeBadges[ch].setTypeface(info[ch].getTypeface());
        modeBadges[ch].setPadding(dp(3),0,dp(3),0);dataRows[ch].addView(modeBadges[ch],new LinearLayout.LayoutParams(-2,-1));
        dataRows[ch].addView(info[ch],new LinearLayout.LayoutParams(0,-1,1));
        panels[ch].addView(dataRows[ch],new FrameLayout.LayoutParams(-1,dp(27),Gravity.TOP|Gravity.LEFT));
        playback[ch]=label("Wiedergabe startet");playback[ch].setTextSize(11);playback[ch].setBackgroundColor(0xB0000000);
        FrameLayout.LayoutParams diagnostic=new FrameLayout.LayoutParams(-1,dp(24),Gravity.BOTTOM);diagnostic.bottomMargin=dp(28);panels[ch].addView(playback[ch],diagnostic);
        badges[ch]=label("K"+(ch+1));badges[ch].setTextSize(11);badges[ch].setBackgroundColor(0x90000000);panels[ch].addView(badges[ch],new FrameLayout.LayoutParams(-2,dp(27),Gravity.BOTTOM|Gravity.LEFT));
        volumeHints[ch]=label("");volumeHints[ch].setTextSize(23);volumeHints[ch].setGravity(Gravity.CENTER);volumeHints[ch].setBackgroundColor(0xC0000000);volumeHints[ch].setVisibility(View.GONE);
        panels[ch].addView(volumeHints[ch],new FrameLayout.LayoutParams(dp(190),dp(68),Gravity.CENTER));
        panels[ch].addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->positionOverlays());
        installVideoGestures(ch);
        freq[ch]=field("10491.538");freq[ch].setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        sr[ch]=field("1500");sr[ch].setInputType(InputType.TYPE_CLASS_NUMBER);
    }
    void installVideoGestures(int ch){
        final boolean[] adjusting={false};final float[] anchor={0};final int[] initial={0};
        GestureDetector gestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onSingleTapConfirmed(MotionEvent e){volumeHints[ch].setText("Lautstärke "+volumes[ch]+" %");volumeHints[ch].setVisibility(View.VISIBLE);ui.postDelayed(()->{if(!adjusting[0])volumeHints[ch].setVisibility(View.GONE);},1200);return true;}
            @Override public boolean onDoubleTap(MotionEvent e){toggleVideoFullscreen(ch);return true;}
            @Override public void onLongPress(MotionEvent e){adjusting[0]=true;anchor[0]=e.getY();initial[0]=volumes[ch];panels[ch].performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);volumeHints[ch].setText("Lautstärke "+volumes[ch]+" %");volumeHints[ch].setVisibility(View.VISIBLE);}
        });
        panels[ch].setOnTouchListener((view,event)->{
            gestures.onTouchEvent(event);
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&adjusting[0]){
                int value=Math.max(0,Math.min(100,Math.round(initial[0]+(anchor[0]-event.getY())*100/Math.max(dp(120),view.getHeight()))));
                if(value!=volumes[ch]){volumes[ch]=value;updateAudio();volumeHints[ch].setText("Lautstärke "+value+" %");}
            }
            if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
                if(adjusting[0]){adjusting[0]=false;getPreferences(0).edit().putInt("volume"+ch,volumes[ch]).apply();volumeHints[ch].setVisibility(View.GONE);}
            }
            return true;
        });
    }
    final boolean[] videoAttached=new boolean[2];

    void showPlayerVideo(int ch,boolean visible){
        if(visible==videoAttached[ch])return;
        MediaPlayer player=players[ch];
        if(visible){
            // Keep video composition within the receiver panel.
            player.attachViews(views[ch],null,false,true);
            player.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT);
        }else{
            // Detach the video surface while keeping the live audio stream running.
            player.detachViews();
        }
        videoAttached[ch]=visible;
    }

    void layoutChannels(){
        if(videoArea==null||panels[1]==null)return;
        boolean wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        videoArea.setOrientation(wide?0:1);
        boolean composing=composingChat();
        int selected=VideoLayoutModel.selection(fullscreenChannel,viewMode,composing);
        int playing=VideoLayoutModel.playbackSelection(selected,viewMode,composing);
        for(int ch=0;ch<2;ch++){
            panels[ch].setVisibility(selected==0||selected==ch+1?View.VISIBLE:View.GONE);
            panels[ch].setLayoutParams(new LinearLayout.LayoutParams(wide?0:-1,wide?-1:0,1));
        }
        
        updateAudio();updateTools();ui.post(()->positionOverlays());
        for(int ch=0;ch<2;ch++){
            boolean play=connected&&foreground&&(playing==0||playing==ch+1);
            if(play){
                if(players[ch]==null)startPlayer(ch);
                showPlayerVideo(ch,selected==0||selected==ch+1);
                if(!playingRequested[ch]){playingRequested[ch]=true;players[ch].play();}
            }else releasePlayer(ch);
        }
    }
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(settingsPopup!=null)settingsPopup.dismiss();spectrum.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(spectrumHeight())));layoutChannels();immerse();}
    void connect(){
        String candidate=address.getText().toString().trim();if(!candidate.contains("://"))candidate="http://"+candidate;
        try{URL u=new URL(candidate);if(!(u.getProtocol().equals("http")||u.getProtocol().equals("https"))||u.getHost().isEmpty()||u.getUserInfo()!=null||u.getQuery()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new Exception();}
        catch(Exception e){Toast.makeText(this,"Bitte nur http://Pi-IP:8090 eingeben",Toast.LENGTH_LONG).show();return;}
        base=candidate.replaceAll("/+$","");generation++;connected=true;relayOnline=false;scan=new AutoScanController();
        getPreferences(0).edit().putString("server",base).apply();
        for(int ch=0;ch<2;ch++){wasLocked[ch]=false;lastService[ch]="";lastFrequency[ch]=0;lastRate[ch]=0;releasePlayer(ch);}layoutChannels();connection.setText("Verbinde mit "+base);
    }
    void releasePlayer(int ch){
        MediaPlayer old=players[ch];players[ch]=null;playingRequested[ch]=false;videoAttached[ch]=false;
        if(old!=null){old.setEventListener(null);old.stop();old.detachViews();old.release();}
    }
    void restartPlayer(int ch){releasePlayer(ch);layoutChannels();}
    void startPlayer(int ch){
        releasePlayer(ch);
        int gen=generation;
        MediaPlayer player=new MediaPlayer(vlc);players[ch]=player;
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
            if(message!=null){String value=message;ui.post(()->{if(players[ch]==player){playback[ch].setText(value);positionOverlays();}});}
            if(event.type==MediaPlayer.Event.EncounteredError||event.type==MediaPlayer.Event.EndReached){
                ui.post(()->{if(players[ch]==player)playback[ch].setText("Stream unterbrochen · neuer Versuch in 3 s");});
                ui.postDelayed(()->{if(connected&&foreground&&gen==generation&&players[ch]==player)restartPlayer(ch);},3000);
            }
        });
        updateAudio();
    }
    void updateAudio(){
        for(int ch=0;ch<2;ch++){
            if(players[ch]!=null)players[ch].setVolume(mixMuted?0:volumes[ch]);
            if(badges[ch]!=null)badges[ch].setText("K"+(ch+1)+" · "+(mixMuted?"stumm":volumes[ch]+" %"));
        }
        if(audioButton!=null){audioButton.setText(mixMuted?"Stumm":"Mix");audioButton.setTextColor(mixMuted?Color.GRAY:GREEN);}
        getPreferences(0).edit().putBoolean("mixMuted",mixMuted).apply();
    }
    void selectSpectrumSignal(int ch,int frequency,int rate,boolean manual){
        if(!connected)return;
        if(manual){
            final int[] rates={33,66,125,250,333,500,1000,1500,2000};String[] labels=new String[rates.length];
            for(int i=0;i<rates.length;i++)labels[i]=rates[i]+" kS/s";
            new AlertDialog.Builder(this).setTitle(String.format(Locale.GERMAN,"Kanal %d · %.3f MHz",ch+1,frequency/1000.0))
                .setItems(labels,(dialog,which)->applySpectrumTune(ch,frequency,rates[which])).setNegativeButton("Abbrechen",null).show();
        }else applySpectrumTune(ch,frequency,rate);
    }
    void applySpectrumTune(int ch,int frequency,int rate){
        setAutomatic(false);
        freq[ch].setText(String.format(Locale.US,"%.3f",frequency/1000.0));sr[ch].setText(Integer.toString(rate));
        if(viewMode>0&&viewMode!=ch+1)setViewMode(ch+1);
        Toast.makeText(this,String.format(Locale.GERMAN,"Kanal %d: %.3f MHz / %d kS/s",ch+1,frequency/1000.0,rate),Toast.LENGTH_SHORT).show();tune(ch);
    }
    void chooseSpectrumSource(){
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Spektrumquelle")
            .setSingleChoiceItems(new String[]{"BATC / AMSAT-UK","Lokaler QO-100-Spektrumserver"},localSpectrum?1:0,
                (choices,which)->{
                    choices.dismiss();
                    if(which==0)setSpectrumSource(false,spectrumAddress);
                    else if(spectrumAddress.isEmpty())editSpectrumAddress();
                    else setSpectrumSource(true,spectrumAddress);
                })
            .setNeutralButton("Lokale Adresse",(choices,which)->editSpectrumAddress())
            .setNegativeButton("Abbrechen",null).create();
        dialog.show();
    }
    String defaultSpectrumAddress(){
        try{
            String host=new URI(base).getHost();
            return host==null?"":new URI("http",null,host,8096,null,null,null).toString();
        }
        catch(URISyntaxException|IllegalArgumentException e){return "";}
    }
    void editSpectrumAddress(){
        EditText input=field(spectrumAddress.isEmpty()?defaultSpectrumAddress():spectrumAddress);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("http://Pi-IP:8096");
        LinearLayout content=new LinearLayout(this);
        content.setPadding(dp(22),0,dp(22),0);
        content.addView(input,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Lokaler Spektrumserver")
            .setView(content).setNegativeButton("Abbrechen",null).setPositiveButton("Verwenden",null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            String candidate=input.getText().toString().trim();
            if(!candidate.contains("://"))candidate="http://"+candidate;
            try{
                URI uri=new URI(candidate);
                String path=uri.getRawPath();
                if(!("http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme()))
                        ||uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null
                        ||!(path.isEmpty()||path.equals("/"))||uri.getPort()==0||uri.getPort()>65535)throw new URISyntaxException(candidate,"Adresse");
                setSpectrumSource(true,candidate.replaceAll("/+$",""));
                dialog.dismiss();
            }catch(URISyntaxException e){input.setError("Bitte eine Adresse wie http://Pi-IP:8096 eingeben");}
        });
    }
    void setSpectrumSource(boolean local,String sourceAddress){
        if(local==localSpectrum&&sourceAddress.equals(spectrumAddress))return;
        spectrumAddress=sourceAddress;
        localSpectrum=local;
        spectrumGeneration++;
        getPreferences(0).edit().putBoolean("localSpectrum",local).putString("spectrumAddress",sourceAddress).apply();
        scan=new AutoScanController(scan);
        scan.spectrum(java.util.Collections.emptyList(),-100000);
        spectrum.clearSource();
        spectrumStatus.setText(local?"Verbinde mit lokalem Spektrum …":"Verbinde mit BATC-Spektrum …");
        Toast.makeText(this,local?"Spektrumquelle: Lokal":"Spektrumquelle: BATC",Toast.LENGTH_SHORT).show();
    }
    void pollSpectrum(){
        int gen=generation,sourceGen=spectrumGeneration;
        boolean local=localSpectrum;
        if(!foreground||(!spectrumEnabled&&!automatic)||(!connected&&!local))return;
        String source=local?spectrumAddress:base;
        try{
            JSONObject data=new JSONObject(requestAt(source,"/api/spectrum",null));
            ui.post(()->{if(gen!=generation||sourceGen!=spectrumGeneration||!foreground)return;try{spectrum.setData(data,local);
                if(data.optBoolean("online")){
                    ArrayList<AutoScanController.Signal> detected=new ArrayList<>();JSONArray signals=data.getJSONArray("signals");
                    for(int i=0;i<signals.length();i++){JSONObject s=signals.getJSONObject(i);detected.add(new AutoScanController.Signal(s.getInt("frequency_khz"),s.getInt("symbolrate_ksps"),s.getInt("start_khz"),s.getInt("end_khz")));}
                    scan.spectrum(detected,android.os.SystemClock.elapsedRealtime());
                }
                spectrumStatus.setText(data.getBoolean("online")?(local?"Lokales Spektrum":"BATC / AMSAT-UK")+" · Symbolrate geschätzt":(local?"Lokales Spektrum offline":"BATC-Spektrum offline · Pi-Internetzugang prüfen"));}catch(JSONException e){spectrumStatus.setText("Spektrumformat unbekannt");}});
        }catch(Exception e){ui.post(()->{if(gen==generation&&sourceGen==spectrumGeneration&&foreground){
            boolean missing="HTTP 404".equals(e.getMessage());
            spectrumStatus.setText(missing?(local?"Bitte den lokalen Spektrumserver aktualisieren":"Spektrum benötigt den neuen Pi-Installer"):"Spektrum vorübergehend nicht erreichbar");
            if(local)spectrum.showUnavailable(missing?"Spektrumserver aktualisieren":"Lokales Spektrum nicht erreichbar");
        }});}
    }
    String request(String path,String body)throws Exception{
        return requestAt(base,path,body);
    }
    String requestAt(String server,String path,String body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(server+path).openConnection();
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
            ui.post(()->{if(gen!=generation||!connected)return;connection.setText("Verbunden · "+base);if(!relayOnline){relayOnline=true;updateTools();}
                for(int ch=0;ch<2;ch++)try{
                    JSONObject p=channels.getJSONObject(ch);String margin=p.isNull("margin_db")?"—":String.format(Locale.GERMAN,"%.1f",p.getDouble("margin_db"));
                    String mer=p.isNull("mer_db")?"—":String.format(Locale.GERMAN,"%.1f",p.getDouble("mer_db"));
                    boolean locked=p.getBoolean("locked");String service=p.getString("service");
                    int currentFrequency=p.getInt("frequency_khz"),currentRate=p.getInt("symbolrate_ksps");
                    scan.receiver(ch,currentFrequency,currentRate,locked,android.os.SystemClock.elapsedRealtime());
                    boolean changedService=!lastService[ch].isEmpty()&&!service.isEmpty()&&!lastService[ch].equals(service);
                    boolean changedTune=lastFrequency[ch]>0&&(Math.abs(currentFrequency-lastFrequency[ch])>20||Math.abs(currentRate-lastRate[ch])>Math.max(5,lastRate[ch]/20));
                    if(locked&&(changedService||changedTune||(!wasLocked[ch]&&lastFrequency[ch]>0)))restartPlayer(ch);
                    wasLocked[ch]=locked;if(locked){lastFrequency[ch]=currentFrequency;lastRate[ch]=currentRate;if(!service.isEmpty())lastService[ch]=service;}
                    spectrum.setTuner(ch,currentFrequency,service,locked);
                    info[ch].setText("K"+(ch+1)+" "+(locked?service:p.getBoolean("online")?"Suche":"Offline")+" · MER "+mer+" · D"+margin+" · "+String.format(Locale.US,"%.3f · %dk",currentFrequency/1000.0,currentRate));
                }catch(JSONException e){info[ch].setText("Statusformat nicht erkannt");}
            });
        }catch(Exception e){ui.post(()->{if(gen==generation){relayOnline=false;connection.setText("Keine Statusverbindung · "+base+" · "+e.getMessage());for(int ch=0;ch<2;ch++){playback[ch].setText("Pi-Verbindung fehlt · Zahnrad → Verbindung und Chat");info[ch].setText("K"+(ch+1)+" · Keine Verbindung");spectrum.setTuner(ch,lastFrequency[ch],"",false);}updateTools();}});}
    }
    void tune(int ch){
        setAutomatic(false);
        if(!connected){Toast.makeText(this,"Zuerst verbinden",Toast.LENGTH_SHORT).show();return;}
        try{
            int khz=new BigDecimal(freq[ch].getText().toString().replace(',','.')).multiply(new BigDecimal(1000)).intValueExact();
            int rate=Integer.parseInt(sr[ch].getText().toString());
            if(khz<9894000||khz>12200000||rate<33||rate>27500)throw new Exception();
            String body=new JSONObject().put("frequency_khz",khz).put("symbolrate_ksps",rate).toString();
            int gen=generation;
            tuningNetwork.execute(()->{try{if(gen!=generation)return;request("/api/tune/"+ch,body);ui.post(()->{if(gen==generation&&connected)restartPlayer(ch);});}catch(Exception e){ui.post(()->Toast.makeText(this,"Umschalten: "+e.getMessage(),Toast.LENGTH_LONG).show());}});
        }catch(Exception e){Toast.makeText(this,"Frequenz in MHz und Symbolrate in kS/s pruefen",Toast.LENGTH_LONG).show();}
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event){
        if(gear!=null&&(settingsPopup==null||!settingsPopup.isShowing())&&event.getActionMasked()==MotionEvent.ACTION_HOVER_MOVE){
            int[] p=new int[2];gear.getLocationOnScreen(p);
            float dx=Math.max(0,Math.max(p[0]-event.getRawX(),event.getRawX()-(p[0]+gear.getWidth())));
            float dy=Math.max(0,Math.max(p[1]-event.getRawY(),event.getRawY()-(p[1]+gear.getHeight())));
            gear.setAlpha(.25f+.75f*(1-Math.min(1,(float)Math.hypot(dx,dy)/dp(100))));
        }
        return super.dispatchGenericMotionEvent(event);
    }
    @Override protected void onRestoreInstanceState(Bundle state){super.onRestoreInstanceState(state);root.requestFocus();}
    @Override public void onBackPressed(){
        if(fullscreenChannel>=0){toggleVideoFullscreen(fullscreenChannel);return;}
        super.onBackPressed();
    }
    @Override protected void onStart(){super.onStart();foreground=true;if(chat!=null)chat.start();if(!connected)connect();else layoutChannels();immerse();}
    @Override protected void onStop(){foreground=false;if(spectrum!=null)spectrum.cancelTouches();if(settingsPopup!=null)settingsPopup.dismiss();if(chat!=null)chat.stop();for(int ch=0;ch<2;ch++)releasePlayer(ch);super.onStop();}
    @Override protected void onDestroy(){if(chat!=null)chat.close();connected=false;generation++;network.shutdownNow();tuningNetwork.shutdownNow();ui.removeCallbacksAndMessages(null);for(int ch=0;ch<2;ch++)releasePlayer(ch);if(vlc!=null)vlc.release();super.onDestroy();}
}
