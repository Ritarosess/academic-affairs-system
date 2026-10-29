package com.school.edu.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体。code = 200 表示成功，其余为业务/系统错误码。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "操作成功", data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    public static Result<Void> ok() {
        return new Result<>(200, "操作成功", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }
}
