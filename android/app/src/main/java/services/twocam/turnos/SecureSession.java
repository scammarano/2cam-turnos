package services.twocam.turnos;
import android.content.Context;
import android.security.KeyPairGeneratorSpec;
import android.util.Base64;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Calendar;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;
import org.json.JSONObject;
/** API22-compatible RSA AndroidKeyStore envelope + AES-GCM session storage. No plaintext token in preferences. */
final class SecureSession {
    private static final String ALIAS="2cam-turnos-session-v1";
    private final Context context;
    SecureSession(Context context){this.context=context.getApplicationContext();}
    private KeyStore store()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            Calendar start=Calendar.getInstance(),end=Calendar.getInstance();end.add(Calendar.YEAR,20);
            KeyPairGenerator gen=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");
            gen.initialize(new KeyPairGeneratorSpec.Builder(context).setAlias(ALIAS).setSubject(new X500Principal("CN=2CamTurnosLocal")).setSerialNumber(BigInteger.ONE).setStartDate(start.getTime()).setEndDate(end.getTime()).setKeySize(2048).build());gen.generateKeyPair();
        }return ks;
    }
    synchronized void save(String text)throws Exception {
        KeyStore ks=store();byte[] key=new byte[32],iv=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(key);random.nextBytes(iv);
        Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");aes.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
        Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.ENCRYPT_MODE,ks.getCertificate(ALIAS).getPublicKey());
        JSONObject value=new JSONObject().put("key",b64(rsa.doFinal(key))).put("iv",b64(iv)).put("data",b64(aes.doFinal(text.getBytes("UTF-8"))));
        if(!context.getSharedPreferences("secure",0).edit().putString("session",value.toString()).commit())throw new Exception("No se pudo guardar el enlace cifrado");
    }
    synchronized String load()throws Exception{
        String raw=context.getSharedPreferences("secure",0).getString("session",null);if(raw==null)return null;JSONObject value=new JSONObject(raw);KeyStore ks=store();
        Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.DECRYPT_MODE,ks.getKey(ALIAS,null));byte[] key=rsa.doFinal(unb64(value.getString("key")));
        Cipher aes=Cipher.getInstance("AES/GCM/NoPadding");aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,unb64(value.getString("iv"))));return new String(aes.doFinal(unb64(value.getString("data"))),"UTF-8");
    }
    synchronized void clear()throws Exception{if(!context.getSharedPreferences("secure",0).edit().remove("session").commit())throw new Exception("No se pudo borrar el enlace");}
    private static String b64(byte[] b){return Base64.encodeToString(b,Base64.NO_WRAP);}
    private static byte[] unb64(String s){return Base64.decode(s,Base64.NO_WRAP);}
}
