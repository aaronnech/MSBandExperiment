package com.psych.aaron.heartratereader;

import android.os.AsyncTask;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Asynchronous task to handle shipping packets to the central data server
 */
public class ServerShipTask extends AsyncTask<List<Packet>, Void, String> {
    // STORAGE TOKEN (semi-safe bad security)
    private static String TOKEN = "saljfn73ksfDUCKDUCKGOOSEksdjf23";

    // Storage script URL
    private static String URL = "http://students.washington.edu/necha/receive.php";

    private String sessionID;

    public ServerShipTask(String sessionID) {
        super();
        this.sessionID = sessionID;
    }

    public static String join(List<Packet> arr, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, il = arr.size(); i < il; i++) {
            if (i > 0)
                sb.append(sep);
            if (arr.get(i) != null)
                sb.append(arr.get(i).toString());
        }
        return sb.toString();
    }

    private String urlEncodePackets(List<Packet> packets) {
        // Join a list of packets with a semi colon.
        return URLEncoder.encode(join(packets, ";"));
    }

    protected String doInBackground(List<Packet>... packets) {
        // If we have a bad list of packets, return
        if (packets[0] == null) return "";

        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);

        // A request has a token, sessionId, and list of packets
        nameValuePairs.add(new BasicNameValuePair("token", ServerShipTask.TOKEN));
        nameValuePairs.add(new BasicNameValuePair("session", sessionID));
        nameValuePairs.add(new BasicNameValuePair("payload", urlEncodePackets(packets[0])));

        HttpClient httpclient = new DefaultHttpClient();

        // Ship the packet bundle
        HttpResponse response;
        String responseString = null;
        try {
            HttpPost httppost = new HttpPost(ServerShipTask.URL);
            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            System.out.println("shipping....");

            response = httpclient.execute(httppost);
            StatusLine statusLine = response.getStatusLine();
            if(statusLine.getStatusCode() == HttpStatus.SC_OK){
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                response.getEntity().writeTo(out);
                responseString = out.toString();
                out.close();
            } else{

                //Closes the connection.
                response.getEntity().getContent().close();
                throw new IOException(statusLine.getReasonPhrase());
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return responseString;
    }
}
