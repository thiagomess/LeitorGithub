package com.example.demo.dto;

import java.util.List;

public record RepoContext(String zipPath, String extractDir, List<ControllerMatch> matches) {
}
