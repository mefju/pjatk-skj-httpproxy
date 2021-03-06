package pl.edu.pja.s19880.v2;


import pl.edu.pja.s19880.v2.headers.HTTPHeader;
import pl.edu.pja.s19880.v2.headers.HTTPHeaderMap;
import pl.edu.pja.s19880.v2.polyfill.InputStreamPolyfill;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class HTTPEntity implements Serializable {
    public static final String LINE_END = "\r\n";
    private String message;
    private HTTPHeaderMap headers;
    private byte[] body;

    public HTTPEntity(String message, HTTPHeaderMap headers, byte[] body) {
        this.message = message;
        this.headers = headers;
        this.body = body;
    }

    public void unpackGzip() {
        if (this.getHeaders().get("Content-Encoding") == null
                || !this.getHeaders().get("Content-Encoding").value().toLowerCase().contains("gzip"))
            return;
        GZIPInputStream gzip;
        try {
            byte[] temp = new byte[body.length];
            System.arraycopy(body, 0, temp, 0, body.length);
            gzip = new GZIPInputStream(new ByteArrayInputStream(body));
            this.body = new InputStreamPolyfill(gzip).readAllBytes();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.getHeaders().put(new HTTPHeader("Content-Encoding", "identity"));
        this.getHeaders().put(new HTTPHeader("Content-Length", "" + body.length));
    }

    public static String getUnpleasantWordFilterScript() {
        List<String> words = new ArrayList<>(Arrays.asList(ProxyConfig.instance.unpleasantWords));
        for (int i = 0; i < words.size(); i++) {
            words.set(i, String.format("\"%s\"", words.get(i).replace("\"", "\\\"")));
        }
        try {
            return new String(Files.readAllBytes(Paths.get("src/pl/edu/pja/s19880/v2/unpleasantWordFilter.js"))).replace("/*UNPLEASANT_WORDS_HERE*/", String.join(",", words));
        } catch (IOException e) {
            return "alert('error!');";
        }
    }

    public String getMessage() {
        return message;
    }

    public HTTPHeaderMap getHeaders() {
        return headers;
    }

    public String getHost() {
        if (!message.split(" ")[0].toLowerCase().equals("connect")) return this.getHeaders().get("Host").value();
        URI uri = null;
        try {
            uri = new URI(message.split(" ")[1]);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return uri.getHost() == null ? uri.getScheme() : uri.getHost();
    }

    public int getPort() {
        URI uri = null;
        try {
            uri = new URI(message.split(" ")[1]);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        try {
            return uri.getPort() == -1 ? Integer.parseInt(uri.getSchemeSpecificPart()) : uri.getPort();
        } catch (NumberFormatException e) {
            return uri.getScheme() != null && uri.getScheme().toLowerCase().equals("https") ? 443 : 80;
        }
    }

    public byte[] getBytes() {
        try {
            ByteArrayOutputStream ba = new ByteArrayOutputStream();
            ba.write(message.getBytes(StandardCharsets.UTF_8));
            ba.write(LINE_END.getBytes(StandardCharsets.UTF_8));
            ba.write(headers.toString().getBytes(StandardCharsets.UTF_8));
            ba.write(LINE_END.getBytes(StandardCharsets.UTF_8));
            ba.write(body);
            return ba.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }


    public String getHash() {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(this.message.getBytes());
            md5.update(this.headers.toString().getBytes());
            md5.update(this.body);
            byte[] digest = md5.digest();
            return Utils.bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
    }
}
