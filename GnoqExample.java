import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class GnoqExample {
    public static void main(String[] args) {
        try {

            String apiKey = System.getenv("GROQ_API_KEY");// load from env variable
            String endpoint = "https://api.groq.com/openai/v1/chat/completions";

            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Request body
            String jsonInputString = "{"
                    + "\"model\": \"llama-3.1-70b-versatile\","
                    + "\"messages\": [{\"role\": \"user\", \"content\": \"Hello Groq!\"}]"
                    + "}";

            try(OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Read response
            try(BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println("Response: " + response.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

