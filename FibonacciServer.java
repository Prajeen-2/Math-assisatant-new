import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.geom.Point2D;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.awt.geom.*;
import java.awt.image.*;
import javax.imageio.ImageIO;

public class FibonacciServer {

    public static void main(String[] args) throws Exception {

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));


        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        System.out.println("Server running on port: " + port);

        server.createContext("/", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            File file = new File("index.html");
            if (!file.exists()) {
                sendText(exchange, 404, "index.html not found!");
                return;
            }

            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            addCORS(exchange);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        });


        server.createContext("/api/calculate", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendText(exchange, 405, "POST only");
                return;
            }

            addCORS(exchange);

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseForm(body);

            double a = Double.parseDouble(params.getOrDefault("a", "0"));
            double b = Double.parseDouble(params.getOrDefault("b", "0"));
            String op = params.getOrDefault("op", "add");

            double result = switch (op) {
                case "add" -> a + b;
                case "sub" -> a - b;
                case "mul" -> a * b;
                case "div" -> (b == 0 ? Double.NaN : a / b);
                default -> 0;
            };

            String json = "{\"result\":" + result + "}";
            sendJSON(exchange, json);
        });

        server.createContext("/api/fibonacci", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendText(exchange, 405, "POST only");
                return;
            }

            addCORS(exchange);

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseForm(body);

            int terms = Integer.parseInt(params.getOrDefault("terms", "6"));
            if (terms < 1) terms = 1;
            if (terms > 20) terms = 20;

            String json = generateFibonacciData(terms);
            sendJSON(exchange, json);
        });

        server.createContext("/api/fibonacci-image", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendText(exchange, 405, "GET only");
                return;
            }

            addCORS(exchange);

            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
            int terms = Integer.parseInt(params.getOrDefault("terms", "6"));
            int size = Integer.parseInt(params.getOrDefault("size", "600"));

            BufferedImage img = renderFibonacciImage(terms, size, size);

            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, 0);
            ImageIO.write(img, "png", exchange.getResponseBody());
            exchange.close();
        });

        server.setExecutor(null);
        server.start();
    }


    private static String generateFibonacciData(int terms) {
        int[] fib = new int[terms + 2];
        fib[0] = 0;
        fib[1] = 1;

        for (int i = 2; i < fib.length; i++)
            fib[i] = fib[i - 1] + fib[i - 2];

        double cx = 0, cy = 0, angle = 0;
        List<String> arcs = new ArrayList<>();

        for (int i = 1; i <= terms; i++) {
            double r = fib[i];
            arcs.add(String.format(Locale.US,
                    "{\"cx\":%.4f,\"cy\":%.4f,\"radius\":%.4f,\"start\":%.4f}",
                    cx, cy, r, angle));

            double endAngle = angle + Math.PI / 2;
            double ex = cx + r * Math.cos(endAngle);
            double ey = cy + r * Math.sin(endAngle);

            double rn = fib[i + 1];
            cx = ex - rn * Math.cos(endAngle);
            cy = ey - rn * Math.sin(endAngle);
            angle = endAngle;
        }

        return "[" + String.join(",", arcs) + "]";
    }


    private static BufferedImage renderFibonacciImage(int terms, int W, int H) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);

        int[] fib = new int[terms + 2];
        fib[0] = 0; fib[1] = 1;
        for (int i = 2; i < fib.length; i++) fib[i] = fib[i - 1] + fib[i - 2];

        double cx = W / 2.0;
        double cy = H / 2.0;
        double angle = 0.0;

        for (int i = 1; i <= terms; i++) {
            double r = fib[i] * 20; // scale factor
            g.setColor(new Color(50 + i * 10, 20, 200 - i * 5));

            g.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r,
                    Math.toDegrees(angle), 90, Arc2D.OPEN));

            double endAngle = angle + Math.PI / 2;
            cx = cx + r * Math.cos(endAngle);
            cy = cy + r * Math.sin(endAngle);
            angle = endAngle;
        }

        g.dispose();
        return img;
    }


    private static void sendJSON(HttpExchange ex, String json) throws IOException {
        addCORS(ex);
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }

    private static void sendText(HttpExchange ex, int code, String msg) throws IOException {
        addCORS(ex);
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain");
        ex.sendResponseHeaders(code, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null) return map;

        for (String p : body.split("&")) {
            String[] kv = p.split("=");
            if (kv.length == 2)
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return map;
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> map = new HashMap<>();
        if (q == null) return map;

        for (String p : q.split("&")) {
            String[] kv = p.split("=");
            if (kv.length == 2)
                map.put(kv[0], kv[1]);
        }
        return map;
    }

    private static void addCORS(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    }
}
