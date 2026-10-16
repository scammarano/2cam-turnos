package services.twocam.turnos;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
public final class HostService extends Service {
    private final Handler timer=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){try{HostRuntime.get(HostService.this).ensureNetwork();}catch(Exception ignored){}timer.postDelayed(this,5000);}};
    @Override public void onCreate(){
        super.onCreate();NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel("turnos-local","Turnos local",NotificationManager.IMPORTANCE_LOW));
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=23)flags|=PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),flags);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"turnos-local"):new Notification.Builder(this);
        startForeground(2200,builder.setSmallIcon(R.drawable.ic_turnos).setContentTitle("2Cam Turnos · sala local").setContentText("Servidor de turnos activo en esta red").setContentIntent(open).setOngoing(true).build());timer.post(tick);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){return START_STICKY;}
    @Override public void onDestroy(){timer.removeCallbacksAndMessages(null);HostRuntime.stop();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
