package passwordmanager.network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import passwordmanager.PasswordManager;
import static passwordmanager.PasswordManager.Cyfer;
import passwordmanager.forms.MainForm;
import passwordmanager.storage.Metadata;


public class Extension implements Server {
    private final MainForm form;
    private HttpServer server;
    
    public Extension(MainForm form){
        this.form = form;
    }
    
    public void start() {
        try {
            // Create an HTTP server bound to port 6700
            server = HttpServer.create(new InetSocketAddress(6700), 0);

            // Create a context to handle requests to /validate
            server.createContext("/validate", h -> {
                // Handle CORS preflight requests
                if ("OPTIONS".equals(h.getRequestMethod())) {
                    handleCors(h);
                } else if ("POST".equals(h.getRequestMethod())) {
                    // Read the input stream from the request body
                    InputStreamReader in = new InputStreamReader(h.getRequestBody(), "utf-8");
                    StringBuilder sb = new StringBuilder();
                    int b;
                    while ((b = in.read()) != -1) {
                        sb.append((char) (b));
                    }
                    // Decrypt the received username
                    String username = Cyfer(sb.toString());

                    // Check if the username matches the stored identifier
                    int code;
                    if (username.equals(PasswordManager.identifier)) {
                        form.lblStatus.setText("Extension Connected: 200");
                        code = 200;  // OK
                    } else {
                        form.lblStatus.setText("Extension attempted to connect with invalid credentials: 400");
                        code = 400;  // Bad Request
                    }

                    // Set response headers and send response
                    h.getResponseHeaders().set("Content-Type", "text/plain");
                    handleCors(h);  // Add CORS headers
                    h.sendResponseHeaders(code, -1);
                } else {
                    // If the request method is not allowed, return 405 Method Not Allowed
                    handleCors(h);
                    h.sendResponseHeaders(405, -1);
                }
            });

            // Create a context to handle requests to /passwords
            server.createContext("/passwords", h -> {
                // Handle CORS preflight requests
                if ("OPTIONS".equals(h.getRequestMethod())) {
                    handleCors(h);
                } else if ("POST".equals(h.getRequestMethod())) {
                    // Read the input stream from the request body
                    InputStreamReader in = new InputStreamReader(h.getRequestBody(), "utf-8");
                    StringBuilder builder = new StringBuilder();
                    int b;
                    while ((b = in.read()) != -1) {
                        builder.append((char) (b));
                    }
                    // Decrypt the received data
                    String buffer = Cyfer(builder.toString());
                    int index = PasswordManager.identifier.length();
                    String username = buffer.substring(0, index);
                    String website = buffer.substring(index);
                    handleCors(h);  // Add CORS headers

                    Metadata data;
                    if (PasswordManager.identifier.equals(username)) {
                        // Fetch and decrypt the stored metadata for the website
                        if ((data = PasswordManager.storage.fetch(website)) != null) {
                            data.decrypt();
                            StringBuilder build = new StringBuilder();
                            for (String d : data.getData()) {
                                build.append((char) (d.length()));
                                build.append(d);
                            }
                            // Encrypt the response and send it back to the client
                            byte[] body = Cyfer(build.toString()).getBytes(StandardCharsets.UTF_8);
                            h.sendResponseHeaders(200, body.length);  // OK
                            OutputStream response = h.getResponseBody();
                            response.write(body);
                            response.close();
                        } else {
                            // If no metadata found, return 400 Bad Request
                            h.sendResponseHeaders(400, -1);
                        }
                    } else {
                        // If the username is invalid, return 404 Not Found
                        h.sendResponseHeaders(404, -1);
                    }
                } else {
                    // If the request method is not allowed, return 405 Method Not Allowed
                    handleCors(h);
                    h.sendResponseHeaders(405, -1);
                }
            });

            // Create a context to handle requests to /generate
            server.createContext("/generate", h -> {
                if ("OPTIONS".equals(h.getRequestMethod())) {
                    handleCors(h);
                } else if ("POST".equals(h.getRequestMethod())) {
                    // Read the input stream from the request body
                    InputStreamReader in = new InputStreamReader(h.getRequestBody(), "utf-8");
                    StringBuilder builder = new StringBuilder();
                    int b;
                    while ((b = in.read()) != -1) {
                        builder.append((char) (b));
                    }
                    // Decrypt the received data
                    String buffer = Cyfer(builder.toString());
                    int index = PasswordManager.identifier.length();
                    String username = buffer.substring(0, index);
                    String website = buffer.substring(index);
                    handleCors(h);  // Add CORS headers

                    if (PasswordManager.identifier.equals(username)) {
                        // Generate a new password, store it, and reload the form
                        String generated = PasswordManager.GenPassword(10);
                        PasswordManager.storage.put(new Metadata(website, false, generated));
                        form.reload();
                        // Encrypt the generated password and send it back to the client
                        byte[] password = Cyfer(generated).getBytes(StandardCharsets.UTF_8);
                        h.sendResponseHeaders(200, password.length);  // OK
                        OutputStream response = h.getResponseBody();
                        response.write(password);
                        response.close();
                    } else {
                        // If the username is invalid, return 404 Not Found
                        h.sendResponseHeaders(404, -1);
                    }
                }
            });

            // Set the executor to null, using the default executor
            server.setExecutor(null);

            // Start the HTTP server
            server.start();

        } catch (Exception e) {
            // Handle any exceptions that occur during server creation
            System.err.println("Failed to create HTTPS server\nReason: " + e.getMessage());
        }
    }

    public void close(){
        // stop the server
        server.stop(0);
    }
    
    private void handleCors(HttpExchange h) {
        h.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        h.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        h.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
        h.getResponseHeaders().add("Access-Control-Max-Age", "86400");  // 24 hours
        if ("OPTIONS".equals(h.getRequestMethod())) {
            try {
                h.sendResponseHeaders(204, -1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
