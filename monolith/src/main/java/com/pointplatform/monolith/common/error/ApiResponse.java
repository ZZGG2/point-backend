package com.pointplatform.monolith.common.error;

/** 공통 응답 포맷 { code, data, message } */
public record ApiResponse<T>(String code, T data, String message) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", data, null);
    }

    public static ApiResponse<Void> error(ErrorCode code) {
        return new ApiResponse<>(code.name(), null, code.message());
    }
}
