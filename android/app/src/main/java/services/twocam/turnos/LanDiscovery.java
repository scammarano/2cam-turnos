package services.twocam.turnos;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import java.net.Inet4Address;
import java.util.ArrayDeque;
import java.util.HashSet;
final class LanDiscovery {
    interface Found{void found(String name,String address);}
    private final NsdManager manager;
    private final WifiManager wifi;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayDeque<NsdServiceInfo> queue=new ArrayDeque<>();
    private final HashSet<String> seen=new HashSet<>();
    private NsdManager.DiscoveryListener listener;
    private WifiManager.MulticastLock lock;
    private int epoch;private boolean resolving,running;
    LanDiscovery(Context context){manager=(NsdManager)context.getSystemService(Context.NSD_SERVICE);wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);}
    void start(Found found){stop();if(manager==null)return;running=true;final int current=++epoch;
        if(wifi!=null){lock=wifi.createMulticastLock("2cam-turnos-search");lock.setReferenceCounted(false);lock.acquire();}
        listener=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String t){}public void onDiscoveryStopped(String t){}public void onStopDiscoveryFailed(String t,int e){}public void onServiceLost(NsdServiceInfo i){}
            public void onStartDiscoveryFailed(String t,int e){main.post(()->{if(current==epoch)stop();});}
            public void onServiceFound(NsdServiceInfo info){main.post(()->{if(!running||current!=epoch||!info.getServiceType().contains("_2cam-turnos._tcp")||!seen.add(info.getServiceName()))return;queue.add(info);resolve(current,found);});}
        };
        try{manager.discoverServices("_2cam-turnos._tcp.",NsdManager.PROTOCOL_DNS_SD,listener);}catch(Exception e){stop();}
        main.postDelayed(()->{if(current==epoch)stop();},12000);
    }
    private void resolve(int current,Found found){if(!running||resolving||queue.isEmpty()||current!=epoch)return;resolving=true;
        try{manager.resolveService(queue.remove(),new NsdManager.ResolveListener(){
            public void onResolveFailed(NsdServiceInfo info,int error){main.post(()->{if(current==epoch){resolving=false;resolve(current,found);}});}
            public void onServiceResolved(NsdServiceInfo info){main.post(()->{if(!running||current!=epoch)return;resolving=false;if(info.getHost() instanceof Inet4Address&&info.getHost().isSiteLocalAddress())found.found(info.getServiceName(),"http://"+info.getHost().getHostAddress()+":"+info.getPort());resolve(current,found);});}
        });}catch(Exception e){resolving=false;resolve(current,found);}
    }
    void stop(){running=false;epoch++;if(listener!=null&&manager!=null)try{manager.stopServiceDiscovery(listener);}catch(Exception ignored){}listener=null;queue.clear();seen.clear();resolving=false;if(lock!=null){try{if(lock.isHeld())lock.release();}catch(Exception ignored){}lock=null;}}
}
