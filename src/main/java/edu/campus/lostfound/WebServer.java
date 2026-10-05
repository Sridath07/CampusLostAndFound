package edu.campus.lostfound;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;

/** Optionally serves the browser UI and a same-origin JSON API backed by Supabase PostgreSQL. */
public class WebServer {
    private static final Pattern CLAIM_PATH = Pattern.compile("/api/reports/(\\d+)/claim");
    private static final Pattern RETURN_PATH = Pattern.compile("/api/reports/(\\d+)/return");

    private final LostAndFoundService service;
    private final ReportRepository repository;
    private final HttpServer server;

    public WebServer(LostAndFoundService service, ReportRepository repository, int port) throws IOException {
        this.service = service;
        this.repository = repository;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.server.setExecutor(Executors.newSingleThreadExecutor());
        this.server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
        System.out.println("Campus Lost-and-Found website: http://127.0.0.1:" + server.getAddress().getPort());
        System.out.println("Database: Supabase PostgreSQL");
        System.out.println("Press Ctrl+C to stop the server.");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/api/reports") && method.equals("GET")) {
                send(exchange, 200, "application/json; charset=utf-8", reportsJson());
            } else if (path.equals("/api/reports") && method.equals("POST")) {
                createReport(exchange);
            } else if (matches(CLAIM_PATH, path) && method.equals("POST")) {
                updateStatus(exchange, CLAIM_PATH, true);
            } else if (matches(RETURN_PATH, path) && method.equals("POST")) {
                updateStatus(exchange, RETURN_PATH, false);
            } else if (path.startsWith("/api/")) {
                send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"Endpoint not found\"}");
            } else {
                serveAsset(exchange, path);
            }
        } catch (IllegalArgumentException ex) {
            send(exchange, 400, "application/json; charset=utf-8", "{\"error\":" + json(ex.getMessage()) + "}");
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            send(exchange, 500, "application/json; charset=utf-8", "{\"error\":\"The server could not complete the request.\"}");
        } finally {
            exchange.close();
        }
    }

    private void createReport(HttpExchange exchange) throws IOException {
        Map<String, String> fields = form(exchange);
        User reporter = new User(required(fields, "name"), required(fields, "campusId"), required(fields, "contact"));
        ReportType type = ReportType.valueOf(required(fields, "type").toUpperCase());
        String title = required(fields, "title");
        String description = required(fields, "description");
        String category = required(fields, "category");
        String location = required(fields, "location");
        LocalDate date = LocalDate.parse(required(fields, "date"));
        ItemReport report = type == ReportType.LOST
                ? service.reportLost(title, description, category, location, reporter, date)
                : service.reportFound(title, description, category, location, reporter, date);
        repository.save(service.getAllReports());
        send(exchange, 201, "application/json; charset=utf-8", reportJson(report));
    }

    private void updateStatus(HttpExchange exchange, Pattern pattern, boolean claim) throws IOException {
        Matcher matcher = pattern.matcher(exchange.getRequestURI().getPath());
        if (!matcher.matches()) {
            send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"Report not found\"}");
            return;
        }
        int id = Integer.parseInt(matcher.group(1));
        boolean updated;
        if (claim) updated = service.claim(id, required(form(exchange), "campusId"));
        else updated = service.markReturned(id);
        if (!updated) {
            send(exchange, 409, "application/json; charset=utf-8", "{\"error\":\"Report is missing or has an incompatible status.\"}");
            return;
        }
        repository.save(service.getAllReports());
        send(exchange, 200, "application/json; charset=utf-8", reportJson(service.findById(id).orElseThrow()));
    }

    private String reportsJson() {
        return service.getAllReports().stream().map(this::reportJson).reduce((a, b) -> a + "," + b)
                .map(rows -> "[" + rows + "]").orElse("[]");
    }

    private String reportJson(ItemReport r) {
        return "{"
                + "\"id\":" + r.getId() + ","
                + "\"type\":" + json(r.getType().name()) + ","
                + "\"title\":" + json(r.getTitle()) + ","
                + "\"description\":" + json(r.getDescription()) + ","
                + "\"category\":" + json(r.getCategory()) + ","
                + "\"location\":" + json(r.getLocation()) + ","
                + "\"date\":" + json(r.getReportDate().toString()) + ","
                + "\"name\":" + json(r.getReporter().getName()) + ","
                + "\"campusId\":" + json(r.getReporter().getCampusId()) + ","
                + "\"contact\":" + json(r.getReporter().getContact()) + ","
                + "\"status\":" + json(r.getStatus().name()) + ","
                + "\"claimedBy\":" + json(r.getClaimedBy() == null ? "" : r.getClaimedBy())
                + "}";
    }

    private void serveAsset(HttpExchange exchange, String requestPath) throws IOException {
        String path = requestPath.equals("/") ? "/index.html" : requestPath;
        if (path.contains("..") || path.contains("\\")) {
            send(exchange, 400, "text/plain; charset=utf-8", "Bad path");
            return;
        }
        String resource = "/web" + path;
        try (var input = WebServer.class.getResourceAsStream(resource)) {
            if (input == null) {
                send(exchange, 404, "text/plain; charset=utf-8", "Page not found");
                return;
            }
            String contentType = switch (path.substring(path.lastIndexOf('.') + 1).toLowerCase()) {
                case "css" -> "text/css; charset=utf-8";
                case "js" -> "text/javascript; charset=utf-8";
                case "svg" -> "image/svg+xml";
                default -> "text/html; charset=utf-8";
            };
            byte[] bytes = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private static Map<String, String> form(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> fields = new LinkedHashMap<>();
        for (String part : body.split("&")) {
            int split = part.indexOf('=');
            String key = split < 0 ? part : part.substring(0, split);
            String value = split < 0 ? "" : part.substring(split + 1);
            fields.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return fields;
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required field: " + key);
        return value.trim();
    }

    private static String json(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> { if (c < 0x20) result.append(String.format("\\u%04x", (int)c)); else result.append(c); }
            }
        }
        return result.append('"').toString();
    }

    private static boolean matches(Pattern pattern, String path) {
        return pattern.matcher(path).matches();
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
