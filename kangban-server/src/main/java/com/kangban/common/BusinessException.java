package com.kangban.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    private final int httpStatus;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
        this.httpStatus = HttpStatus.BAD_REQUEST.value();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = HttpStatus.BAD_REQUEST.value();
    }

    public BusinessException(int code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(404, message, HttpStatus.NOT_FOUND.value());
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, message, HttpStatus.UNAUTHORIZED.value());
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message, HttpStatus.FORBIDDEN.value());
    }

    public static BusinessException paramsError(String message) {
        return new BusinessException(400, message, HttpStatus.BAD_REQUEST.value());
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(409, message, HttpStatus.CONFLICT.value());
    }

    public static BusinessException tooManyRequests(String message) {
        return new BusinessException(429, message, HttpStatus.TOO_MANY_REQUESTS.value());
    }
}
