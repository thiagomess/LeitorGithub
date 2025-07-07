package com.example.demo.client;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Component
public class GithubClient {
    private static final String GITHUB_REPO_ZIP = "https://github.com/Pastor/modules/archive/refs/heads/master.zip";
    private static final String ZIP_FILE = "repo.zip";

    public String downloadRepoAsZip() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Resource> response = restTemplate.exchange(
            GITHUB_REPO_ZIP, HttpMethod.GET, null, Resource.class);
        try (InputStream in = response.getBody().getInputStream()) {
            Files.copy(in, Paths.get(ZIP_FILE), StandardCopyOption.REPLACE_EXISTING);
        }
        return ZIP_FILE;
    }
}
