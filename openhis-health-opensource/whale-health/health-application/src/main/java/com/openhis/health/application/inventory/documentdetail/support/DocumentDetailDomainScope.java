/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.support;

import com.openhis.health.domain.share.constants.DocumentDetailExportConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;

/**
 * 单据详情报表业务域（药库 / 药房 / 科室药房），由请求 {@code domainScope} 传入。
 */
public enum DocumentDetailDomainScope {

    /** 药库域。 */
    WAREHOUSE(DocumentDetailExportConstants.WAREHOUSE_FILE_NAME_PREFIX),

    /** 药房域。 */
    PHARMACY(DocumentDetailExportConstants.PHARMACY_FILE_NAME_PREFIX),

    /** 科室药房域。 */
    DEPARTMENT(DocumentDetailExportConstants.DEPARTMENT_FILE_NAME_PREFIX);

    private final String exportFileNamePrefix;

    DocumentDetailDomainScope(String exportFileNamePrefix) {
        this.exportFileNamePrefix = exportFileNamePrefix;
    }

    /**
     * 导出文件名前缀（不含时间戳与扩展名）。
     *
     * @return 前缀
     */
    public String getExportFileNamePrefix() {
        return this.exportFileNamePrefix;
    }

    /**
     * 解析前端传入的业务域码（{@code WAREHOUSE}/{@code PHARMACY}/{@code DEPARTMENT}，大小写不敏感）。
     *
     * @param domainScope 请求字段
     * @return 枚举值
     * @throws BusinessException {@code INV_DDC_202} 缺失或非法
     */
    public static DocumentDetailDomainScope requireFromCode(String domainScope) {
        if (StringUtils.isEmpty(domainScope)) {
            throw new BusinessException(ErrorCodeConstants.INV_DDC_202, "业务域 domainScope 不能为空");
        }
        String normalized = domainScope.trim().toUpperCase();
        for (DocumentDetailDomainScope scope : DocumentDetailDomainScope.values()) {
            if (scope.name().equals(normalized)) {
                return scope;
            }
        }
        throw new BusinessException(ErrorCodeConstants.INV_DDC_202, "业务域 domainScope 无效");
    }
}
