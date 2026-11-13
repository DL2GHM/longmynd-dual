package de.dl2ghm.longmyndmobil;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** BATC wideband chat. Engine.IO 4 / Socket.IO default namespace over HTTPS polling.
 * Independent of the Pi; no identity or message is sent until explicitly requested.
 * Own implementation, GPL-3.0-or-later. */
public final class ChatClient {
    public interface Listener {
        void state(boolean online, String nickname, String detail);
        void message(JSONObject message, boolean history);
        void sent(String text, boolean success);
    }
    private static final String ENDPOINT="https://eshail.batc.org.uk/wb/chat/socket.io/?EIO=4&transport=polling&room=eshail-wb";
    private final String endpoint;
    private final Listener listener;
    private final Object postLock=new Object();
    private final ExecutorService receiver=Executors.newSingleThreadExecutor();
    private final ExecutorService sender=Executors.newSingleThreadExecutor();
    private volatile boolean running, closed;
    private volatile String desiredNick="";
    private volatile Session session;
    private static class Session {
        volatile boolean active=true,ready=false;
        boolean closeQueued=false;
        volatile String sid="",confirmedNick="",requestedNick=null;
        volatile HttpURLConnection receive;
        int timeout=40000;
    }
    public ChatClient(Listener listener){this(ENDPOINT,listener);}
    ChatClient(String endpoint,Listener listener){this.endpoint=endpoint;this.listener=listener;}
    public synchronized void start(){
        if(running||closed)return;
        running=true;
        receiver.execute(this::run);
    }
    public synchronized void stop(){
        running=false;
        Session s=session;
        if(s!=null){s.active=false;if(s.receive!=null)s.receive.disconnect();finish(s);}
    }
    public void close(){stop();closed=true;receiver.shutdownNow();sender.shutdown();}
    private void enqueue(Runnable task){try{sender.execute(task);}catch(RejectedExecutionException ignored){}}
    private void finish(Session s){
        synchronized(s){if(s.closeQueued||s.sid.isEmpty())return;s.closeQueued=true;}
        enqueue(()->{try{post(s,"41\u001e1");}catch(Exception ignored){}});
    }
    public void login(String nickname){
        desiredNick=nickname;
        Session s=session;
        if(s!=null&&s.ready&&s.active)enqueue(()->register(s));
    }
    public boolean canSend(){Session s=session;return running&&s!=null&&s.active&&s.ready&&!s.confirmedNick.isEmpty()&&s.confirmedNick.equals(desiredNick);}
    public void send(String text){
        Session s=session;
        if(!canSend()){listener.sent(text,false);return;}
        enqueue(()->{
            boolean ok=false;
            try{if(s==session&&s.active&&canSend()){post(s,event("message",new JSONObject().put("message",text)));ok=true;}}
            catch(Exception e){s.active=false;if(s.receive!=null)s.receive.disconnect();}
            listener.sent(text,ok);
        });
    }
    private boolean current(Session s){return running&&!closed&&session==s&&s.active;}
    private void state(Session s,String detail){if(current(s))listener.state(s.ready,s.confirmedNick,detail);}
    private void register(Session s){
        if(!current(s)||!s.ready)return;
        try{
            s.confirmedNick="";
            String nick=desiredNick;
            s.requestedNick=nick;
            post(s,event("setnick",new JSONObject().put("nick",nick)));
            state(s,!s.confirmedNick.isEmpty()?"Angemeldet als "+s.confirmedNick:nick.isEmpty()?"Chat verbunden · nicht angemeldet":"Chat-Anmeldung läuft …");
        }catch(Exception e){s.active=false;if(s.receive!=null)s.receive.disconnect();}
    }
    static String event(String name,JSONObject data){return "42"+new JSONArray().put(name).put(data).toString();}
    private void run(){
        int retry=1000;
        while(running&&!closed){
            Session s=new Session();session=s;
            state(s,"Chat wird verbunden …");
            try{
                String opening=http(s,null);
                if(!opening.startsWith("0"))throw new IOException("Chat-Handshake");
                JSONObject handshake=new JSONObject(opening.substring(1));
                s.sid=handshake.getString("sid");
                s.timeout=Math.max(10000,Math.min(120000,handshake.optInt("pingInterval",25000)+handshake.optInt("pingTimeout",5000)+5000));
                post(s,"40");
                while(current(s)){
                    String packets=http(s,null);
                    for(String packet:packets.split("\u001e")){
                        if(!current(s))break;
                        if(packet.equals("2")){enqueue(()->{try{if(current(s))post(s,"3");}catch(Exception e){s.active=false;if(s.receive!=null)s.receive.disconnect();}});}
                        else if(packet.startsWith("40")){
                            s.ready=true;retry=1000;state(s,"Chat verbunden · nicht angemeldet");
                            if(!desiredNick.isEmpty())enqueue(()->register(s));
                        }else if(packet.startsWith("42"))handleEvent(s,new JSONArray(packet.substring(2)));
                        else if(packet.equals("1")||packet.startsWith("41")||packet.startsWith("44"))throw new IOException("Chat getrennt");
                    }
                }
            }catch(Exception e){/* Reconnect below; never replay user messages. */}
            finally{
                s.active=false;s.ready=false;s.confirmedNick="";
                // Release anonymous/named presence promptly, even when going into the background.
                finish(s);
            }
            if(running&&!closed){
                listener.state(false,"","Chat unterbrochen · neuer Versuch …");
                try{Thread.sleep(retry);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
                retry=Math.min(20000,retry*2);
            }
        }
    }
    private void handleEvent(Session s,JSONArray packet)throws JSONException {
        String name=packet.getString(0);JSONObject data=packet.optJSONObject(1);if(data==null)return;
        if(name.equals("history")){
            JSONArray messages=data.optJSONArray("history");
            if(messages!=null)for(int i=Math.max(0,messages.length()-100);i<messages.length();i++)if(current(s))listener.message(messages.getJSONObject(i),true);
        }else if(name.equals("message")){if(current(s))listener.message(data,false);}
        if(name.equals("nicks")||name.equals("history")){
            JSONArray nicks=data.optJSONArray("nicks");String wanted=desiredNick;
            boolean found=false;if(nicks!=null)for(int i=0;i<nicks.length();i++)if(wanted.equals(nicks.optString(i)))found=true;
            // A server nick-list confirms registration; HTTP success alone is insufficient.
            s.confirmedNick=!wanted.isEmpty()&&wanted.equals(s.requestedNick)&&found?wanted:"";
            state(s,s.confirmedNick.isEmpty()?(wanted.isEmpty()?"Chat verbunden · nicht angemeldet":"Chat-Anmeldung wird bestätigt …"):"Angemeldet als "+s.confirmedNick);
        }
    }
    private void post(Session s,String body)throws IOException {synchronized(postLock){http(s,body);}}
    private String http(Session s,String body)throws IOException {
        String url=endpoint+"&t="+System.nanoTime()+(s.sid.isEmpty()?"":"&sid="+URLEncoder.encode(s.sid,"UTF-8"));
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(body==null?s.timeout:8000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Origin","https://eshail.batc.org.uk");
        if(body==null)s.receive=c;
        try{
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","text/plain;charset=UTF-8");byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            if(c.getResponseCode()!=200)throw new IOException("Chat HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>2097152)throw new IOException("Chat-Antwort zu groß");}
                return out.toString("UTF-8");
            }
        }finally{if(body==null&&s.receive==c)s.receive=null;c.disconnect();}
    }
}
