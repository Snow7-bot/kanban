package com.kangban.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一 API 响应格式
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一响应")
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "状态码 0=成功")
    private int code;

    @Schema(description = "消息")
    private String message;

    @Schema(description = "数据")
    private T data;

    public static <T> Result<T> success() {
        return new Result<>(0, "成功", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "成功", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(0, message, data);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    public static <T> Result<T> forbidden(String message) {
        return new Result<>(403, message, null);
    }

    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }

    public static <T> Result<T> paramsError(String message) {
        return new Result<>(400, message, null);
    }
}
