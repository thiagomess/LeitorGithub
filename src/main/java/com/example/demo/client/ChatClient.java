package com.example.demo.client;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
public class ChatClient {

    private final WebClient webClient;
    private final String chatEndpoint;
   

    public ChatClient(WebClient.Builder builder,
                      @Value("${chat.endpoint:https://genai-inference-app.stackspot.com/v1/agent/01JZ9J6GT997JENKZ9VH77F0TY/chat}") String chatEndpoint) {
        this.webClient = builder.build();
        this.chatEndpoint = chatEndpoint;
     }

    public String callChatEndpoint(List<String> uploadIds, String userPrompt, String fixedJwt) {
        JSONObject body = new JSONObject();
        body.put("streaming", false);
        body.put("user_prompt", userPrompt);
        body.put("stackspot_knowledge", false);
        body.put("return_ks_in_response", true);
        body.put("upload_ids", new JSONArray(uploadIds));

        return webClient.post()
            .uri(chatEndpoint)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + fixedJwt)
            .bodyValue(body.toString())
            .retrieve()
            .bodyToMono(String.class)
            .block();
    }
}