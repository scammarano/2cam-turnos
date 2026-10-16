package services.twocam.turnoscore;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class LocalQueueTest {
    private JSONObject pair(LocalQueue q,String role,int counter)throws Exception{
        JSONObject code=q.openPairing(role,counter);
        return q.request("POST","/pair",null,null,new JSONObject().put("name",role).put("code",code.getString("code")));
    }
    private JSONObject command(LocalQueue q,JSONObject c,String key,String type,int counter,String id)throws Exception{
        return q.request("POST","/commands",c.getString("token"),key,new JSONObject().put("type",type).put("counter",counter).put("ticket_id",id==null?"":id));
    }
    @Test public void fullCycleAndIdempotentRetrySurviveRestart()throws Exception{
        final String[] disk={null};LocalQueue q=new LocalQueue(null,s->disk[0]=s);JSONObject control=pair(q,"supervisor",1);
        JSONObject issued=command(q,control,"issue-00001","issue",1,null);assertEquals("A-001",issued.getJSONObject("ticket").getString("ticket_label"));
        JSONObject call=command(q,control,"call-000001","call",1,null);String id=call.getJSONObject("ticket").getString("ticket_id");
        assertTrue(call.similar(command(q,control,"call-000001","call",1,null)));
        JSONObject repeat=command(q,control,"repeat-0001","repeat",1,id);
        assertTrue(repeat.similar(command(q,control,"repeat-0001","repeat",1,id)));
        assertEquals(2,q.displaySnapshot().getLong("call_sequence"));
        command(q,control,"start-00001","start",1,id);command(q,control,"finish-0001","finish",1,id);
        LocalQueue restarted=new LocalQueue(disk[0],s->disk[0]=s);
        assertTrue(issued.similar(command(restarted,control,"issue-00001","issue",1,null)));
        assertEquals(1,restarted.displaySnapshot().getInt("finished"));assertEquals(0,restarted.displaySnapshot().getInt("waiting"));
        assertFalse(disk[0].contains(control.getString("token")));
    }
    @Test public void twoOperatorsCannotTakeTheSameTicket()throws Exception{
        LocalQueue q=new LocalQueue(null,s->{});JSONObject supervisor=pair(q,"supervisor",1),a=pair(q,"operator",1),b=pair(q,"operator",2);
        command(q,supervisor,"issue-con-01","issue",1,null);command(q,supervisor,"issue-con-02","issue",1,null);
        CountDownLatch ready=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();AtomicReference<String> ta=new AtomicReference<>(),tb=new AtomicReference<>();
        Thread one=new Thread(()->{try{ready.await();ta.set(command(q,a,"operator-001","call",1,null).getJSONObject("ticket").getString("ticket_id"));}catch(Throwable e){error.set(e);}});
        Thread two=new Thread(()->{try{ready.await();tb.set(command(q,b,"operator-002","call",2,null).getJSONObject("ticket").getString("ticket_id"));}catch(Throwable e){error.set(e);}});
        one.start();two.start();ready.countDown();one.join();two.join();if(error.get()!=null)throw new AssertionError(error.get());
        assertNotEquals(ta.get(),tb.get());assertEquals(2,q.displaySnapshot().getJSONArray("active_calls").length());assertEquals(0,q.displaySnapshot().getInt("waiting"));
    }
    @Test public void fourthControlRejectedViewerSeparateAndRevocationWorks()throws Exception{
        LocalQueue q=new LocalQueue(null,s->{});JSONObject first=pair(q,"supervisor",1);pair(q,"operator",2);pair(q,"issuer",1);
        try{q.openPairing("operator",3);fail();}catch(LocalQueue.Failure e){assertEquals(409,e.status);}
        JSONObject viewer=pair(q,"viewer",1);assertEquals(3,q.request("GET","/discovery",null,null,new JSONObject()).getInt("controls"));
        try{command(q,viewer,"viewer-00001","issue",1,null);fail();}catch(LocalQueue.Failure e){assertEquals(403,e.status);}
        q.revoke(first.getString("control_id"));try{q.request("GET","/snapshot",first.getString("token"),null,new JSONObject());fail();}catch(LocalQueue.Failure e){assertEquals(401,e.status);}
        pair(q,"supervisor",1);
    }
    @Test public void invalidPermissionsTransitionsAndStaleCallAreRejected()throws Exception{
        LocalQueue q=new LocalQueue(null,s->{});JSONObject issuer=pair(q,"issuer",1),operator=pair(q,"operator",2);
        try{command(q,issuer,"wrong-00001","call",1,null);fail();}catch(LocalQueue.Failure e){assertEquals(403,e.status);}
        try{command(q,operator,"wrong-00002","call",1,null);fail();}catch(LocalQueue.Failure e){assertEquals(403,e.status);}
        command(q,issuer,"issue-00001","issue",1,null);JSONObject call=command(q,operator,"call-000001","call",2,null);String id=call.getJSONObject("ticket").getString("ticket_id");
        try{command(q,operator,"finish-early","finish",2,id);fail();}catch(LocalQueue.Failure e){assertEquals(409,e.status);}
        try{command(q,operator,"stale-call-1","start",2,"old-call-id");fail();}catch(LocalQueue.Failure e){assertEquals(409,e.status);}
        assertEquals("called",q.displaySnapshot().getJSONArray("active_calls").getJSONObject(0).getString("state"));
    }
    @Test public void diskFailureRollsBackTicketNumberAndEvent()throws Exception{
        final boolean[] fail={false};LocalQueue q=new LocalQueue(null,s->{if(fail[0])throw new IOException("disk full");});JSONObject control=pair(q,"supervisor",1);long before=q.displaySnapshot().getLong("sequence");fail[0]=true;
        try{command(q,control,"disk-fail-01","issue",1,null);fail();}catch(IOException expected){}
        assertEquals(before,q.displaySnapshot().getLong("sequence"));assertEquals(0,q.displaySnapshot().getInt("waiting"));fail[0]=false;
        assertEquals("A-001",command(q,control,"disk-fail-01","issue",1,null).getJSONObject("ticket").getString("ticket_label"));
    }
    @Test public void keyConflictAndUnauthorizedAccessDoNotMutate()throws Exception{
        LocalQueue q=new LocalQueue(null,s->{});JSONObject control=pair(q,"supervisor",1);command(q,control,"same-key-001","issue",1,null);
        try{command(q,control,"same-key-001","call",1,null);fail();}catch(LocalQueue.Failure e){assertEquals(409,e.status);}
        try{q.request("GET","/snapshot","incorrect",null,new JSONObject());fail();}catch(LocalQueue.Failure e){assertEquals(401,e.status);}
        assertEquals(1,q.displaySnapshot().getInt("waiting"));
    }
    @Test public void pairingExpiresAndFiveWrongAttemptsCloseIt()throws Exception{
        final long[] time={1000};LocalQueue.Clock clock=new LocalQueue.Clock(){public long now(){return time[0];}public String day(){return "2026-10-16";}};
        LocalQueue q=new LocalQueue(null,s->{},clock);String code=q.openPairing("operator",1).getString("code");
        for(int i=0;i<5;i++)try{q.request("POST","/pair",null,null,new JSONObject().put("name","Tablet").put("code","000000"));fail();}catch(LocalQueue.Failure e){assertEquals(403,e.status);}
        try{q.request("POST","/pair",null,null,new JSONObject().put("name","Tablet").put("code",code));fail();}catch(LocalQueue.Failure expected){}
        code=q.openPairing("operator",1).getString("code");time[0]+=120001;
        try{q.request("POST","/pair",null,null,new JSONObject().put("name","Tablet").put("code",code));fail();}catch(LocalQueue.Failure e){assertEquals(403,e.status);}
    }
    @Test public void snapshotReplayAndRetentionGap()throws Exception{
        LocalQueue q=new LocalQueue(null,s->{});JSONObject control=pair(q,"supervisor",1);
        for(int i=0;i<501;i++)command(q,control,"retention-"+i,"issue",1,null);
        try{q.request("GET","/events?after_sequence=0",control.getString("token"),null,new JSONObject());fail();}catch(LocalQueue.Failure e){assertEquals(410,e.status);}
        JSONObject result=q.request("GET","/events?after_sequence=500",control.getString("token"),null,new JSONObject());
        assertEquals(2,result.getJSONArray("events").length());assertFalse(result.toString().contains("actor_ref"));
        assertEquals(501,q.displaySnapshot().getInt("waiting"));
    }
    @Test public void dayChangeDoesNotDuplicateOpenTicketLabels()throws Exception{
        final String[] day={"2026-10-16"};LocalQueue.Clock clock=new LocalQueue.Clock(){public long now(){return 1000;}public String day(){return day[0];}};
        LocalQueue q=new LocalQueue(null,s->{},clock);JSONObject c=pair(q,"supervisor",1);
        assertEquals("A-001",command(q,c,"day-one-001","issue",1,null).getJSONObject("ticket").getString("ticket_label"));day[0]="2026-10-17";
        assertEquals("A-002",command(q,c,"day-two-001","issue",1,null).getJSONObject("ticket").getString("ticket_label"));
    }
    @Test public void corruptStorageIsNotSilentlyReset()throws Exception{
        final boolean[] wrote={false};try{new LocalQueue("{bad-data",s->wrote[0]=true);fail();}catch(Exception expected){}assertFalse(wrote[0]);
    }
}
