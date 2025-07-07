package com.example.demo.dto;

import java.nio.file.Path;

public record ControllerMatch(
    Path filePath,
    String className,
    boolean scopeFound,
    boolean urlFound,
    String scope
) {}
