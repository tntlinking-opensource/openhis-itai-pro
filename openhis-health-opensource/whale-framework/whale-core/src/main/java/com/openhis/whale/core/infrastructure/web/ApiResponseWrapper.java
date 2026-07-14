/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.web;

import com.openhis.whale.core.application.dto.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * API响应包装器
 * 自动将返回值包装为Result格式（如果还没有包装）
 * 注意：AppServiceControllerRegistrar已经返回Result，这里主要处理其他Controller
 */
@RestControllerAdvice(basePackages = "com.openhis.whale")
public class ApiResponseWrapper implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 如果返回值已经是Result类型，不需要再次包装
        return !Result.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        // 如果body已经是Result类型，直接返回
        if (body instanceof Result) {
            return body;
        }

        // 对于void方法，返回成功结果
        if (body == null && returnType.getParameterType() == void.class) {
            return Result.success();
        }

        // 其他情况，包装为Result
        return Result.success(body);
    }
}
