package services.twocam.turnos;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONObject;
import services.twocam.turnoscore.LocalQueue;

/** Actual native UI + local HTTP server + AtomicFile + API22 encrypted keystore round trip. No PC backend. */
public class TurnosInstrumentation extends Instrumentation {
    private MainActivity activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{exercise();result.putString("stream","TURNOS_ANDROID_PASS\n");finish(Activity.RESULT_OK,result);}catch(Throwable e){result.putString("stream","TURNOS_ANDROID_FAIL: "+e.getClass().getSimpleName()+" "+e.getMessage()+"\n");finish(Activity.RESULT_CANCELED,result);}}
    private View find(View view,String tag){if(tag.equals(view.getTag()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),tag);if(found!=null)return found;}}return null;}
    private View view(String tag){return find(activity.getWindow().getDecorView(),tag);}
    private void waitFor(String tag,String contains)throws Exception{long end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end){boolean[] ok={false};runOnMainSync(()->{View v=view(tag);ok[0]=v!=null&&(contains==null||(v instanceof TextView&&((TextView)v).getText().toString().contains(contains)));});if(ok[0])return;Thread.sleep(100);}throw new AssertionError(tag+" did not show "+contains);}
    private void click(String tag)throws Exception{long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){boolean[] clicked={false};runOnMainSync(()->{View v=view(tag);if(v!=null&&v.isEnabled()){v.performClick();clicked[0]=true;}});if(clicked[0])return;Thread.sleep(100);}throw new AssertionError("Disabled/missing "+tag);}
    private void launch(){activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private void close(){runOnMainSync(()->activity.finish());waitForIdleSync();}
    private void exercise()throws Exception{
        if(!Endpoint.normalize("192.168.1.40:8990").equals("http://192.168.1.40:8990"))throw new AssertionError("endpoint");
        try{Endpoint.normalize("https://example.com");throw new AssertionError("public URL accepted");}catch(IllegalArgumentException expected){}
        launch();waitFor("hostMode",null);click("hostMode");waitForIdleSync();
        runOnMainSync(()->{if(activity.testDialog==null)throw new AssertionError("PIN setup missing");((EditText)find(activity.testDialog.getWindow().getDecorView(),"adminPin")).setText("123456");activity.testDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();});
        waitFor("hostAddress","http://");HostRuntime host=HostRuntime.get(getTargetContext());host.ensureNetwork();String base=host.address;
        if(base.isEmpty())throw new AssertionError("LAN server not started");
        String code=host.queue.openPairing("supervisor",1).getString("code");close();getTargetContext().getSharedPreferences("settings",0).edit().putString("mode","client").commit();
        launch();click("clientMode");waitFor("serverAddress",null);
        runOnMainSync(()->{((EditText)view("serverAddress")).setText(base);((EditText)view("deviceName")).setText("Tablet emulador");((EditText)view("pairCode")).setText(code);});click("pair");waitFor("connection","Conectado");
        click("issue");waitFor("receipt","A-001");click("call");waitFor("clientTicket","A-001");click("repeat");waitFor("receipt","called");
        // Wait for command completion independently of a label that may already match.
        waitUntil(()->host.queue.displaySnapshot().getLong("call_sequence")==2);
        click("start");waitFor("receipt","serving");click("finish");waitFor("receipt","finished");
        waitUntil(()->host.queue.displaySnapshot().getInt("finished")==1);
        close();SecureSession secure=new SecureSession(getTargetContext());JSONObject binding=new JSONObject(secure.load());
        JSONObject body=new JSONObject().put("type","issue").put("counter",1);String key="android-lost-response-001";
        Api.request(base,"/commands",binding.getString("token"),key,body);binding.put("pending",new JSONObject().put("key",key).put("body",body));secure.save(binding.toString());
        if(getTargetContext().getSharedPreferences("secure",0).getString("session","").contains(binding.getString("token")))throw new AssertionError("plaintext token");
        launch();waitFor("connection","Conectado");click("retry");waitUntil(()->!new JSONObject(secure.load()).has("pending"));
        if(host.queue.displaySnapshot().getInt("waiting")!=1)throw new AssertionError("retry issued duplicate ticket");
        close();JSONObject good=new JSONObject(secure.load()),offline=new JSONObject(good.toString()).put("base","http://127.0.0.1:9");secure.save(offline.toString());
        launch();waitFor("connection","Desconectado");runOnMainSync(()->{if(view("issue").isEnabled()||view("call").isEnabled())throw new AssertionError("offline commands enabled");});close();secure.save(good.toString());
        launch();waitFor("connection","Conectado");click("call");waitFor("clientTicket","A-002");click("start");waitFor("receipt","serving");click("finish");waitFor("receipt","finished");
        String firstId=host.queue.id();getTargetContext().stopService(new Intent(getTargetContext(),HostService.class));waitForIdleSync();HostRuntime.stop();
        HostRuntime restored=HostRuntime.get(getTargetContext());if(!restored.queue.id().equals(firstId)||restored.queue.displaySnapshot().getInt("finished")!=2)throw new AssertionError("persistence restart failed");
        restored.ensureNetwork();JSONObject state=Api.request(restored.address,"/snapshot",good.getString("token"),null,null);if(state.getInt("finished")!=2)throw new AssertionError("restored token failed");
        // Two extra controls fill the three slots; a fourth must fail. Roles are checked through HTTP.
        String receptionCode=restored.queue.openPairing("issuer",1).getString("code");JSONObject reception=Api.request(restored.address,"/pair",null,null,new JSONObject().put("name","Recepción").put("code",receptionCode));
        String operatorCode=restored.queue.openPairing("operator",2).getString("code");Api.request(restored.address,"/pair",null,null,new JSONObject().put("name","Ventanilla 2").put("code",operatorCode));
        try{restored.queue.openPairing("operator",3);throw new AssertionError("fourth control accepted");}catch(LocalQueue.Failure expected){if(expected.status!=409)throw expected;}
        try{Api.request(restored.address,"/commands",reception.getString("token"),"issuer-forbidden-001",new JSONObject().put("type","call").put("counter",1));throw new AssertionError("issuer called next");}catch(Api.Failure expected){if(expected.status!=403)throw expected;}
        close();HostRuntime.stop();
    }
    private interface Check{boolean test()throws Exception;}
    private void waitUntil(Check check)throws Exception{long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){if(check.test())return;Thread.sleep(100);}throw new AssertionError("State wait timed out");}
}
