/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.web.exception;

import com.openhis.whale.core.application.dto.Result;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.UnauthorizedException;
import com.openhis.whale.core.application.exception.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Object>> handleBusinessException(BusinessException e) {
        Throwable root = getRootCause(e);
        StackTraceElement location = findPreferredBusinessLocation(e);
        if (location != null) {
            logger.warn("业务异常: {} | rootType={} | rootMsg={} ({}.{}, line={})",
                e.getMessage(),
                root.getClass().getName(),
                root.getMessage(),
                location.getClassName(),
                location.getMethodName(),
                location.getLineNumber());
            logIdeStackFrameHint(logger, false, location);
        } else {
            logger.warn("业务异常: {} | rootType={} | rootMsg={}", e.getMessage(), root.getClass().getName(), root.getMessage());
        }
        Result<Object> result = Result.error(e.getErrorCode(), e.getMessage());
        Map<String, Object> details = e.getDetails();
        if (details != null && !details.isEmpty()) {
            result.setErrors(details);
        }
        if ("SERVICE_UNAVAILABLE".equals(e.getErrorCode())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Result<Object>> handleEntityNotFoundException(EntityNotFoundException e) {
        logger.warn("实体不存在: {}", e.getMessage());
        Result<Object> result = Result.error(e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result<Object>> handleValidationException(ValidationException e) {
        logger.warn("验证异常: {}", e.getMessage());
        Map<String, Object> errors = new HashMap<>();
        if (e.getErrors() != null) {
            errors.putAll(e.getErrors());
        }
        Result<Object> result = Result.error(e.getErrorCode(), e.getMessage(), errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Result<Object>> handleUnauthorizedException(UnauthorizedException e) {
        logger.warn("未授权异常: {}", e.getMessage());
        Result<Object> result = Result.error(e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String, Object> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        logger.warn("参数验证失败: {}", errors);
        Result<Object> result = Result.error("VALIDATION_ERROR", "参数验证失败", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Object>> handleConstraintViolationException(ConstraintViolationException e) {
        Map<String, Object> errors = e.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage
                ));
        logger.warn("约束验证失败: {}", errors);
        Result<Object> result = Result.error("VALIDATION_ERROR", "约束验证失败", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理 Spring Security 访问拒绝异常（403）
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Object>> handleAccessDeniedException(AccessDeniedException e) {
        logger.warn("访问被拒绝: {}", e.getMessage());
        Result<Object> result = Result.error("FORBIDDEN", "没有权限访问该资源");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
    }

    /**
     * 处理 Spring Security 认证异常（401）
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Object>> handleAuthenticationException(AuthenticationException e) {
        logger.warn("认证失败: {}", e.getMessage());
        Result<Object> result = Result.error("UNAUTHORIZED", "未授权访问，请先登录");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    /**
     * 处理认证凭证未找到异常（401）
     */
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Result<Object>> handleAuthenticationCredentialsNotFoundException(AuthenticationCredentialsNotFoundException e) {
        logger.warn("认证凭证未找到: {}", e.getMessage());
        Result<Object> result = Result.error("UNAUTHORIZED", "未授权访问，请先登录");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    /**
     * 数据完整性约束违反：单独记录，便于与库侧 NOT NULL / FK / UNIQUE 信息对照；
     * 同时输出标准栈帧行，便于 IDE 控制台点击跳转到 com.openhis 业务代码。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Object>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        Throwable root = getRootCause(e);
        StackTraceElement location = findPreferredBusinessLocation(e);
        logger.error("数据完整性约束违反: rootType={}, rootMsg={}", root.getClass().getName(), root.getMessage());
        if (location != null) {
            logIdeStackFrameHint(logger, true, location);
        }
        logger.error("数据完整性约束违反（完整堆栈）", e);
        String rootMessage = root.getMessage() == null ? "" : root.getMessage();
        if (rootMessage.contains("whale_users_user_name") || rootMessage.contains("(user_name)=")) {
            Result<Object> result = Result.error("VALIDATION_ERROR", "用户名已存在");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
        if (rootMessage.contains("idx_users_email_unique") || rootMessage.contains("(email)=")) {
            Result<Object> result = Result.error("VALIDATION_ERROR", "邮箱已存在");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
        Result<Object> result = Result.error("INTERNAL_ERROR", "系统内部错误");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleException(Exception e) {
        Throwable root = getRootCause(e);
        StackTraceElement location = findPreferredBusinessLocation(e);
        if (location != null) {
            logger.error("系统异常: type={}, rootType={}, rootMsg={}, location={}.{}:{}",
                e.getClass().getName(),
                root.getClass().getName(),
                root.getMessage(),
                location.getClassName(),
                location.getMethodName(),
                location.getLineNumber());
            logIdeStackFrameHint(logger, true, location);
            logger.error("系统异常（完整堆栈）", e);
        } else {
            logger.error("系统异常: type={}, rootType={}, rootMsg={}",
                e.getClass().getName(),
                root.getClass().getName(),
                root.getMessage(),
                e);
        }
        Result<Object> result = Result.error("INTERNAL_ERROR", "系统内部错误");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * 在整条异常链（含 cause）的栈帧中查找首选业务代码位置。
     * <p>JDBC/Hibernate 根因往往不含 com.openhis 帧，故需从外层 Spring 包装异常继续向上扫描。</p>
     */
    private StackTraceElement findPreferredBusinessLocation(Throwable throwable) {
        Set<Throwable> seen = new HashSet<>();
        for (Throwable t = throwable; t != null && !seen.contains(t); t = t.getCause()) {
            seen.add(t);
            StackTraceElement location = findBusinessLocation(t);
            if (location != null) {
                return location;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        Throwable root = getRootCause(throwable);
        return root.getStackTrace().length > 0 ? root.getStackTrace()[0] : null;
    }

    /**
     * 输出与 Throwable.printStackTrace 同格式的单行栈帧，IntelliJ 控制台可解析为可点击链接。
     */
    private static void logIdeStackFrameHint(Logger log, boolean errorLevel, StackTraceElement location) {
        String file = location.getFileName() != null ? location.getFileName() : "Unknown Source";
        // 与 Throwable.printStackTrace 完全一致的单行格式，便于 IDE 控制台解析为可点击栈链接
        String line = "\tat " + location.getClassName() + "." + location.getMethodName()
            + "(" + file + ":" + location.getLineNumber() + ")";
        if (errorLevel) {
            log.error(line);
        } else {
            log.warn(line);
        }
    }

    private StackTraceElement findBusinessLocation(Throwable throwable) {
        for (StackTraceElement element : throwable.getStackTrace()) {
            String className = element.getClassName();
            if (className.startsWith("com.openhis.health.")) {
                return element;
            }
        }
        for (StackTraceElement element : throwable.getStackTrace()) {
            String className = element.getClassName();
            if (className.startsWith("com.openhis.") && !className.startsWith("com.openhis.whale.core.")) {
                return element;
            }
        }
        return null;
    }
}
