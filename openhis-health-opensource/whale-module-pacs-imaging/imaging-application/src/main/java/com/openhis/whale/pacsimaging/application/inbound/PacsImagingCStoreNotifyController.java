/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.inbound;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.pacsimaging.application.capture.request.OnCStoreReceivedRequest;
import com.openhis.whale.pacsimaging.application.capture.response.OnCStoreReceivedResponse;
import com.openhis.whale.pacsimaging.application.capture.service.PacsImageCaptureAppService;
import com.openhis.whale.pacsimaging.application.config.ImagingMiddlewareProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Orthanc C-STORE 稳定检查回调入口（IF-PACS-CAP-10 入站）。
 *
 * <p>鉴权：共享密钥头 {@code X-Imaging-Notify-Token}；租户由头或配置 defaultTenantId 注入。</p>
 */
@RestController
@RequestMapping("/api/integration/pacs-imaging")
@RequiredArgsConstructor
public class PacsImagingCStoreNotifyController {

    public static final String HEADER_NOTIFY_TOKEN = "X-Imaging-Notify-Token";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";

    private final PacsImageCaptureAppService pacsImageCaptureAppService;
    private final ImagingMiddlewareProperties imagingMiddlewareProperties;
    private final TenantContext tenantContext;

    /**
     * Orthanc Lua OnStableStudy → 幂等绑定 ImagingStudy + hasMedia。
     *
     * @param notifyToken 共享密钥
     * @param tenantIdHeader 可选租户头
     * @param request C-STORE 回写入参
     * @return 绑定结果
     */
    @PostMapping("/cstore-notify")
    public ResponseEntity<OnCStoreReceivedResponse> cstoreNotify(
        @RequestHeader(value = HEADER_NOTIFY_TOKEN, required = false) String notifyToken,
        @RequestHeader(value = HEADER_TENANT_ID, required = false) String tenantIdHeader,
        @RequestBody OnCStoreReceivedRequest request) {
        String expected = null;
        if (imagingMiddlewareProperties.getNotify() != null) {
            expected = imagingMiddlewareProperties.getNotify().getToken();
        }
        if (StringUtils.isEmpty(expected) || !expected.equals(notifyToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long tenantId = this.resolveTenantId(tenantIdHeader);
        if (tenantId == null) {
            throw new BusinessException("TENANT_REQUIRED", "缺少租户：请传 X-Tenant-Id 或配置 defaultTenantId");
        }
        tenantContext.setCurrentTenantId(tenantId);
        try {
            OnCStoreReceivedResponse response = pacsImageCaptureAppService.onCStoreReceived(request);
            return ResponseEntity.ok(response);
        } finally {
            tenantContext.setCurrentTenantId(null);
        }
    }

    /**
     * 解析租户：优先请求头，其次配置默认值。
     *
     * @param tenantIdHeader {@code X-Tenant-Id} 原文
     * @return 租户主键；都空则 null
     */
    private Long resolveTenantId(String tenantIdHeader) {
        if (StringUtils.isNotEmpty(tenantIdHeader)) {
            try {
                return Long.parseLong(tenantIdHeader.trim());
            } catch (NumberFormatException ex) {
                throw new BusinessException("TENANT_REQUIRED", "X-Tenant-Id 格式非法");
            }
        }
        if (imagingMiddlewareProperties.getNotify() != null) {
            return imagingMiddlewareProperties.getNotify().getDefaultTenantId();
        }
        return null;
    }
}
