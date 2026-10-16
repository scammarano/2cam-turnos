package services.twocam.turnoscore;

import org.json.JSONArray;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

/** Single persistent local authority. This module belongs to Turnos, never to the Signage player. */
public final class LocalQueue {
    public interface Persistence { void write(String json) throws Exception; }
    public interface Clock { long now(); String day(); }
    public static final class Failure extends Exception {
        public final int status;
        public Failure(int status, String message) { super(message); this.status=status; }
    }
    private final Persistence persistence;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private JSONObject data;
    private String code="", pairRole="";
    private int pairCounter, attempts;
    private long expiry;
    public LocalQueue(String saved, Persistence persistence) throws Exception {
        this(saved,persistence,new Clock(){
            public long now(){return System.currentTimeMillis();}
            public String day(){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date());}
        });
    }
    public LocalQueue(String saved, Persistence persistence, Clock clock) throws Exception {
        this.persistence=persistence; this.clock=clock;
        if(saved!=null){
            data=new JSONObject(saved);
            if(data.getInt("schema")!=1)throw new Failure(503,"Datos de una versión incompatible. No se han borrado.");
            data.getJSONArray("tickets");data.getJSONArray("controls");data.getJSONArray("events");data.getJSONObject("requests");
        }else{
            data=new JSONObject().put("schema",1).put("id",UUID.randomUUID().toString()).put("name","Sala principal")
                .put("service","Atención general").put("prefix","A").put("day",clock.day()).put("number",0)
                .put("sequence",0).put("callSequence",0).put("tickets",new JSONArray()).put("controls",new JSONArray())
                .put("events",new JSONArray()).put("requests",new JSONObject());
            persistence.write(data.toString());
        }
    }
    private interface Change { JSONObject apply() throws Exception; }
    private JSONObject transaction(Change change) throws Exception {
        JSONObject previous=copy(data);
        try{JSONObject result=change.apply();persistence.write(data.toString());return copy(result);}
        catch(Exception error){data=previous;throw error;}
    }
    public static JSONObject copy(JSONObject object) throws Exception {return new JSONObject(object.toString());}
    private static String text(String value,int max) throws Failure {
        if(value==null||value.trim().isEmpty()||value.trim().length()>max)throw new Failure(422,"Texto requerido (máximo "+max+" caracteres)");
        return value.trim();
    }
    private static String hash(String value) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));StringBuilder out=new StringBuilder();
        for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private static boolean same(String a,String b) throws Exception {return MessageDigest.isEqual(a.getBytes("UTF-8"),b.getBytes("UTF-8"));}
    private String secret(){byte[] bytes=new byte[32];random.nextBytes(bytes);StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    public synchronized String id() throws Exception{return data.getString("id");}
    public synchronized String name(){return data.optString("name");}
    public synchronized JSONObject configure(String name,String service,String prefix) throws Exception {
        final String n=text(name,60),s=text(service,60),p=text(prefix,3).toUpperCase(Locale.ROOT);
        if(!p.matches("[A-Z]{1,3}"))throw new Failure(422,"Prefijo: 1 a 3 letras A-Z");
        if(!p.equals(data.getString("prefix"))&&data.getJSONArray("tickets").length()>0)throw new Failure(409,"El prefijo no cambia después de emitir tickets");
        return transaction(()->{data.put("name",n).put("service",s).put("prefix",p);event("turnos.area_configured",null,"local-admin",false);return snapshot(null);});
    }
    private int controlCount(boolean viewers) throws Exception {
        int count=0;JSONArray controls=data.getJSONArray("controls");
        for(int i=0;i<controls.length();i++)if(controls.getJSONObject(i).getString("role").equals("viewer")==viewers)count++;
        return count;
    }
    public synchronized JSONObject openPairing(String role,int counter) throws Exception {
        if(!role.equals("supervisor")&&!role.equals("operator")&&!role.equals("issuer")&&!role.equals("viewer"))throw new Failure(422,"Rol inválido");
        if(counter<1||counter>4)throw new Failure(422,"Puesto: 1 a 4");
        if(controlCount(role.equals("viewer"))>=(role.equals("viewer")?5:3))throw new Failure(409,role.equals("viewer")?"Máximo cinco visores secundarios":"Máximo tres controles. Desvincula uno antes de enlazar otro.");
        pairRole=role;pairCounter=counter;attempts=0;expiry=clock.now()+120000;code=String.valueOf(100000+random.nextInt(900000));
        return new JSONObject().put("code",code).put("expires_at_ms",expiry).put("role",role).put("counter",counter);
    }
    public synchronized JSONArray controls() throws Exception {
        JSONArray result=new JSONArray(),controls=data.getJSONArray("controls");
        for(int i=0;i<controls.length();i++){JSONObject c=controls.getJSONObject(i);result.put(new JSONObject().put("id",c.getString("id")).put("name",c.getString("name")).put("role",c.getString("role")).put("counter",c.getInt("counter")));}
        return result;
    }
    public synchronized void revoke(String id) throws Exception {
        transaction(()->{JSONArray next=new JSONArray(),controls=data.getJSONArray("controls");for(int i=0;i<controls.length();i++)if(!controls.getJSONObject(i).getString("id").equals(id))next.put(controls.getJSONObject(i));data.put("controls",next);event("turnos.control_revoked",null,"local-admin",false);return new JSONObject();});
    }
    private JSONObject access(String token) throws Exception {
        if(token==null||token.length()!=64)throw new Failure(401,"Enlace no autorizado");
        String digest=hash(token);JSONArray controls=data.getJSONArray("controls");
        for(int i=0;i<controls.length();i++)if(same(digest,controls.getJSONObject(i).getString("token_hash")))return controls.getJSONObject(i);
        throw new Failure(401,"Control revocado o no autorizado");
    }
    private JSONObject pair(JSONObject body) throws Exception {
        if(code.isEmpty()||clock.now()>=expiry||attempts>=5)throw new Failure(403,"Código cerrado o vencido. Abre otro en la pantalla.");
        if(!same(body.optString("code"),code)){attempts++;throw new Failure(403,"Código incorrecto");}
        boolean viewer=pairRole.equals("viewer");
        if(controlCount(viewer)>=(viewer?5:3))throw new Failure(409,"Límite de dispositivos enlazados");
        final String name=text(body.optString("name"),50);
        JSONObject response=transaction(()->{
            String token=secret(),id=UUID.randomUUID().toString();
            data.getJSONArray("controls").put(new JSONObject().put("id",id).put("name",name).put("role",pairRole).put("counter",pairCounter).put("token_hash",hash(token)));
            event("turnos.control_paired",null,id,false);
            return new JSONObject().put("token",token).put("control_id",id).put("screen_id",data.getString("id")).put("role",pairRole).put("counter",pairCounter);
        });code="";expiry=0;return response;
    }
    private JSONObject active(int counter) throws Exception {
        JSONArray tickets=data.getJSONArray("tickets");
        for(int i=0;i<tickets.length();i++){JSONObject t=tickets.getJSONObject(i);if(t.optInt("counter")==counter&&(t.getString("state").equals("called")||t.getString("state").equals("serving")))return t;}
        return null;
    }
    private JSONObject publicTicket(JSONObject ticket) throws Exception {
        return new JSONObject().put("ticket_id",ticket.getString("id")).put("ticket_label",ticket.getString("label"))
            .put("state",ticket.getString("state")).put("counter_id",ticket.optInt("counter"))
            .put("counter_label",ticket.optInt("counter")>0?"Ventanilla "+ticket.getInt("counter"):"");
    }
    private JSONObject event(String type,JSONObject ticket,String actor,boolean sound) throws Exception {
        long sequence=data.getLong("sequence")+1;data.put("sequence",sequence);
        JSONObject payload=ticket==null?new JSONObject():publicTicket(ticket);
        payload.put("sound",sound).put("call_sequence",data.getLong("callSequence"));
        JSONObject e=new JSONObject().put("schema","2cam.events.v1").put("event_id",UUID.randomUUID().toString()).put("sequence",sequence)
            .put("occurred_at_ms",clock.now()).put("source","turnos").put("type",type).put("tenant_ref","local:"+data.getString("id"))
            .put("scope",new JSONObject().put("area_id",data.getString("id"))).put("payload",payload).put("actor_ref",actor);
        JSONArray events=data.getJSONArray("events");events.put(e);if(events.length()>500)events.remove(0);return e;
    }
    private JSONObject command(JSONObject control,String key,JSONObject body) throws Exception {
        String role=control.getString("role"),type=body.optString("type");
        if(!type.equals("issue")&&!type.equals("call")&&!type.equals("repeat")&&!type.equals("start")&&!type.equals("finish"))throw new Failure(422,"Acción inválida");
        if(role.equals("viewer")||(role.equals("issuer")&&!type.equals("issue"))||(role.equals("operator")&&type.equals("issue")))throw new Failure(403,"Acción fuera de permisos");
        Object raw=body.opt("counter");int counter=body.optInt("counter",control.getInt("counter"));
        if(raw!=null&&(!(raw instanceof Number)||((Number)raw).doubleValue()!=counter))throw new Failure(422,"Puesto inválido");
        if(counter<1||counter>4)throw new Failure(422,"Puesto: 1 a 4");
        if(!role.equals("supervisor")&&counter!=control.getInt("counter"))throw new Failure(403,"Puesto fuera de permisos");
        if(key==null||!key.matches("[A-Za-z0-9_-]{8,100}"))throw new Failure(422,"Clave idempotente requerida");
        String ticketId=body.optString("ticket_id"),identity=control.getString("id")+":"+key;
        String fingerprint=hash(type+":"+counter+":"+ticketId);
        JSONObject requests=data.getJSONObject("requests"),prior=requests.optJSONObject(identity);
        if(prior!=null){if(!same(prior.getString("hash"),fingerprint))throw new Failure(409,"Clave ya usada para otra operación");return copy(prior.getJSONObject("response"));}
        final int chosen=counter;
        return transaction(()->{
            // Retain accepted commands for 24h; reject overload rather than evict unknown outcomes early.
            JSONArray expired=new JSONArray();Iterator<String> keys=requests.keys();while(keys.hasNext()){String k=keys.next();if(requests.getJSONObject(k).getLong("at")<clock.now()-86400000)expired.put(k);}
            for(int i=0;i<expired.length();i++)requests.remove(expired.getString(i));
            if(requests.length()>=10000)throw new Failure(409,"Límite de operaciones pendientes de retención");
            JSONArray tickets=data.getJSONArray("tickets");JSONObject ticket=null;String eventType;
            if(type.equals("issue")){
                if(tickets.length()>=5000)throw new Failure(409,"Límite local de 5000 tickets; exporta y solicita mantenimiento");
                if(!clock.day().equals(data.getString("day"))){
                    boolean pending=false;for(int i=0;i<tickets.length();i++)if(!tickets.getJSONObject(i).getString("state").equals("finished"))pending=true;
                    data.put("day",clock.day());if(!pending)data.put("number",0);
                }
                int number=data.getInt("number")+1;data.put("number",number);
                ticket=new JSONObject().put("id",UUID.randomUUID().toString()).put("day",clock.day()).put("number",number)
                    .put("label",data.getString("prefix")+"-"+String.format(Locale.ROOT,"%03d",number)).put("state","waiting").put("counter",0).put("issued_at",clock.now());
                tickets.put(ticket);eventType="turnos.ticket_issued";
            }else if(type.equals("call")){
                if(active(chosen)!=null)throw new Failure(409,"El puesto tiene una atención pendiente");
                for(int i=0;i<tickets.length();i++)if(tickets.getJSONObject(i).getString("state").equals("waiting")){ticket=tickets.getJSONObject(i);break;}
                if(ticket==null)throw new Failure(409,"No hay tickets en espera");
                ticket.put("state","called").put("counter",chosen).put("called_at",clock.now());eventType="turnos.ticket_called";
                data.put("callSequence",data.getLong("callSequence")+1);
            }else{
                ticket=active(chosen);
                if(ticket==null||!ticket.getString("id").equals(ticketId))throw new Failure(409,"El llamado cambió. Actualiza el estado antes de operar.");
                String state=ticket.getString("state");
                if(type.equals("repeat")){
                    if(!state.equals("called"))throw new Failure(409,"La atención ya comenzó");
                    eventType="turnos.call_repeated";data.put("callSequence",data.getLong("callSequence")+1);
                }else if(type.equals("start")){
                    if(!state.equals("called"))throw new Failure(409,"La atención ya comenzó");
                    ticket.put("state","serving").put("started_at",clock.now());eventType="turnos.service_started";
                }else{
                    if(!state.equals("serving"))throw new Failure(409,"Inicia la atención antes de finalizar");
                    ticket.put("state","finished").put("finished_at",clock.now());eventType="turnos.service_finished";
                }
            }
            JSONObject e=event(eventType,ticket,control.getString("id"),type.equals("call")||type.equals("repeat"));
            JSONObject result=new JSONObject().put("event_id",e.getString("event_id")).put("sequence",e.getLong("sequence")).put("ticket",publicTicket(ticket));
            requests.put(identity,new JSONObject().put("hash",fingerprint).put("response",result).put("at",clock.now()));return result;
        });
    }
    public synchronized JSONObject displaySnapshot() throws Exception{return snapshot(null);}
    private JSONObject snapshot(JSONObject control) throws Exception {
        JSONArray active=new JSONArray(),recent=new JSONArray();int waiting=0,finished=0;JSONArray tickets=data.getJSONArray("tickets");
        for(int i=0;i<tickets.length();i++){
            JSONObject t=tickets.getJSONObject(i);String state=t.getString("state");
            if(state.equals("waiting"))waiting++;if(state.equals("finished"))finished++;
        }
        for(int counter=1;counter<=4;counter++){JSONObject t=active(counter);if(t!=null)active.put(publicTicket(t));}
        JSONArray events=data.getJSONArray("events");for(int i=events.length()-1;i>=0&&recent.length()<4;i--){JSONObject e=events.getJSONObject(i);if(e.getString("type").equals("turnos.ticket_called"))recent.put(copy(e.getJSONObject("payload")));}
        JSONObject result=new JSONObject().put("schema","2cam.turnos.local.snapshot.v1").put("screen_id",data.getString("id")).put("name",data.getString("name"))
            .put("service",data.getString("service")).put("sequence",data.getLong("sequence")).put("call_sequence",data.getLong("callSequence"))
            .put("active_calls",active).put("recent_calls",recent).put("waiting",waiting).put("finished",finished).put("generated_at_ms",clock.now());
        for(int i=events.length()-1;i>=0;i--){JSONObject e=events.getJSONObject(i);if(e.getJSONObject("payload").optBoolean("sound")){result.put("last_announcement",new JSONObject().put("sequence",e.getLong("sequence")).put("at",e.getLong("occurred_at_ms")).put("payload",copy(e.getJSONObject("payload"))));break;}}
        if(control!=null)result.put("role",control.getString("role")).put("counter",control.getInt("counter"));return result;
    }
    public synchronized JSONObject request(String method,String path,String token,String key,JSONObject body) throws Exception {
        if(method.equals("GET")&&path.equals("/discovery"))return new JSONObject().put("screen_id",id()).put("name",name()).put("mode","turnos").put("controls",controlCount(false)).put("max_controls",3);
        if(method.equals("POST")&&path.equals("/pair"))return pair(body);
        JSONObject control=access(token);
        if(method.equals("GET")&&path.equals("/snapshot"))return snapshot(control);
        if(method.equals("POST")&&path.equals("/commands"))return command(control,key,body);
        if(method.equals("GET")&&path.startsWith("/events?after_sequence=")){
            long after;try{after=Long.parseLong(path.substring("/events?after_sequence=".length()));}catch(Exception e){throw new Failure(422,"Cursor inválido");}
            if(after<0||after>data.getLong("sequence"))throw new Failure(409,"Cursor inválido");
            JSONArray events=data.getJSONArray("events"),result=new JSONArray();
            if(events.length()>0&&after<events.getJSONObject(0).getLong("sequence")-1)throw new Failure(410,"snapshot_required");
            for(int i=0;i<events.length();i++)if(events.getJSONObject(i).getLong("sequence")>after){JSONObject e=copy(events.getJSONObject(i));e.remove("actor_ref");result.put(e);}
            return new JSONObject().put("events",result).put("sequence",data.getLong("sequence"));
        }
        throw new Failure(404,"Ruta Turnos no encontrada");
    }
    public synchronized String exportJson(){return data.toString();}
}
