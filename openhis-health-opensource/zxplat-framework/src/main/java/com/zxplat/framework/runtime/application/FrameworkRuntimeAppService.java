/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.runtime.application;

import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.runtime.application.dto.FrameworkRuntimeIngestRequest;
import com.zxplat.framework.runtime.collector.domain.FrameworkRuntimeState;
import com.zxplat.framework.runtime.collector.repository.FrameworkRuntimeStateRepository;
import com.zxplat.framework.runtime.config.FrameworkRuntimeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 框架运行时 ingest 端点（仅中心监控节点启用）。
 */
@Service
@ConditionalOnProperty(prefix = "zxplat.framework.runtime", name = "collector-enabled", havingValue = "true")
public class FrameworkRuntimeAppService {

    private final FrameworkRuntimeProperties properties;
    private final FrameworkRuntimeStateRepository repository;

    public FrameworkRuntimeAppService(
            FrameworkRuntimeProperties properties,
            FrameworkRuntimeStateRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    public void ingest(FrameworkRuntimeIngestRequest request) {
        assertAuthorized();
        if (request == null || request.getInsightCode() == null || request.getInsightCode().isBlank()) {
            throw new ValidationException("insightCode 不能为空");
        }

        FrameworkRuntimeState entity = new FrameworkRuntimeState();
        entity.setInsightCode(request.getInsightCode());
        entity.setCategory(request.getCategory());
        entity.setRuntimeVersion(request.getRuntimeVersion());
        entity.setOperatorUserId(request.getOperatorUserId());
        entity.setSessionStartedAt(request.getSessionStartedAt());
        entity.setOrgCategory(request.getOrgCategory());
        entity.setOrgName(request.getOrgName());
        entity.setDeploymentFingerprint(request.getDeploymentFingerprint());
        entity.setLicenseCustomer(request.getLicenseCustomer());
        entity.setLicenseNotAfter(request.getLicenseNotAfter());
        entity.setPayloadJson(request.getPayloadJson());
        entity.setObservedAt(request.getObservedAt());
        entity.setReceivedAt(LocalDateTime.now());
        repository.save(entity);
    }

    private void assertAuthorized() {
        String expected = properties.getSyncToken();
        if (!StringUtils.hasText(expected) || expected.trim().length() < 16) {
            throw new ValidationException("framework runtime ingest unauthorized");
        }
        String actual = FrameworkRuntimeIngestAuthContext.currentToken();
        if (expected.equals(actual)) {
            return;
        }
        throw new ValidationException("framework runtime ingest unauthorized");
    }
}
