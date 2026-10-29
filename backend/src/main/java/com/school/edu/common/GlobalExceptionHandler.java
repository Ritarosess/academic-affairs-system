package com.school.edu.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：保证任何异常都以统一响应体返回，且不向前端暴露堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：返回业务语义的错误信息。HTTP 状态保持 200，由响应体 code 表达错误。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：@Valid 在 @RequestBody 上 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.fail(400, msg.isEmpty() ? "参数校验失败" : msg);
    }

    /** 参数绑定失败：表单/查询参数 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.fail(400, msg.isEmpty() ? "参数校验失败" : msg);
    }

    /** 认证失败 */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleBadCredentials(BadCredentialsException e) {
        return Result.fail(401, "用户名或密码错误");
    }

    /** 账号被停用 */
    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleDisabled(DisabledException e) {
        return Result.fail(401, "账号已被停用，请联系管理员");
    }

    /** 越权访问（方法级 @PreAuthorize 触发） */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.fail(403, "无权访问该资源");
    }

    /**
     * 请求体不是合法 JSON、或字段类型无法转换。
     * 属于客户端错误（400），不应记为系统异常。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误: {}", e.getMostSpecificCause().getMessage());
        return Result.fail(400, "请求体格式错误，请检查提交的数据格式");
    }

    /** 路径变量 / 查询参数类型不匹配，例如 /api/admin/users/abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}={}", e.getName(), e.getValue());
        return Result.fail(400, "参数「" + e.getName() + "」格式不正确");
    }

    /** 缺少必填的查询参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return Result.fail(400, "缺少必填参数「" + e.getParameterName() + "」");
    }

    /** 请求方法不被支持，例如对只接受 GET 的接口发 POST */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return Result.fail(405, "请求方法不被支持：" + e.getMethod());
    }

    /**
     * 静态资源/处理器不存在。
     *
     * <p>典型场景：浏览器里的旧标签页、其它应用的页面，或已被删除的图片仍在请求
     * 本项目不存在的路径。这属于正常的 404，**不是系统异常** —— 若交给下面的兜底
     * 处理器，会打出 ERROR 级完整堆栈并返回 500，既污染日志又误导排错。</p>
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(Exception e) {
        log.warn("请求的资源不存在: {}", e.getMessage());
        return Result.fail(404, "请求的资源不存在");
    }

    /** 兜底：记录日志，返回通用错误，不暴露内部细节 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }
}
