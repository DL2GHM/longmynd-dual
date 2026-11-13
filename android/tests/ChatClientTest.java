package de.dl2ghm.longmyndmobil;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Local protocol fixture only. Never posts to the public BATC chat. */
public class ChatClientTest {
    static void check(boolean ok,String detail){if(!ok)throw new AssertionError(detail);}
    static void await(CountDownLatch latch,String detail)throws Exception{check(latch.await(8,TimeUnit.SECONDS),detail);}
    public static void main(String[] args)throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        ExecutorService pool=Executors.newCachedThreadPool();server.setExecutor(pool);
        BlockingQueue<String> packets=new LinkedBlockingQueue<>();
        AtomicInteger registrations=new AtomicInteger(),pongs=new AtomicInteger(),opens=new AtomicInteger();
        AtomicReference<String> received=new AtomicReference<>();
        CountDownLatch history=new CountDownLatch(1),online=new CountDownLatch(1),login=new CountDownLatch(1),sent=new CountDownLatch(1),echo=new CountDownLatch(1),logout=new CountDownLatch(1),again=new CountDownLatch(1);
        String text="73 · Grüße \"QO-100\" <b>Test</b>";
        server.createContext("/chat",e->{
            String reply="ok";
            try{
                if(e.getRequestMethod().equals("GET")){
                    if(!e.getRequestURI().getQuery().contains("sid=")){
                        opens.incrementAndGet();reply="0{\"sid\":\"test-session\",\"pingInterval\":200,\"pingTimeout\":2000}";
                    }else{reply=packets.poll(300,TimeUnit.MILLISECONDS);if(reply==null)reply="2";}
                }else{
                    String body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                    if(body.equals("40")){
                        packets.add("40{\"sid\":\"namespace\"}\u001e42[\"history\",{\"nicks\":[],\"history\":[{\"time\":\"2026-11-05T17:00:00Z\",\"name\":\"TEST\",\"message\":\"Grüße\"}]}]");
                    }else if(body.equals("3"))pongs.incrementAndGet();
                    else if(body.startsWith("42")){
                        JSONArray event=new JSONArray(body.substring(2));JSONObject data=event.getJSONObject(1);
                        if(event.getString(0).equals("setnick")){
                            registrations.incrementAndGet();String nick=data.getString("nick");
                            packets.add("42"+new JSONArray().put("nicks").put(new JSONObject().put("nicks",nick.isEmpty()?new JSONArray():new JSONArray().put(nick))));
                        }else if(event.getString(0).equals("message")){
                            received.set(data.getString("message"));
                            packets.add("42"+new JSONArray().put("message").put(new JSONObject().put("time","2026-11-05T17:01:00Z").put("name","DL2GHM").put("message",received.get())));
                        }
                    }
                }
            }catch(Exception ex){reply="1";}
            byte[] bytes=reply.getBytes(StandardCharsets.UTF_8);
            try{e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);}finally{e.close();}
        });
        server.start();
        ChatClient client=new ChatClient("http://127.0.0.1:"+server.getAddress().getPort()+"/chat?EIO=4&transport=polling",new ChatClient.Listener(){
            public void state(boolean connected,String nickname,String detail){
                if(connected){online.countDown();if(nickname.equals("DL2GHM")){login.countDown();if(opens.get()>=2)again.countDown();}else if(registrations.get()>1&&detail.contains("nicht angemeldet"))logout.countDown();}
            }
            public void message(JSONObject message,boolean old){if(old&&message.optString("message").equals("Grüße"))history.countDown();if(!old&&text.equals(message.optString("message")))echo.countDown();}
            public void sent(String message,boolean ok){if(ok&&message.equals(text))sent.countDown();}
        });
        try{
            client.start();await(online,"namespace connection");await(history,"history Unicode decoding");
            check(registrations.get()==0,"read-only start must not register a callsign");check(!client.canSend(),"anonymous cannot send");
            client.login("DL2GHM");await(login,"server nick confirmation");check(client.canSend(),"registered send state");
            client.send(text);await(sent,"POST accepted");await(echo,"server echo event");check(text.equals(received.get()),"JSON escaping and Unicode round trip");
            Thread.sleep(700);check(pongs.get()>0,"Engine.IO ping/pong");
            packets.add("1");await(again,"reconnect restores explicitly requested nickname");check(opens.get()>=2,"new session after close packet");
            client.login("");await(logout,"logout state");check(!client.canSend(),"logout disables sending");
            client.stop();check(!client.canSend(),"background disables sending");
            System.out.println("PASS: anonymous read, history, login confirmation, Unicode message, heartbeat, reconnect, logout, background");
        }finally{client.close();server.stop(0);pool.shutdownNow();}
    }
}
