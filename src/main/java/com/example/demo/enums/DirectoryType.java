package com.example.demo.enums;

public enum DirectoryType {
    CONTROLLER("controllers"),
    TEST("testes");

    private final String description;

    DirectoryType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}