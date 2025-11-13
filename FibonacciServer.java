import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class FibonacciServer {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // ROOT ENDPOINT
        server.createContext("/", exchange -> {
            addCORS(exchange);

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            sendString(exchange, 200, "Java Fibonacci API is running");
        });

        // BASIC CALCULATOR
        server.createContext("/calculate", exchange -> {
            addCORS(exchange);

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

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

            String json = "{\"result\": " + result + "}";

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes().length);
            exchange.getResponseBody().write(json.getBytes());
            exchange.close();
        });

        // FIBONACCI IMAGE ENDPOINT
        server.createContext("/fibonacci-image", exchange -> {
            addCORS(exchange);

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

            int terms = parseInt(params.get("terms"), 8);
            int width = parseInt(params.get("width"), 600);
            int height = parseInt(params.get("height"), 600);

            BufferedImage img = drawFibSpiral(terms, width, height);

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
        System.out.println("Server running on port " + port);
    }

    // ---------------- CORS SUPPORT ----------------

    private static void addCORS(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
    }

    // ---------------- HELPERS ----------------

    private static void sendString(HttpExchange ex, int code, String msg) throws IOException {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;

        for (String p : query.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2) map.put(kv[0], kv[1]);
        }
        return map;
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        for (String p : body.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2)
                map.put(kv[0], kv[1]);
        }
        return map;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); }
        catch (Exception e) { return def; }
    }

    // ---------------- FIBONACCI SPIRAL DRAWING ----------------

    private static BufferedImage drawFibSpiral(int terms, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int[] fib = new int[terms + 2];
        fib[0] = 0;
        fib[1] = 1;
        for (int i = 2; i < fib.length; i++) {
            fib[i] = fib[i - 1] + fib[i - 2];
        }

        double cx = width / 2.0;
        double cy = height / 2.0;
        double angle = 0;

        g.setStroke(new BasicStroke(3));
        g.setColor(new Color(255, 70, 70));

        for (int i = 1; i <= terms; i++) {
            double r = fib[i] * 12; // scale

            Arc2D arc = new Arc2D.Double(
                cx - r, cy - r, r * 2, r * 2,
                Math.toDegrees(angle),
                90,
                Arc2D.OPEN
            );

            g.draw(arc);

            double endAngle = angle + Math.PI / 2;

            double endX = cx + r * Math.cos(endAngle);
            double endY = cy + r * Math.sin(endAngle);

            double nextR = fib[i + 1] * 12;

            cx = endX - nextR * Math.cos(endAngle);
            cy = endY - nextR * Math.sin(endAngle);

            angle = endAngle;
        }

        g.dispose();
        return img;
    }
}
