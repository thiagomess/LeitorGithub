package com.example.demo.enums;

public enum TypeAction {
    CONTROLLER, UNIT_TEST, KARATE;

    public static TypeAction fromString(String action) {
        try {
            return TypeAction.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException("Unsupported action: " + action);
        }
    }
}