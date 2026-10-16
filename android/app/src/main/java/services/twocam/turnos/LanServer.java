package services.twocam.turnos;
import org.json.JSONObject;
import services.twocam.turnoscore.LocalQueue;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.io.BufferedInputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
final class LanServer {
    private final LocalQueue queue;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(16));
    private ServerSocket server;private volatile boolean running;
    LanServer(LocalQueue q){queue=q;}
    void start(InetAddress address,int port)throws Exception {
        ServerSocket listener=new ServerSocket();
        try{listener.setReuseAddress(true);listener.bind(new InetSocketAddress(address,port),8);listener.setSoTimeout(1000);server=listener;}
        catch(Exception error){try{listener.close();}catch(Exception ignored){}throw error;}
        running=true;
        new Thread(()->{while(running){try{Socket c=server.accept();try{workers.execute(()->handle(c));}catch(Exception e){c.close();}}catch(java.net.SocketTimeoutException ignored){}catch(Exception e){if(!running)break;}}},"turnos-lan").start();
    }
    void close(){running=false;try{if(server!=null)server.close();}catch(Exception ignored){}workers.shutdownNow();}
    private static String line(BufferedInputStream in,int max)throws Exception {
        StringBuilder result=new StringBuilder();int b;
        while((b=in.read())!=-1&&b!=10){if(b!=13)result.append((char)b);if(result.length()>max)throw new Exception("Cabecera demasiado grande");}
        if(b==-1)throw new Exception("Solicitud incompleta");return result.toString();
    }
    private void handle(Socket socket){try(Socket c=socket){
        try{
            c.setSoTimeout(4000);
            if(!c.getInetAddress().isSiteLocalAddress()&&!c.getInetAddress().isLoopbackAddress()){respond(c,403,new JSONObject().put("error","Solo red privada local"));return;}
            BufferedInputStream in=new BufferedInputStream(c.getInputStream());String[] first=line(in,2048).split(" ");
            if(first.length!=3||!first[2].equals("HTTP/1.1"))throw new Exception();
            HashMap<String,String> headers=new HashMap<>();int total=0;boolean complete=false;
            for(int i=0;i<40;i++){String row=line(in,4096);total+=row.length();if(total>16384)throw new Exception();if(row.isEmpty()){complete=true;break;}int colon=row.indexOf(':');if(colon<1)throw new Exception();String name=row.substring(0,colon).trim().toLowerCase(Locale.ROOT);if(headers.containsKey(name))throw new Exception();headers.put(name,row.substring(colon+1).trim());}
            if(!complete||headers.containsKey("transfer-encoding"))throw new Exception();
            if(headers.containsKey("origin")){respond(c,403,new JSONObject().put("error","Solo clientes nativos, sin Origin web"));return;}
            String method=first[0],path=first[1];if(!method.equals("GET")&&!method.equals("POST")){respond(c,405,new JSONObject().put("error","Método no permitido"));return;}
            if(!path.startsWith("/api/v1/")){respond(c,404,new JSONObject().put("error","Ruta no encontrada"));return;}
            int length=headers.containsKey("content-length")?Integer.parseInt(headers.get("content-length")):0;
            if(length<0||length>8192)throw new Exception();
            if(method.equals("POST")&&(headers.get("content-type")==null||!headers.get("content-type").startsWith("application/json"))){respond(c,415,new JSONObject().put("error","Se requiere JSON"));return;}
            byte[] bytes=new byte[length];int offset=0;while(offset<length){int n=in.read(bytes,offset,length-offset);if(n<1)throw new Exception();offset+=n;}
            String authorization=headers.get("authorization");String token=authorization!=null&&authorization.startsWith("Bearer ")?authorization.substring(7):null;
            JSONObject body=method.equals("POST")?new JSONObject(new String(bytes,"UTF-8")):new JSONObject();
            respond(c,200,queue.request(method,path.substring(7),token,headers.get("idempotency-key"),body));
        }catch(LocalQueue.Failure e){respond(c,e.status,new JSONObject().put("error",e.getMessage()));}
        catch(Exception e){try{respond(c,503,new JSONObject().put("error","Solicitud inválida o no se pudo guardar; reintenta con la misma clave"));}catch(Exception ignored){}}
    }catch(Exception ignored){}}
    private static void respond(Socket c,int status,JSONObject value)throws Exception {
        byte[] body=value.toString().getBytes("UTF-8");java.io.OutputStream out=c.getOutputStream();
        out.write(("HTTP/1.1 "+status+" Turnos\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n\r\n").getBytes("US-ASCII"));out.write(body);out.flush();
    }
}
