/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.dto;

import lombok.Data;

import java.util.Map;

/**
 * 统一响应格式
 * 对应ABP的AjaxResponse
 */
@Data
public class Result<T> {

    /** 成功 */
    public static final int SUCCESS = 200;

    /** 失败 */
    public static final int FAIL = 500;

    private int code;
    private boolean success;
    private T data;
    private String message;
    private String errorCode;
    private Map<String, Object> errors;  // 验证错误详情

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setData(data);
        result.setCode(Result.SUCCESS);
        return result;
    }

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setCode(Result.SUCCESS);
        return result;
    }

    public static <T> Result<T> error(String errorCode, String message) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(Result.FAIL);
        result.setErrorCode(errorCode);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(String errorCode, String message, Map<String, Object> errors) {
        Result<T> result = new Result<>();
        result.setCode(Result.FAIL);
        result.setSuccess(false);
        result.setErrorCode(errorCode);
        result.setMessage(message);
        result.setErrors(errors);
        return result;
    }
}
