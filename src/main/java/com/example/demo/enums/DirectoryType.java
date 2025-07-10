package com.example.demo.enums;

public enum DirectoryType {
    CONTROLLER("controllers"),
    SOURCE("código-fonte"),
    TEST("testes");

    private final String description;

    DirectoryType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}