/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.application.dict;

import com.openhis.whale.crb.domain.config.CrbOrgReportConfig;
import com.openhis.whale.crb.domain.config.CrbOrgReportConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;

/**
 * 传染病前置对照与机构配置 AppService（POST /api/app/infectiousDict/{methodName}）。
 */
@Service
@Validated
@RequiredArgsConstructor
public class InfectiousDictAppService {

    private final CrbOrgReportConfigRepository orgReportConfigRepository;

    /**
     * 读取当前租户的前置上报配置。
     *
     * @param tenantId 租户 id
     * @return 配置摘要（未配置时 empty）
     */
    public Optional<OrgReportConfigDto> getOrgReportConfig(Long tenantId) {
        // Gate：未传租户则无法定位院端配置
        if (tenantId == null) {
            return Optional.empty();
        }
        // Pre：按租户查启用配置
        Optional<CrbOrgReportConfig> config = orgReportConfigRepository
                .findFirstByTenantIdAndEnabledTrueAndIsDeletedFalse(tenantId);
        // Asm：映射为前端 DTO
        if (config.isEmpty()) {
            return Optional.empty();
        }
        CrbOrgReportConfig row = config.get();
        OrgReportConfigDto dto = new OrgReportConfigDto();
        dto.setOrgCode(row.getOrgCode());
        dto.setOrgName(row.getOrgName());
        dto.setBaseUrl(row.getBaseUrl());
        dto.setEnabled(row.getEnabled());
        return Optional.of(dto);
    }

    /** 院端前置配置只读 DTO。 */
    public static class OrgReportConfigDto {

        /** 国家机构代码。 */
        private String orgCode;

        /** 机构名称。 */
        private String orgName;

        /** 前置 baseUrl。 */
        private String baseUrl;

        /** 是否启用。 */
        private Boolean enabled;

        public String getOrgCode() {
            return orgCode;
        }

        public void setOrgCode(String orgCode) {
            this.orgCode = orgCode;
        }

        public String getOrgName() {
            return orgName;
        }

        public void setOrgName(String orgName) {
            this.orgName = orgName;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}
