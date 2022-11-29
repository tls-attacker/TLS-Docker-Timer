package de.rub.nds.timingdockerevaluator.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.List;

public class HttpUtil {
    
    public static final String REQUEST_PORT_PATH = "portrequest";
    public static final String ENABLE_PORT_SWITCHING = "enableportswitch";
    
    public static List<String> queryDockerHttpServer(String ip, String endpoint) throws Exception {
        URL urlTarget = new URL("http://" + ip + ":8090/" + endpoint);
        URLConnection urlConnection = urlTarget.openConnection();
        BufferedReader in = new BufferedReader(
                                new InputStreamReader(
                                urlConnection.getInputStream()));
        List<String> outputs = new LinkedList<>();
        String inputLine;
        while ((inputLine = in.readLine()) != null)  {
            outputs.add(inputLine);
        }
            
        in.close();
        return outputs;
    }
    
    public static boolean enablePortSwitiching(String ip) {
        List<String> response = null;
        try {
            response = queryDockerHttpServer(ip, ENABLE_PORT_SWITCHING);
            for(String line: response) {
                if(line.contains("Port switching enabled")) {
                    return true;
                }
            }
        } catch (Exception ex) {
        }
        return false;
    } 
    
    public static int getCurrentPort(String ip, int fallbackPort) {
        try {
            List<String> response = queryDockerHttpServer(ip, REQUEST_PORT_PATH);
            for(String line: response) {
                if(line.contains("-Port")) {
                    String reportedPort = line.substring(line.indexOf("Use:") + "Use".length() + 1, line.indexOf("-Port"));
                    return Integer.parseInt(reportedPort);
                }
            }
        } catch (Exception ex) {
            return fallbackPort;
        }
        return fallbackPort;
    }
}
