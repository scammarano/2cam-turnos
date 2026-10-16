package services.twocam.turnos;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
final class Api {
    static final class Failure extends Exception {final int status;Failure(int s,String m){super(m);status=s;}}
    static JSONObject request(String base,String path,String token,String key,JSONObject body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(Endpoint.normalize(base)+"/api/v1"+path).openConnection();
        try{
            c.setConnectTimeout(3000);c.setReadTimeout(4000);c.setInstanceFollowRedirects(false);c.setRequestMethod(body==null?"GET":"POST");
            c.setRequestProperty("Accept","application/json");
            if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);if(key!=null)c.setRequestProperty("Idempotency-Key",key);
            if(body!=null){byte[] bytes=body.toString().getBytes("UTF-8");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setFixedLengthStreamingMode(bytes.length);try(java.io.OutputStream out=c.getOutputStream()){out.write(bytes);}}
            int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
            if(stream==null)throw new Failure(status,"Sin respuesta del Fire");ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=stream){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>1048576)throw new Exception("Respuesta demasiado grande");}}
            JSONObject result=new JSONObject(new String(out.toByteArray(),"UTF-8"));
            if(status!=200)throw new Failure(status,result.optString("error","Solicitud rechazada"));return result;
        }finally{c.disconnect();}
    }
}
