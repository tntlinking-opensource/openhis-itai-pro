/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.domain.viewerendpoint.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 阅片根地址配置聚合根（{@code pacs_viewer_endpoint}）。
 */
@Getter
@Setter
@Entity
@Table(name = "pacs_viewer_endpoint")
public class ViewerEndpoint extends BaseAuditedEntity {

    private static final String STUDY_INSTANCE_UIDS = "StudyInstanceUIDs";

    /** 机构标识；空表示全局默认。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 模态码；空表示该范围默认。 */
    @Column(name = "modality_code")
    private String modalityCode;

    /** OHIF http(s) 根地址，不含 StudyUID。 */
    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    /** 是否启用。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /**
     * 校验 baseUrl（BR-IMG-06）。
     *
     * @param baseUrl 根地址
     */
    public void assertBaseUrl(String baseUrl) {
        if (StringUtils.isEmpty(baseUrl)) {
            throw new BusinessException(ErrorCodeConstants.PACS_VIEWER_ENDPOINT_INVALID, "baseUrl 不能为空");
        }
        String normalized = baseUrl.trim();
        boolean http = normalized.startsWith("http://") || normalized.startsWith("https://");
        if (!http) {
            throw new BusinessException(ErrorCodeConstants.PACS_VIEWER_ENDPOINT_INVALID, "baseUrl 必须以 http:// 或 https:// 开头");
        }
        if (normalized.contains(STUDY_INSTANCE_UIDS)) {
            throw new BusinessException(ErrorCodeConstants.PACS_VIEWER_ENDPOINT_INVALID, "baseUrl 不得包含 StudyInstanceUIDs");
        }
    }

    /**
     * 停用本端点（BR-IMG-05）。
     */
    public void disable() {
        this.enabled = Boolean.FALSE;
    }
}
