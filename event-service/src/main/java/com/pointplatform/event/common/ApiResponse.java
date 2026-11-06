package com.pointplatform.event.common;

public record ApiResponse<T>(String code, T data, String message) {
    public static <T> ApiResponse<T> of(String code, T data, String message) {
        return new ApiResponse<>(code, data, message);
    }
}
