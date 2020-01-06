package pl.edu.pja.s19880.v2;

import pl.edu.pja.s19880.v2.headers.HTTPHeader;
import pl.edu.pja.s19880.v2.headers.HTTPHeaderMap;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ConnectionHandler implements Runnable {
    private final Socket socket;
    private final DataInputStream input;
    private final OutputStream output;

    public ConnectionHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.output = socket.getOutputStream();
        new Thread(this).start();
    }

    @Override
    public void run() {
        try {
            while (true) {
                HTTPEntity entity = parseHTTP(input);
                if(entity.getMessage().toLowerCase().contains("connect")) {
                    Logger.log(entity.getMessage());
                    socket.close();
                    return;
                }
                proxy(entity);
                socket.close();
                return;
            }
        } catch (IOException | InterruptedException e) {
            if(e.getMessage().contains("Stream closed")) return;
            e.printStackTrace();
        }
    }
    public static HTTPEntity parseHTTP(DataInputStream stream) throws IOException, InterruptedException {
        String message = stream.readLine();
        HTTPHeaderMap headers = new HTTPHeaderMap();
        String temp = stream.readLine();
        while(temp != null && !temp.isEmpty()) {
            headers.put(new HTTPHeader(temp));
            temp = stream.readLine();
        }
        byte[] body = new byte[0];
        HTTPHeader contentLength = headers.get("Content-Length");
        if (contentLength != null) {
            int readBytes = 0;
            int contentLengthValue = Integer.parseInt(contentLength.value());
            body = new byte[contentLengthValue];
            while (readBytes < contentLengthValue) {
                readBytes += stream.read(body, readBytes, contentLengthValue - readBytes);
                Thread.sleep(10);
            }
        } else if (stream.available() > 0) {
            body = stream.readAllBytes();
        }
        return new HTTPEntity(message, headers, body);
    }

    private void proxy(HTTPEntity httpEntity) throws InterruptedException {
        String message = httpEntity.getMessage();
        httpEntity.setMessage(message.replace("HTTP/1.1", "HTTP/1.0"));
        Logger.log("(REQ) " + message);
        String lowerCaseMethod = message.split(" ")[0].toLowerCase();
        httpEntity.getHeaders().put(new HTTPHeader("Accept-Encoding", "identity")); // we prefer plaintext
        httpEntity.getHeaders().remove("If-None-Match"); // we don't need caches
        httpEntity.getHeaders().remove("If-Modified-Since"); // we don't need caches
        try {
            if(!lowerCaseMethod.equals("get") && !lowerCaseMethod.equals("post")) {
                socket.close();
                return;
            }
            Socket s = new Socket();
            s.connect(new InetSocketAddress(InetAddress.getByName(httpEntity.getHeaders().get("host").value()), 80));
            Logger.log("(PRX) Connected socket!");
            s.getOutputStream().write(httpEntity.getBytes());
            HTTPEntity proxied = parseHTTP(new DataInputStream(new BufferedInputStream(s.getInputStream())));
            Logger.log("(RES) " + proxied.getMessage());
            proxied.getHeaders().put(new HTTPHeader("X-Proxy-Author", "Piotr Adamczyk | s19880"));
            proxied.getHeaders().put(new HTTPHeader("X-This-Proxy", "is very offensive to me 🎅"));
            if (proxied.getHeaders().containsKey("Content-Type") && proxied.getHeaders().get("Content-Type").value().contains("text/html")) {
                String body = new String(proxied.getBody(), StandardCharsets.UTF_8);
                int headIndex = body.toLowerCase().indexOf("</head>");
                if (headIndex != -1) {
                    body = body.substring(0, headIndex) + "<script>" + HTTPEntity.getUnpleasantWordFilterScript() + "</script>" + body.substring(headIndex);
                    proxied.setBody(body.getBytes(StandardCharsets.UTF_8));
                    proxied.getHeaders().put(new HTTPHeader("Content-Length", "" + proxied.getBody().length));
                }
            }
            output.write(proxied.getBytes());
            output.flush();
            s.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}