package com.dbexplorer.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import com.dbexplorer.model.AIConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Service to interact with AI APIs for SQL generation.
 * Supports OpenAI, Claude, DeepSeek, and Gemini.
 */
public class AIAssistantService {
    private final HttpClient httpClient;
    private final AIConfigManager configManager;

    public AIAssistantService(AIConfigManager configManager) {
        this.httpClient = HttpClient.newHttpClient();
        this.configManager = configManager;
    }

    /**
     * Generates SQL from natural language description using the AI model.
     */
    public String generateSQL(String naturalLanguageQuery, String schemaInfo, String databaseType) {
        AIConfig config = configManager.getLastUsedConfig();
        return generateSQLWithSpecificConfig(config, naturalLanguageQuery, schemaInfo, databaseType);
    }

    /**
     * Generates SQL using a specific AI profile.
     */
    public String generateSQLWithSpecificConfig(AIConfig config, String naturalLanguageQuery, String schemaInfo, String databaseType) {
        if (config == null || !config.isEnabled()) {
            return "Error: AI Assistant is not configured. Please configure it in Edit → AI Configuration.";
        }
        
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            return "Error: API Key not configured.";
        }

        try {
            String systemPrompt = buildSystemPrompt(schemaInfo, databaseType);
            String response;
            
            response = switch (config.getApiProvider()) {
                case "Claude" -> callClaudeAPI(config, systemPrompt, naturalLanguageQuery);
                case "Gemini" -> callGeminiAPI(config, systemPrompt, naturalLanguageQuery);
                case "DeepSeek" -> callOpenAICompatibleAPI(config, systemPrompt, naturalLanguageQuery); // DeepSeek uses OpenAI format
                default -> callOpenAIAPI(config, systemPrompt, naturalLanguageQuery);
            };
            
            return extractContent(config.getApiProvider(), response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: Request was interrupted: " + e.getMessage();
        } catch (IOException e) {
            return "Error: Failed to connect to AI service: " + e.getMessage();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String buildSystemPrompt(String schemaInfo, String databaseType) {
        return """
            You are an expert SQL query generator. Your task is to convert natural language descriptions into valid SQL queries.
            
            DATABASE TYPE: %s
            
            DATABASE SCHEMA:
            %s
            
            INSTRUCTIONS:
            1. Generate only the SQL query, no explanations.
            2. Use appropriate SQL syntax for %s.
            3. Include proper table and column names from the schema.
            4. Return ONLY the SQL query without markdown backticks or additional text.""".formatted(databaseType, schemaInfo, databaseType);
    }

    private String callOpenAIAPI(AIConfig config, String systemPrompt, String userQuery) throws IOException, InterruptedException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        
        if (config.getModel().startsWith("o1") || config.getModel().contains("nano")) {
            requestBody.addProperty("max_completion_tokens", config.getMaxTokens());
        } else {
            requestBody.addProperty("temperature", config.getTemperature());
            requestBody.addProperty("max_tokens", config.getMaxTokens());
        }

        JsonArray messages = new JsonArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(createMsg("system", systemPrompt));
        }
        messages.add(createMsg("user", userQuery));
        requestBody.add("messages", messages);

        String url = config.getBaseUrl() + "/chat/completions";
        return executeRequest(url, requestBody.toString(), Map.of(
            "Content-Type", "application/json",
            "Authorization", "Bearer " + config.getApiKey()
        ));
    }

    private String callOpenAICompatibleAPI(AIConfig config, String systemPrompt, String userQuery) throws IOException, InterruptedException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("temperature", config.getTemperature());
        requestBody.addProperty("max_tokens", config.getMaxTokens());
        requestBody.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(createMsg("system", systemPrompt));
        }
        messages.add(createMsg("user", userQuery));
        requestBody.add("messages", messages);

        String url = config.getBaseUrl() + "/chat/completions";
        return executeRequest(url, requestBody.toString(), Map.of(
            "Content-Type", "application/json",
            "Authorization", "Bearer " + config.getApiKey()
        ));
    }

    private String callClaudeAPI(AIConfig config, String systemPrompt, String userQuery) throws IOException, InterruptedException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("max_tokens", config.getMaxTokens());
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            requestBody.addProperty("system", systemPrompt);
        }

        JsonArray messages = new JsonArray();
        messages.add(createMsg("user", userQuery));
        requestBody.add("messages", messages);

        String url = config.getBaseUrl() + "/messages";
        return executeRequest(url, requestBody.toString(), Map.of(
            "content-type", "application/json",
            "x-api-key", config.getApiKey(),
            "anthropic-version", "2023-06-01"
        ));
    }

    private String callGeminiAPI(AIConfig config, String systemPrompt, String userQuery) throws IOException, InterruptedException {
        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();

        JsonObject part = new JsonObject();
        JsonArray parts = new JsonArray();
        part.addProperty("text", userQuery);
        parts.add(part);

        JsonObject content = new JsonObject();
        content.add("parts", parts);
        contents.add(content);
        requestBody.add("contents", contents);

        // Pass system prompt via system_instruction (correct Gemini API field)
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject sysInstruction = new JsonObject();
            JsonArray sysParts = new JsonArray();
            JsonObject sysPart = new JsonObject();
            sysPart.addProperty("text", systemPrompt);
            sysParts.add(sysPart);
            sysInstruction.add("parts", sysParts);
            requestBody.add("system_instruction", sysInstruction);
        }

        String url = config.getBaseUrl() + "/models/" + config.getModel() + ":generateContent?key=" + config.getApiKey();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) return response.body();
        throw new IOException("Gemini API error (" + response.statusCode() + "): " + response.body());
    }

    private JsonObject createMsg(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content);
        return msg;
    }

    private String executeRequest(String url, String body, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(java.time.Duration.ofSeconds(30));
        headers.forEach(builder::header);
        
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) return response.body();
        throw new IOException("API error (" + response.statusCode() + "): " + response.body());
    }

    public String testConnection(AIConfig config) throws Exception {
        String query = "Hello";
        String response = switch (config.getApiProvider()) {
            case "Claude" -> callClaudeAPI(config, null, query);
            case "Gemini" -> callGeminiAPI(config, null, query);
            case "DeepSeek" -> callOpenAICompatibleAPI(config, null, query);
            default -> callOpenAIAPI(config, null, query);
        };
        
        String result = extractContent(config.getApiProvider(), response);
        return "Success! " + config.getModel() + " replied: " + result;
    }

    private String extractContent(String provider, String response) {
        try {
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if ("Claude".equals(provider)) {
                return json.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
            } else if ("Gemini".equals(provider)) {
                return json.getAsJsonArray("candidates").get(0).getAsJsonObject()
                        .get("content").getAsJsonObject().getAsJsonArray("parts").get(0).getAsJsonObject()
                        .get("text").getAsString();
            } else {
                return json.getAsJsonArray("choices").get(0).getAsJsonObject()
                        .get("message").getAsJsonObject().get("content").getAsString();
            }
        } catch (Exception e) {
            return "Error parsing response: " + response;
        }
    }

    public boolean isConfigured() { return configManager.isConfigured(); }
}
