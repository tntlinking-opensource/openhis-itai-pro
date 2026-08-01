/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.application.integration;

import com.openhis.whale.crb.application.support.CrbConfigMaskSupport;
import com.openhis.whale.crb.domain.config.CrbProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 前置连通性探测 AppService（POST /api/app/infectiousIntegration/{methodName}）。
 *
 * <p><b>DOC-CONFLICT：</b>AS-CRB-MON-001-SDD IF-04 要求调用 {@code NationalFrontSoftwareClient#probeConnectivity}；
 * 本实现仅读 {@link CrbProperties} 做配置存在性探测（application 不可依赖 gateway 发 HTTP）。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class InfectiousIntegrationAppService {

    private final CrbProperties crbProperties;

    /**
     * 探测前置软件配置是否齐备（不发 HTTP）。
     *
     * @return 配置探测结果
     */
    public FrontSoftwareConnectivityDto probeFrontSoftwareConnectivity() {
        // Pre：读取 yml 绑定配置
        CrbProperties.FrontSoftware frontSoftware = crbProperties.getFrontSoftware();
        String baseUrl = trimToNull(frontSoftware.getBaseUrl());
        String orgCode = trimToNull(frontSoftware.getOrgCode());
        String license = trimToNull(frontSoftware.getLicense());
        FrontSoftwareConnectivityDto dto = new FrontSoftwareConnectivityDto();
        dto.setBaseUrlMasked(CrbConfigMaskSupport.maskBaseUrl(baseUrl));
        dto.setOrgCode(orgCode);
        boolean configured = baseUrl != null && orgCode != null && license != null;
        dto.setConfigured(configured);
        // 按配置项缺失给出可读提示
        if (configured) {
            dto.setMessage("前置 baseUrl/orgCode/license 均已配置（未执行 HTTP 探测）");
        } else {
            dto.setMessage("前置配置不完整：请检查 openhis.crb.front-software.*");
        }
        return dto;
    }

    /**
     * 前置配置探测结果 DTO。
     */
    public static class FrontSoftwareConnectivityDto {

        /** 是否三项配置均非空。 */
        private boolean configured;

        /** 脱敏 baseUrl。 */
        private String baseUrlMasked;

        /** 国家机构代码。 */
        private String orgCode;

        /** 人类可读说明。 */
        private String message;

        /**
         * @return 是否已配置
         */
        public boolean isConfigured() {
            return configured;
        }

        /**
         * @param configured 是否已配置
         */
        public void setConfigured(boolean configured) {
            this.configured = configured;
        }

        /**
         * @return 脱敏 baseUrl
         */
        public String getBaseUrlMasked() {
            return baseUrlMasked;
        }

        /**
         * @param baseUrlMasked 脱敏 baseUrl
         */
        public void setBaseUrlMasked(String baseUrlMasked) {
            this.baseUrlMasked = baseUrlMasked;
        }

        /**
         * @return 机构代码
         */
        public String getOrgCode() {
            return orgCode;
        }

        /**
         * @param orgCode 机构代码
         */
        public void setOrgCode(String orgCode) {
            this.orgCode = orgCode;
        }

        /**
         * @return 说明
         */
        public String getMessage() {
            return message;
        }

        /**
         * @param message 说明
         */
        public void setMessage(String message) {
            this.message = message;
        }
    }
}
