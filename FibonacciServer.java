import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.ArrayList;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

public class FibonacciServer {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        System.out.println("Server running on port " + port);

        server.createContext("/", ex -> {
            addCORS(ex);
            if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
                sendText(ex, 405, "Method Not Allowed");
                return;
            }

            File f = new File("index.html");
            if (!f.exists()) {
                sendText(ex, 404, "index.html not found");
                return;
            }

            byte[] d = java.nio.file.Files.readAllBytes(f.toPath());
            ex.getResponseHeaders().set("Content-Type","text/html");
            ex.sendResponseHeaders(200, d.length);
            ex.getResponseBody().write(d);
            ex.close();
        });

        server.createContext("/api/calculate", ex -> {
            addCORS(ex);
            if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
                sendText(ex, 405, "POST only");
                return;
            }

            String body = new String(ex.getRequestBody().readAllBytes());
            Map<String,String> p = parseForm(body);

            double a = Double.parseDouble(p.getOrDefault("a","0"));
            double b = Double.parseDouble(p.getOrDefault("b","0"));
            String op = p.getOrDefault("op","add");

            double r = switch(op){
                case "add" -> a + b;
                case "sub" -> a - b;
                case "mul" -> a * b;
                case "div" -> (b == 0 ? Double.NaN : a / b);
                default -> 0;
            };

            sendJSON(ex, "{\"result\":"+r+"}");
        });

        server.createContext("/api/fibonacci", ex -> {
            addCORS(ex);
            if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
                sendText(ex, 405, "POST only");
                return;
            }

            String body = new String(ex.getRequestBody().readAllBytes());
            Map<String,String> p = parseForm(body);
            int terms = Integer.parseInt(p.getOrDefault("terms","6"));
            if (terms < 1) terms = 1;
            if (terms > 30) terms = 30;

            sendJSON(ex, generateJSON(terms));
        });

        server.createContext("/api/fibonacci-image", ex -> {
            addCORS(ex);
            if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
                sendText(ex, 405, "GET only");
                return;
            }

            Map<String,String> q = parseQuery(ex.getRequestURI().getQuery());
            int terms = Integer.parseInt(q.getOrDefault("terms","6"));
            int W = Integer.parseInt(q.getOrDefault("width","800"));
            int H = Integer.parseInt(q.getOrDefault("height","800"));

            BufferedImage img = renderSpiral(terms, W, H);

            ex.getResponseHeaders().set("Content-Type","image/png");
            ex.sendResponseHeaders(200, 0);
            ImageIO.write(img,"png",ex.getResponseBody());
            ex.close();
        });

        server.setExecutor(null);
        server.start();
    }


    // ============================================================
    // FIBONACCI JSON FOR FRONTEND CANVAS
    // ============================================================
    private static String generateJSON(int terms){
        int[] fib = new int[terms+2];
        fib[0]=0; fib[1]=1;
        for(int i=2;i<fib.length;i++) fib[i]=fib[i-1]+fib[i-2];

        double cx=0, cy=0, angle=0;

        java.util.List<String> arcs = new java.util.ArrayList<>();

        for(int i=1;i<=terms;i++){
            double r=fib[i];
            arcs.add(String.format(Locale.US,
                "{\"cx\":%.6f,\"cy\":%.6f,\"r\":%.6f,\"start\":%.6f}",
                cx, cy, r, angle));

            double end = angle + Math.PI/2;
            double ex = cx + r * Math.cos(end);
            double ey = cy + r * Math.sin(end);

            double nextR = fib[i+1];
            cx = ex - nextR*Math.cos(end);
            cy = ey - nextR*Math.sin(end);

            angle = end;
        }

        return "{\"arcs\":[" + String.join(",", arcs) + "]}";
    }

    // ============================================================
    //  PNG RENDERER — PERFECT QUARTER ARCS
    // ============================================================
    private static BufferedImage renderSpiral(int terms,int W,int H){
        BufferedImage img=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

        // --- Parse the JSON geometry directly ---
        Map<String,Object> geom = parseJSON(generateJSON(terms));
        java.util.List<Map<String,Object>> arcs =
                (java.util.List<Map<String,Object>>) geom.get("arcs");

        double minX=1e9,maxX=-1e9,minY=1e9,maxY=-1e9;

        // Find bounds
        for (Map<String,Object> a : arcs){
            double cx=(double)a.get("cx");
            double cy=(double)a.get("cy");
            double r=(double)a.get("r");
            double start=(double)a.get("start");
            double end=start+Math.PI/2;

            for(double t=start; t<=end; t+=0.01){
                double x=cx+r*Math.cos(t);
                double y=cy+r*Math.sin(t);
                minX=Math.min(minX,x);
                maxX=Math.max(maxX,x);
                minY=Math.min(minY,y);
                maxY=Math.max(maxY,y);
            }
        }

        double worldW=maxX-minX, worldH=maxY-minY;
        double scale=Math.min(W/worldW * 0.85, H/worldH * 0.85);

        double ox = W/2 - (minX + worldW/2)*scale;
        double oy = H/2 + (minY + worldH/2)*scale;

        g.setColor(Color.WHITE);
        g.fillRect(0,0,W,H);

        // GRID
        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(230,230,230));
        for (int i=-20;i<=20;i++){
            int xx=(int)(ox+i*scale);
            g.drawLine(xx,0,xx,H);

            int yy=(int)(oy - i*scale);
            g.drawLine(0,yy,W,yy);
        }

        // AXES
        g.setColor(Color.GRAY);
        g.setStroke(new BasicStroke(2));
        g.drawLine((int)ox,0,(int)ox,H);
        g.drawLine(0,(int)oy,W,(int)oy);

        // ARCS
        for(int i=0;i<arcs.size();i++){
            Map<String,Object> a = arcs.get(i);
            double cx=(double)a.get("cx");
            double cy=(double)a.get("cy");
            double r=(double)a.get("r");
            double start=(double)a.get("start");

            float hue = 0.10f + (float)i / arcs.size() * 0.25f;
            g.setColor(Color.getHSBColor(hue,0.85f,0.85f));
            g.setStroke(new BasicStroke(3));

            double sx = ox + cx*scale;
            double sy = oy - cy*scale;
            double rp = r*scale;

            g.draw(new Arc2D.Double(
                    sx-rp, sy-rp,
                    rp*2, rp*2,
                    Math.toDegrees(-start),
                    -90,
                    Arc2D.OPEN));
        }

        g.dispose();
        return img;
    }

    // ============================================================
    // MINIMAL JSON PARSER FOR OUR ARC DATA
    // ============================================================
    private static Map<String,Object> parseJSON(String s){
        Map<String,Object> out=new HashMap<>();
        java.util.List<Map<String,Object>> arcs = new java.util.ArrayList<>();

        String inner = s.substring(s.indexOf("[")+1, s.lastIndexOf("]"));
        String[] items = inner.split("\\},\\{");

        for(String it : items){
            it = it.replace("{","").replace("}","");
            String[] kv = it.split(",");
            Map<String,Object> arc = new HashMap<>();
            for(String pair : kv){
                String[] p = pair.split(":");
                arc.put(p[0].replace("\"",""), Double.parseDouble(p[1]));
            }
            arcs.add(arc);
        }

        out.put("arcs", arcs);
        return out;
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private static Map<String,String> parseForm(String body){
        Map<String,String> m=new HashMap<>();
        if(body==null || body.isEmpty()) return m;
        for(String p:body.split("&")){
            String[] kv=p.split("=");
            if(kv.length==2)m.put(
                    URLDecoder.decode(kv[0],StandardCharsets.UTF_8),
                    URLDecoder.decode(kv[1],StandardCharsets.UTF_8));
        }
        return m;
    }

    private static Map<String,String> parseQuery(String q){
        Map<String,String> m=new HashMap<>();
        if(q==null) return m;
        for(String p:q.split("&")){
            String[] kv=p.split("=");
            if(kv.length==2)m.put(kv[0], kv[1]);
        }
        return m;
    }

    private static void sendText(HttpExchange ex,int code,String msg)throws IOException{
        addCORS(ex);
        byte[] d=msg.getBytes();
        ex.getResponseHeaders().set("Content-Type","text/plain");
        ex.sendResponseHeaders(code,d.length);
        ex.getResponseBody().write(d);
        ex.close();
    }

    private static void sendJSON(HttpExchange ex,String json)throws IOException{
        addCORS(ex);
        byte[] d=json.getBytes();
        ex.getResponseHeaders().set("Content-Type","application/json");
        ex.sendResponseHeaders(200,d.length);
        ex.getResponseBody().write(d);
        ex.close();
    }

    private static void addCORS(HttpExchange ex){
        ex.getResponseHeaders().add("Access-Control-Allow-Origin","*");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers","Content-Type");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods","GET,POST,OPTIONS");
    }
}
