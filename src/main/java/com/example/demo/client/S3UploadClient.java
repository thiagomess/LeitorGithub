package com.example.demo.client;

import java.nio.file.Path;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class S3UploadClient {

    private final WebClient webClient;

    public S3UploadClient(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    public Mono<String> uploadFileToEndpoint(Path filePath, String jwt) {
        String requestJson = String.format(
                "{\"file_name\": \"%s\", \"target_type\": \"CONTEXT\", \"expiration\": 60}",
                filePath.getFileName().toString());

        return webClient.post()
                .uri("https://data-integration-api.stackspot.com/v2/file-upload/form")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .bodyValue(requestJson)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(formResponse -> {
                    return Mono.fromCallable(() -> {
                        JSONObject uploadData = new JSONObject(formResponse);
                        String url = uploadData.getString("url");
                        JSONObject form = uploadData.getJSONObject("form");

                        try (CloseableHttpClient client = HttpClients.createDefault()) {
                            HttpPost post = new HttpPost(url);

                            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                            builder.addTextBody("key", form.getString("key"));
                            builder.addTextBody("x-amz-algorithm", form.getString("x-amz-algorithm"));
                            builder.addTextBody("x-amz-credential", form.getString("x-amz-credential"));
                            builder.addTextBody("x-amz-date", form.getString("x-amz-date"));
                            builder.addTextBody("x-amz-security-token", form.getString("x-amz-security-token"));
                            builder.addTextBody("policy", form.getString("policy"));
                            builder.addTextBody("x-amz-signature", form.getString("x-amz-signature"));
                            builder.addBinaryBody("file", filePath.toFile());

                            HttpEntity multipart = builder.build();
                            post.setEntity(multipart);

                            try (CloseableHttpResponse response = client.execute(post)) {
                                int statusCode = response.getStatusLine().getStatusCode();
                                if (statusCode != 204 && statusCode != 201 && statusCode != 200) {
                                    throw new RuntimeException("Upload failed: " + statusCode);
                                }
                            }
                        }

                        return uploadData.getString("id");
                    });
                });
    }
}
