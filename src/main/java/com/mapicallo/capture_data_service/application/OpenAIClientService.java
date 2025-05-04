package com.mapicallo.capture_data_service.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapicallo.capture_data_service.config.OpenAIConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;


import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class OpenAIClientService {

    /*@Value("${openai.api.key}")
    private String openAiApiKey;*/

    private static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    @Autowired
    private OpenAIConfig openAIConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String summarizeText(String content) throws IOException {
        try {


            HttpClient client = HttpClient.newHttpClient();

            // Construimos el mensaje
            Map<String, Object> message = Map.of(
                    "role", "user",
                    "content", "Resume el siguiente texto médico con un lenguaje claro y estructurado para su análisis: " + content
            );

            Map<String, Object> body = Map.of(
                    "model", "gpt-3.5-turbo",
                    "messages", List.of(message),
                    "temperature", 0.4
            );

            // Serializamos a JSON real
            ObjectMapper mapper = new ObjectMapper();
            String jsonBody = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + openAIConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println(">>> JSON enviado a OpenAI:");
            System.out.println(jsonBody);
            System.out.println(">>> Authorization Header: Bearer " + openAIConfig.getApiKey());
            System.out.println(">>> Response code: " + response.statusCode());
            System.out.println(">>> Response body: " + response.body());

            if (response.statusCode() == 200) {
                JsonNode jsonNode = mapper.readTree(response.body());
                return jsonNode.get("choices").get(0).get("message").get("content").asText();
            } else {
                throw new IOException("Unexpected response from OpenAI: " + response.body());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request to OpenAI was interrupted", e);
        }
    }

}
