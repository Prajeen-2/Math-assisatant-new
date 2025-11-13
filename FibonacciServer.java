import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FibonacciServer {

    public static void main(String[] args) throws Exception {

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        System.out.println("Server running on port: " + port);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        
        server.createContext("/", exchange -> sendString(exchange, 200, "Java Fibonacci API is running"));

        
        server.createContext("/fibonacci-image", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

            int terms = parseInt(params.get("terms"), 8);
            int width = parseInt(params.get("width"), 600);
            int height = parseInt(params.get("height"), 600);

            if (terms < 1) terms = 1;
            if (terms > 20) terms = 20;

            BufferedImage img = renderFibonacci(terms, width, height);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);

            byte[] data = baos.toByteArray();

            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        });

        server.setExecutor(null);
        server.start();
    }


    private static BufferedImage renderFibonacci(int terms, int width, int height) {

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        int[] fib = new int[terms + 2];
        fib[0] = 0;
        fib[1] = 1;
        for (int i = 2; i < fib.length; i++) {
            fib[i] = fib[i - 1] + fib[i - 2];
        }

        java.util.List<Double> xList = new java.util.ArrayList<>();
        java.util.List<Double> yList = new java.util.ArrayList<>();

        double cx = 0;
        double cy = 0;
        double angle = 0;

        for (int i = 1; i <= terms; i++) {
            double r = fib[i];
            int samples = 100 + (i * 30);

            for (int s = 0; s <= samples; s++) {
                double t = (double) s / samples;
                double theta = angle + (Math.PI / 2.0) * t;
                double x = cx + r * Math.cos(theta);
                double y = cy + r * Math.sin(theta);

                xList.add(x);
                yList.add(y);
            }

            double endAngle = angle + Math.PI / 2.0;

            double ex = cx + r * Math.cos(endAngle);
            double ey = cy + r * Math.sin(endAngle);

            double rNext = fib[i + 1];
            cx = ex - rNext * Math.cos(endAngle);
            cy = ey - rNext * Math.sin(endAngle);

            angle = endAngle;
        }

        double minX = xList.stream().min(Double::compare).get();
        double maxX = xList.stream().max(Double::compare).get();
        double minY = yList.stream().min(Double::compare).get();
        double maxY = yList.stream().max(Double::compare).get();

        double scale = (width * 0.8) / Math.max(maxX - minX, maxY - minY);
        int offsetX = (int) (width * 0.1);
        int offsetY = (int) (height * 0.1);

        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(255, 60, 60));

        for (int i = 0; i < xList.size() - 1; i++) {
            int x1 = (int) ((xList.get(i) - minX) * scale + offsetX);
            int y1 = (int) (height - ((yList.get(i) - minY) * scale + offsetY));

            int x2 = (int) ((xList.get(i + 1) - minX) * scale + offsetX);
            int y2 = (int) (height - ((yList.get(i + 1) - minY) * scale + offsetY));

            g.drawLine(x1, y1, x2, y2);
        }

        g.setFont(new Font("Segoe UI", Font.BOLD, 20));
        g.setColor(Color.BLACK);
        g.drawString("Fibonacci Spiral (90° arcs, radii = Fibonacci numbers)", 20, 40);

        g.dispose();
        return img;
    }


    private static int parseInt(String v, int def) {
        try { return Integer.parseInt(v); }
        catch (Exception e) { return def; }
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> map = new HashMap<>();
        if (q == null) return map;
        for (String p : q.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2)
                map.put(kv[0], kv[1]);
        }
        return map;
    }

    private static void sendString(HttpExchange ex, int code, String msg) throws IOException {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/plain");
        ex.sendResponseHeaders(code, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }
}
