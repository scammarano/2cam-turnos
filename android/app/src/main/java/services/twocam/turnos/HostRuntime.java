package services.twocam.turnos;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.util.AtomicFile;
import services.twocam.turnoscore.LocalQueue;
import java.io.File;
import java.io.FileOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

final class HostRuntime {
    private static HostRuntime instance;
    final LocalQueue queue;
    private final Context context;
    private LanServer server;
    private NsdManager.RegistrationListener registration;
    private WifiManager.MulticastLock multicast;
    volatile String address="",networkStatus="Conecta el Fire a Wi-Fi o Ethernet privada";
    private HostRuntime(Context context)throws Exception {
        this.context=context.getApplicationContext();AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"turnos-local-v1.json"));String saved=null;
        // AtomicFile openRead can restore a .bak file after an interrupted write.
        if(file.getBaseFile().exists()||new File(file.getBaseFile().getPath()+".bak").exists())saved=new String(file.readFully(),"UTF-8");
        queue=new LocalQueue(saved,json->{FileOutputStream out=null;try{out=file.startWrite();out.write(json.getBytes("UTF-8"));file.finishWrite(out);}catch(Exception error){if(out!=null)file.failWrite(out);throw error;}});
    }
    static synchronized HostRuntime get(Context context)throws Exception {if(instance==null)instance=new HostRuntime(context);return instance;}
    static synchronized void stop(){if(instance!=null){instance.closeNetwork();instance=null;}}
    static InetAddress privateAddress()throws Exception {
        for(NetworkInterface network:Collections.list(NetworkInterface.getNetworkInterfaces())){
            if(!network.isUp()||network.isLoopback())continue;
            for(InetAddress ip:Collections.list(network.getInetAddresses()))if(ip instanceof Inet4Address&&ip.isSiteLocalAddress())return ip;
        }throw new Exception("Sin red local privada");
    }
    synchronized void ensureNetwork(){
        try{
            InetAddress ip=privateAddress();String next="http://"+ip.getHostAddress()+":8990";
            if(next.equals(address)&&server!=null)return;
            closeNetwork();server=new LanServer(queue);server.start(ip,8990);address=next;networkStatus="Servidor LAN activo";
            WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if(wifi!=null){multicast=wifi.createMulticastLock("2cam-turnos-host");multicast.setReferenceCounted(false);multicast.acquire();}
            NsdManager nsd=(NsdManager)context.getSystemService(Context.NSD_SERVICE);
            if(nsd!=null){
                registration=new NsdManager.RegistrationListener(){public void onRegistrationFailed(NsdServiceInfo i,int e){}public void onUnregistrationFailed(NsdServiceInfo i,int e){}public void onServiceRegistered(NsdServiceInfo i){}public void onServiceUnregistered(NsdServiceInfo i){}};
                NsdServiceInfo info=new NsdServiceInfo();info.setServiceName("2Cam Turnos "+queue.id().substring(0,6));info.setServiceType("_2cam-turnos._tcp.");info.setPort(8990);info.setAttribute("mode","turnos");nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,registration);
            }
        }catch(Exception e){if(server==null){address="";networkStatus="Conecta a una red privada; se reintenta automáticamente";}}
    }
    private synchronized void closeNetwork(){
        if(server!=null)server.close();server=null;address="";
        if(registration!=null){try{((NsdManager)context.getSystemService(Context.NSD_SERVICE)).unregisterService(registration);}catch(Exception ignored){}registration=null;}
        if(multicast!=null){try{if(multicast.isHeld())multicast.release();}catch(Exception ignored){}multicast=null;}
    }
}
