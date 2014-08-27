/* Copyright(c) 2011 M Hata
   This software is released under the MIT License.
   http://opensource.org/licenses/mit-license.php */
package tabou.http;
import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

/**
 */
public class SimpleProxy {
    static int DEFAULT_PROXY_PORT = 8080;
    private URL parentProxy = null;
    private ServerSocket serverSocket;

    public static void usage() {
        System.err.println("Usage使い方: java tabou.http.SimpleProxy [-p ポート番号] [-P 親プロキシURL]");
        System.exit(-1);
    }
    public static void main(String[] args) throws Exception{
        int localPort = DEFAULT_PROXY_PORT;  /* ポート番号取得       */
        SimpleProxy simpleProxy = new SimpleProxy();
        int argi = 0;
        for (; argi < args.length; argi++) {
            char[] chars = args[argi].toCharArray();
            if (chars[0] != '-') {
                break;
            }
            if (chars.length != 2) {
                System.err.println("不正な引数:" + args[argi]);
                usage();
            }
            char c = chars[1];
            switch (c) {
            case 'p':
                localPort = Integer.parseInt(args[++argi]);  /* ポート番号取得       */
                break;
            case 'P':
                simpleProxy.parentProxy = new URL(args[++argi]);  /* 親プロキシ       */
                break;
            default:
                System.err.println("不正な引数:" + c);
                usage();
            }
        }
        simpleProxy.proxy(localPort);
    }
    public void proxy(int localPort) throws IOException{
        this.serverSocket = new ServerSocket(localPort);
        while (true) {
            System.out.println("wait:*."+ localPort  +" ...");
            Socket requestSocket = this.serverSocket.accept();
            System.out.println("accept:" + requestSocket.getInetAddress());
            try{
                request(requestSocket);
            }catch(Exception e){
                System.err.println(e.toString());
            }finally{
                requestSocket.close();
            }
            System.out.println("close");
        }
    }
    public void close() throws IOException{
        this.serverSocket.close();
    }
    public void request(Socket requestSocket) throws IOException{
        requestSocket.setSoTimeout(1000 * 100);
        BufferedInputStream requestIn =
            new BufferedInputStream(requestSocket.getInputStream());
        BufferedOutputStream requestOut =
            new BufferedOutputStream(requestSocket.getOutputStream());

        String firstLine = readLine(requestIn);
        System.out.println(firstLine);
        String[] stats = firstLine.split(" ");
        String methed = stats[0];
        URL    url    = new URL(stats[1]);
        String version= stats[2];
        Socket webSocket;
        try{
            if(parentProxy == null){
                int webPort = url.getPort();
                if(webPort < 0){
                    webPort = 80;
                }
                webSocket = new Socket(url.getHost(),webPort);
            }else{
                webSocket = new Socket(parentProxy.getHost(),parentProxy.getPort());
            }
        }catch(Exception e){
            System.err.println(e.toString());
            requestOut.write("HTTP/1.0 500 ".getBytes());
            requestOut.write(e.toString().getBytes());
            requestOut.write("\r\n".getBytes());
            requestOut.write("Content-type: text/plain\r\n".getBytes());
            requestOut.write("Connection: close\r\n".getBytes());
            requestOut.write("\r\n".getBytes());
            requestOut.write(e.toString().getBytes());
            requestOut.flush();
            return;
        }
        webSocket.setSoTimeout(1000 * 100);
        BufferedOutputStream webOut =
            new BufferedOutputStream(webSocket.getOutputStream());
        webOut.write(methed.getBytes());
        webOut.write(" ".getBytes());
        if(parentProxy != null){
            webOut.write(url.toString().getBytes());
        }else{
            webOut.write(url.getFile().getBytes());
        }
        webOut.write(" ".getBytes());
        webOut.write(version.getBytes());
        webOut.write("\r\n".getBytes());
        int contentLength = 0;
        while(true){
            String line = readLine(requestIn);
            if(line == null){
                break;
            }
            System.out.println(line);
            if(line.equals("")){
                webOut.write("Connection: close\r\n".getBytes());
                webOut.write("\r\n".getBytes());
                break;
            }
            int index = line.indexOf(':');
            String tagName = line.substring(0,index).toUpperCase();
            String value   = line.substring(index +1).trim();
            if(tagName.equals("KEEP-ALIVE")       ||
               tagName.equals("PROXY-CONNECTION") ||
               tagName.equals("CONNECTION")){
    	        continue;
            }
            if(tagName.equals("CONTENT-LENGTH")){
                contentLength = Integer.parseInt(value);
            }
            webOut.write(line.getBytes());
            webOut.write("\r\n".getBytes());
        }
        for(int i = 0;i < contentLength;i++){
            int c = requestIn.read();
            if(c < 0){
                break;
            }
            webOut.write(c);
//            System.out.write(c);
        }
        webOut.flush();
        BufferedInputStream webIn =
            new BufferedInputStream(webSocket.getInputStream());
        firstLine = readLine(webIn);
        System.out.println(firstLine);
        requestOut.write(firstLine.getBytes());
        requestOut.write("\r\n".getBytes());
        while(true){
            String line = readLine(webIn);
            if(line == null){
                break;
            }else if(line.equals("")){
                requestOut.write("\r\n".getBytes());
                break;
            }
            System.out.println(line);
            requestOut.write(line.getBytes());
            requestOut.write("\r\n".getBytes());
        }
        while(true){
            int c = webIn.read();
            if(c < 0){
                break;
            }
            requestOut.write(c);
        }
        webOut.close();
        webIn.close();
        requestOut.close();
        requestIn.close();
        webSocket.close();
    }
    /**
     * 一行読み込み
     */
    public String readLine(InputStream in) throws IOException{
        ByteArrayOutputStream sb = new ByteArrayOutputStream();
        while(true){
            int c = in.read();
            if(c < 0){
                if(sb.size() == 0){
                    return null; //読み込み終了
                }
                break;
            }else if(c == '\r'){
            }else if(c == '\n'){
                break;
            }else{
                sb.write(c);
            }
        }
        return sb.toString();
    }
}
