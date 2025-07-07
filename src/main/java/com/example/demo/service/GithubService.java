package com.example.demo.service;

import com.example.demo.client.GithubClient;
import com.example.demo.util.FileUtil;
import org.springframework.stereotype.Service;

@Service
public class GithubService {

    private final GithubClient githubClient;

    public GithubService(GithubClient githubClient) {
        this.githubClient = githubClient;
    }

    public String downloadAndExtractRepo() throws Exception {
        String zipPath = githubClient.downloadRepoAsZip();
        String extractDir = FileUtil.unzip(zipPath, "repo");
        return extractDir;
    }
}
