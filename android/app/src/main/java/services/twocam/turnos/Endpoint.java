package services.twocam.turnos;
import java.net.URI;
final class Endpoint {
    static String normalize(String input) {
        try {
            String value=input.trim();if(!value.contains("://"))value="http://"+value;
            URI uri=new URI(value);String host=uri.getHost();int port=uri.getPort()<0?8990:uri.getPort();
            if(!"http".equals(uri.getScheme())||host==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||(!uri.getPath().isEmpty()&&!uri.getPath().equals("/"))||port<1||port>65535)throw new Exception();
            String[] parts=host.split("\\.");if(parts.length!=4)throw new Exception();int[] octets=new int[4];
            for(int i=0;i<4;i++){if(!parts[i].matches("[0-9]{1,3}"))throw new Exception();octets[i]=Integer.parseInt(parts[i]);if(octets[i]>255)throw new Exception();}
            if(!(octets[0]==10||(octets[0]==192&&octets[1]==168)||(octets[0]==172&&octets[1]>=16&&octets[1]<=31)||octets[0]==127))throw new Exception();
            return "http://"+host+":"+port;
        }catch(Exception e){throw new IllegalArgumentException("Introduce la IP privada que muestra el Fire (ej.: 192.168.1.40:8990)");}
    }
}
