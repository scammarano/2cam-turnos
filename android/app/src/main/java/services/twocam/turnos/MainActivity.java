package services.twocam.turnos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native Fire + tablet UI. Network work is off the UI thread; all controls wait for server acceptance. */
public final class MainActivity extends Activity {
    private static final int INK=Color.rgb(235,245,248),MUTED=Color.rgb(158,185,197),GREEN=Color.rgb(161,245,203);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private LinearLayout page;
    private TextView status,ticket,meta,counts,receipt;
    private EditText address,name,code;
    private Spinner counter;
    private Button retry;
    private final ArrayList<Button> commands=new ArrayList<>();
    private SecureSession secure;
    private JSONObject session,snapshot;
    private LanDiscovery discovery;
    private TextToSpeech speech;
    private ToneGenerator tone;
    private boolean speechReady,polling,busy,connected;
    private String mode="home";
    private long generation,lastSound=-1;
    private SharedPreferences preferences;
    AlertDialog testDialog;
    private final Runnable tick=new Runnable(){public void run(){if(mode.equals("host"))renderHost();else if(mode.equals("client")&&session!=null)poll();main.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);preferences=getSharedPreferences("settings",0);secure=new SecureSession(this);discovery=new LanDiscovery(this);
        try{String saved=secure.load();if(saved!=null)session=new JSONObject(saved);}catch(Exception error){showError("No se pudo leer el enlace cifrado. No se ha borrado.");}
        speech=new TextToSpeech(this,result->{if(result==TextToSpeech.SUCCESS&&speech!=null){int language=speech.setLanguage(new Locale("es"));speechReady=language>=0;}});
        try{tone=new ToneGenerator(AudioManager.STREAM_MUSIC,70);}catch(Exception ignored){}
        if(preferences.getString("mode","").equals("host"))showHost();else if(session!=null)showClient();else home();main.post(tick);
    }
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setPadding(0,dp(6),0,dp(6));page.addView(view);return view;}
    private void base(String title){generation++;polling=false;connected=false;discovery.stop();commands.clear();page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(24),dp(20),dp(24),dp(24));page.setBackgroundColor(Color.rgb(16,43,59));ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);setContentView(scroll);text("2Cam Turnos",24,GREEN);text(title,18,INK);text("0.2.0 · PRUEBAS LOCALES",11,MUTED);}
    private Button button(String label,String tag,Runnable action){Button view=new Button(this);view.setText(label);view.setTag(tag);view.setAllCaps(false);view.setMinHeight(dp(48));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(6);page.addView(view,lp);view.setOnClickListener(v->action.run());return view;}
    private EditText input(String hint,String tag,boolean numeric){EditText view=new EditText(this);view.setHint(hint);view.setTag(tag);view.setTextColor(INK);view.setHintTextColor(MUTED);view.setSingleLine(true);if(numeric)view.setInputType(InputType.TYPE_CLASS_NUMBER);page.addView(view,new LinearLayout.LayoutParams(-1,dp(52)));return view;}
    private Spinner spinner(String[] values){Spinner view=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);view.setAdapter(adapter);view.setBackgroundColor(Color.rgb(40,65,78));page.addView(view,new LinearLayout.LayoutParams(-1,dp(48)));return view;}
    private void home(){mode="home";base("Selecciona cómo utilizar este equipo");button("Pantalla principal · Fire / Android TV","hostMode",()->pin(()->showHost()));button("Control remoto · tablet / teléfono","clientMode",()->showClient());text("Instala este mismo APK en ambos equipos y conéctalos a la misma red privada. No necesita Internet ni una computadora encendida.",16,MUTED);text("Esta versión tiene una sala, una cola FIFO y cuatro ventanillas. La publicidad y la integración con la APK Digital Signage van en una entrega posterior.",14,MUTED);}
    private void showHost(){
        mode="host";preferences.edit().putString("mode","host").apply();base("Pantalla principal");lastSound=-1;
        try{HostRuntime.get(this);Intent service=new Intent(this,HostService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);}catch(Exception error){showError("No se pudo abrir el almacenamiento de turnos. No se han borrado los datos.");return;}
        status=text("Iniciando red local…",14,MUTED);status.setTag("hostAddress");
        ticket=text("Espere su llamado",42,GREEN);ticket.setTag("hostTicket");ticket.setGravity(Gravity.CENTER);
        meta=text("",20,INK);meta.setGravity(Gravity.CENTER);counts=text("",16,MUTED);
        button("Enlazar control / visor","openPair",()->pin(()->pairing()));
        button("Dispositivos enlazados · revocar","controls",()->pin(()->controls()));
        button("Configurar sala y servicio","configure",()->pin(()->configure()));
        button("Audio: "+(preferences.getBoolean("audio",true)?"activado":"silenciado"),"audio",()->{preferences.edit().putBoolean("audio",!preferences.getBoolean("audio",true)).apply();showHost();});
        button("Elegir otro modo","menu",()->pin(()->home()));
        button("Detener servidor local","stopHost",()->pin(()->{new AlertDialog.Builder(this).setTitle("¿Detener Turnos?").setMessage("Los controles dejarán de operar hasta volver a abrir Pantalla principal. Los tickets quedan guardados.").setPositiveButton("Detener",(d,w)->{stopService(new Intent(this,HostService.class));preferences.edit().remove("mode").apply();home();}).setNegativeButton("Cancelar",null).show();}));
        renderHost();
    }
    private void renderHost(){if(!mode.equals("host")||status==null)return;try{
        HostRuntime host=HostRuntime.get(this);JSONObject state=host.queue.displaySnapshot();status.setText((host.address.isEmpty()?host.networkStatus:host.address)+"\n"+state.getString("name")+" · "+state.getString("service"));renderState(state);
        JSONObject announcement=state.optJSONObject("last_announcement");long latest=announcement==null?0:announcement.getLong("sequence");
        if(lastSound<0)lastSound=latest;
        if(latest>lastSound){lastSound=latest;if(preferences.getBoolean("audio",true)&&System.currentTimeMillis()-announcement.getLong("at")<5000){JSONObject payload=announcement.getJSONObject("payload");boolean active=false;JSONArray calls=state.getJSONArray("active_calls");for(int i=0;i<calls.length();i++)if(calls.getJSONObject(i).getString("ticket_id").equals(payload.getString("ticket_id")))active=true;if(active){if(tone!=null)tone.startTone(ToneGenerator.TONE_PROP_BEEP,160);if(speechReady)speech.speak("Turno "+payload.getString("ticket_label")+", "+payload.getString("counter_label"),TextToSpeech.QUEUE_FLUSH,null,"call-"+latest);}}}
    }catch(Exception error){status.setText("No se pudo leer el estado local. Los datos no se han reiniciado.");}}
    private void renderState(JSONObject state)throws Exception{
        JSONArray calls=state.getJSONArray("active_calls");StringBuilder tickets=new StringBuilder(),places=new StringBuilder();
        for(int i=0;i<calls.length();i++){JSONObject call=calls.getJSONObject(i);if(i>0){tickets.append("   ·   ");places.append("   |   ");}tickets.append(call.getString("ticket_label"));places.append(call.getString("counter_label")).append(call.getString("state").equals("serving")?" · En atención":" · Llamado");}
        ticket.setText(tickets.length()==0?"Espere su llamado":tickets.toString());meta.setText(places.toString());counts.setText("En espera: "+state.getInt("waiting")+"     Finalizados: "+state.getInt("finished")+"     Secuencia: "+state.getLong("sequence"));
    }
    private String pinHash(String pin,byte[] salt)throws Exception{return Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(new PBEKeySpec(pin.toCharArray(),salt,100000,256)).getEncoded(),Base64.NO_WRAP);}
    private void pin(Runnable action){
        if(System.currentTimeMillis()<preferences.getLong("pinBlockedUntil",0)){showError("Espera 30 segundos antes de volver a intentar el PIN");return;}
        boolean setup=!preferences.contains("pinHash");LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        EditText value=new EditText(this);value.setTag("adminPin");value.setHint(setup?"Crea un PIN de 6 dígitos":"PIN de la pantalla");value.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);box.addView(value);
        testDialog=new AlertDialog.Builder(this).setTitle(setup?"Proteger la pantalla":"Administración local").setMessage(setup?"Guarda este PIN. Se pedirá para enlazar, configurar y revocar dispositivos.":"Introduce tu PIN de 6 dígitos").setView(box).setPositiveButton("Continuar",null).setNegativeButton("Cancelar",null).create();
        testDialog.setOnShowListener(d->testDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            String entered=value.getText().toString();if(!entered.matches("[0-9]{6}")){value.setError("Exactamente 6 dígitos");return;}
            if(setup){byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);if(!preferences.edit().putString("pinSalt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("pinHash",pinHash(entered,salt)).commit())throw new Exception();}
            else{String actual=pinHash(entered,Base64.decode(preferences.getString("pinSalt",""),Base64.NO_WRAP));if(!MessageDigest.isEqual(actual.getBytes("UTF-8"),preferences.getString("pinHash","").getBytes("UTF-8"))){int tries=preferences.getInt("pinTries",0)+1;preferences.edit().putInt("pinTries",tries%5).putLong("pinBlockedUntil",tries>=5?System.currentTimeMillis()+30000:0).apply();value.setError("PIN incorrecto");if(tries>=5)testDialog.dismiss();return;}}
            preferences.edit().putInt("pinTries",0).remove("pinBlockedUntil").apply();testDialog.dismiss();action.run();
        }catch(Exception error){showError("No se pudo guardar/verificar el PIN");}}));testDialog.show();
    }
    private void pairing(){try{
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);Spinner role=new Spinner(this),place=new Spinner(this);
        role.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Supervisor · todas las operaciones","Operador · solo su ventanilla","Recepción · solo emisión","Visor secundario · solo lectura"}));
        place.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Ventanilla 1","Ventanilla 2","Ventanilla 3","Ventanilla 4"}));box.addView(role);box.addView(place);
        new AlertDialog.Builder(this).setTitle("Permisos del nuevo dispositivo").setView(box).setPositiveButton("Mostrar código",(d,w)->{try{String[] roles={"supervisor","operator","issuer","viewer"};JSONObject result=HostRuntime.get(this).queue.openPairing(roles[role.getSelectedItemPosition()],place.getSelectedItemPosition()+1);TextView display=new TextView(this);display.setText(result.getString("code"));display.setTextSize(48);display.setGravity(Gravity.CENTER);testDialog=new AlertDialog.Builder(this).setTitle("Código de enlace · vence en 2 minutos").setMessage("En la tablet busca esta pantalla y escribe este código. Un solo uso; máximo 3 controles (visores aparte).").setView(display).setPositiveButton("Cerrar",null).show();}catch(Exception error){showError(error.getMessage());}}).setNegativeButton("Cancelar",null).show();
    }catch(Exception error){showError(error.getMessage());}}
    private void controls(){try{JSONArray devices=HostRuntime.get(this).queue.controls();String[] names=new String[devices.length()];for(int i=0;i<names.length;i++){JSONObject c=devices.getJSONObject(i);names[i]=c.getString("name")+" · "+c.getString("role")+" · V"+c.getInt("counter");}
        if(names.length==0){showError("No hay dispositivos enlazados");return;}
        new AlertDialog.Builder(this).setTitle("Selecciona un dispositivo para revocarlo").setItems(names,(d,index)->new AlertDialog.Builder(this).setTitle("¿Revocar este dispositivo?").setMessage(names[index]).setPositiveButton("Revocar",(dd,w)->{try{HostRuntime.get(this).queue.revoke(devices.getJSONObject(index).getString("id"));}catch(Exception error){showError(error.getMessage());}}).setNegativeButton("Cancelar",null).show()).setNegativeButton("Cerrar",null).show();
    }catch(Exception error){showError(error.getMessage());}}
    private void configure(){try{
        JSONObject state=HostRuntime.get(this).queue.displaySnapshot();LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        EditText room=new EditText(this),service=new EditText(this),prefix=new EditText(this);room.setHint("Nombre de sala");room.setText(state.getString("name"));service.setHint("Servicio");service.setText(state.getString("service"));prefix.setHint("Prefijo (A, B, etc.)");box.addView(room);box.addView(service);box.addView(prefix);
        new AlertDialog.Builder(this).setTitle("Configurar sala local").setMessage("Prefijo vacío: conserva el actual. Después de emitir tickets, el prefijo no se cambia.").setView(box).setPositiveButton("Guardar",(d,w)->{try{String p=prefix.getText().toString().trim();HostRuntime host=HostRuntime.get(this);if(p.isEmpty())p=new JSONObject(host.queue.exportJson()).getString("prefix");host.queue.configure(room.getText().toString(),service.getText().toString(),p);renderHost();}catch(Exception error){showError(error.getMessage());}}).setNegativeButton("Cancelar",null).show();
    }catch(Exception error){showError(error.getMessage());}}
    private void showClient(){
        mode="client";preferences.edit().putString("mode","client").apply();base(session==null?"Enlazar con la pantalla":"Control remoto local");
        status=text("Sin conexión",15,MUTED);status.setTag("connection");
        if(session==null){
            text("1. Abre «Enlazar control» en el Fire.\n2. Busca su pantalla o escribe la dirección que muestra.\n3. Introduce el código de 6 dígitos.",16,INK);
            address=input("IP del Fire, ej. 192.168.1.40:8990","serverAddress",false);name=input("Nombre de esta tablet","deviceName",false);name.setText("Control "+Build.MODEL);code=input("Código de enlace del Fire","pairCode",true);
            button("Buscar pantallas Turnos en esta red","discover",()->search());button("Enlazar","pair",()->pairClient());button("Volver","back",()->home());search();return;
        }
        text(session.optString("base")+" · "+session.optString("role"),13,MUTED);
        ticket=text("Esperando estado del Fire…",36,GREEN);ticket.setTag("clientTicket");meta=text("",18,INK);counts=text("",15,MUTED);receipt=text("",18,GREEN);receipt.setTag("receipt");
        if(session.optString("role").equals("supervisor")){counter=spinner(new String[]{"Ventanilla 1","Ventanilla 2","Ventanilla 3","Ventanilla 4"});}else counter=null;
        String role=session.optString("role");
        if(role.equals("supervisor")||role.equals("issuer"))commandButton("Emitir ticket","issue");
        if(role.equals("supervisor")||role.equals("operator")){commandButton("Llamar siguiente","call");commandButton("Repetir llamado","repeat");commandButton("Iniciar atención","start");commandButton("Finalizar atención","finish");}
        retry=button("Reintentar orden pendiente (misma clave)","retry",()->sendPending());retry.setVisibility(session.has("pending")?View.VISIBLE:View.GONE);
        button("Ver otro modo","menu",()->home());button("Borrar enlace de esta tablet","unlink",()->new AlertDialog.Builder(this).setTitle("¿Borrar enlace?").setMessage("Si hay una orden pendiente, su resultado puede estar confirmado en el Fire. Revoca también este dispositivo en el Fire para liberar su plaza.").setPositiveButton("Borrar enlace",(d,w)->{try{secure.clear();session=null;snapshot=null;showClient();}catch(Exception error){showError(error.getMessage());}}).setNegativeButton("Cancelar",null).show());
        snapshot=session.optJSONObject("snapshot");if(snapshot!=null)try{renderState(snapshot);status.setText("Último estado guardado · esperando conexión");}catch(Exception ignored){}enableCommands(false);poll();
    }
    private void search(){final long current=generation;status.setText("Buscando durante 12 segundos. Si no aparece, escribe la IP del Fire.");discovery.start((n,base)->{if(current==generation&&session==null){Button found=button(n+" · "+base,"found-"+base,()->address.setText(base));found.setTextSize(13);}});}
    private void pairClient(){
        if(busy)return;final String base,device=name.getText().toString(),pin=code.getText().toString();try{base=Endpoint.normalize(address.getText().toString());if(!pin.matches("[0-9]{6}"))throw new Exception("Código: 6 dígitos");if(device.trim().isEmpty()||device.length()>50)throw new Exception("Nombre: 1 a 50 caracteres");}catch(Exception error){showError(error.getMessage());return;}
        busy=true;final long current=generation;status.setText("Enlazando…");io.execute(()->{try{
            JSONObject found=Api.request(base,"/discovery",null,null,null);if(!found.getString("mode").equals("turnos"))throw new Exception("No es una pantalla Turnos");
            JSONObject result=Api.request(base,"/pair",null,null,new JSONObject().put("name",device).put("code",pin));if(!found.getString("screen_id").equals(result.getString("screen_id")))throw new Exception("La pantalla cambió durante el enlace");result.put("base",base);
            main.post(()->{busy=false;if(current!=generation)return;try{secure.save(result.toString());session=result;showClient();}catch(Exception error){showError("El Fire aceptó el enlace, pero no se pudo guardar. Revócalo en el Fire y vuelve a enlazar.");}});
        }catch(Exception error){main.post(()->{busy=false;if(current==generation)status.setText("No se enlazó: "+error.getMessage()+". Si el Fire muestra este control como enlazado, revócalo antes de repetir.");});}});
    }
    private void commandButton(String title,String type){Button button=button(title,type,()->prepare(type));commands.add(button);}
    private void enableCommands(boolean enabled){for(Button button:commands)button.setEnabled(enabled&&!busy&&session!=null&&!session.has("pending"));if(retry!=null)retry.setEnabled(!busy);}
    private int chosenCounter(){return counter!=null?counter.getSelectedItemPosition()+1:session.optInt("counter",1);}
    private void prepare(String type){try{
        if(!connected||busy||session.has("pending"))return;int place=chosenCounter();JSONObject body=new JSONObject().put("type",type).put("counter",place);
        if(type.equals("repeat")||type.equals("start")||type.equals("finish")){
            JSONObject selected=null;JSONArray calls=snapshot.getJSONArray("active_calls");for(int i=0;i<calls.length();i++)if(calls.getJSONObject(i).getInt("counter_id")==place)selected=calls.getJSONObject(i);
            if(selected==null)throw new Exception("No hay un llamado activo en esta ventanilla");body.put("ticket_id",selected.getString("ticket_id"));
        }
        JSONObject next=new JSONObject(session.toString()).put("pending",new JSONObject().put("key",UUID.randomUUID().toString()).put("body",body));secure.save(next.toString());session=next;retry.setVisibility(View.VISIBLE);sendPending();
    }catch(Exception error){showError(error.getMessage());}}
    private void sendPending(){
        if(session==null||!session.has("pending")||busy)return;busy=true;enableCommands(false);status.setText("Esperando confirmación del Fire…");final long current=generation;final JSONObject binding;
        try{binding=new JSONObject(session.toString());}catch(Exception error){busy=false;return;}
        io.execute(()->{try{
            JSONObject pending=binding.getJSONObject("pending");JSONObject result=Api.request(binding.getString("base"),"/commands",binding.getString("token"),pending.getString("key"),pending.getJSONObject("body"));
            main.post(()->{busy=false;if(current!=generation)return;try{JSONObject next=new JSONObject(session.toString());next.remove("pending");secure.save(next.toString());session=next;retry.setVisibility(View.GONE);JSONObject t=result.getJSONObject("ticket");receipt.setText(t.getString("ticket_label")+" · "+t.getString("state")+" · Confirmado por el Fire");poll();}catch(Exception error){status.setText("Aceptado por el Fire. No se pudo limpiar la orden guardada: reintenta con la misma clave.");}});
        }catch(Exception error){main.post(()->{busy=false;if(current!=generation)return;connected=false;enableCommands(false);
            if(error instanceof Api.Failure&&((Api.Failure)error).status>=400&&((Api.Failure)error).status<500){try{JSONObject next=new JSONObject(session.toString());next.remove("pending");secure.save(next.toString());session=next;retry.setVisibility(View.GONE);}catch(Exception ignored){}status.setText("Orden rechazada: "+error.getMessage());}
            else status.setText("Sin confirmación. El Fire puede haberla aceptado. Reintenta la orden pendiente; no envíes otra.");
        });}});
    }
    private void poll(){
        if(session==null||polling||busy)return;polling=true;final long current=generation;final String base=session.optString("base"),token=session.optString("token"),id=session.optString("screen_id");
        io.execute(()->{try{JSONObject result=Api.request(base,"/snapshot",token,null,null);if(!id.equals(result.getString("screen_id")))throw new Exception("La identidad de la pantalla cambió");
            main.post(()->{if(current!=generation)return;polling=false;try{long previous=snapshot==null?-1:snapshot.optLong("sequence");snapshot=result;connected=true;renderState(result);status.setText(session.has("pending")?"Conectado · orden pendiente de confirmar. Pulsa Reintentar.":"Conectado al Fire · estado confirmado");enableCommands(true);if(result.getLong("sequence")!=previous){JSONObject next=new JSONObject(session.toString()).put("snapshot",result);secure.save(next.toString());session=next;}}catch(Exception error){connected=false;enableCommands(false);status.setText("No se pudo guardar/mostrar el estado. Reabre la app sin borrar sus datos.");}});
        }catch(Exception error){main.post(()->{if(current!=generation)return;polling=false;connected=false;enableCommands(false);status.setText("Desconectado · ESTADO DESACTUALIZADO. "+(session.has("pending")?"Hay una orden pendiente de confirmar.":"No se enviarán nuevos llamados."));});}});
    }
    private void showError(String message){if(isFinishing())return;new AlertDialog.Builder(this).setTitle("2Cam Turnos").setMessage(message==null?"No se pudo completar la operación":message).setPositiveButton("Cerrar",null).show();}
    @Override public void onBackPressed(){if(mode.equals("host"))pin(()->home());else if(!mode.equals("home"))home();else super.onBackPressed();}
    @Override public void onDestroy(){generation++;main.removeCallbacksAndMessages(null);discovery.stop();io.shutdownNow();if(speech!=null)speech.shutdown();if(tone!=null)tone.release();super.onDestroy();}
}
