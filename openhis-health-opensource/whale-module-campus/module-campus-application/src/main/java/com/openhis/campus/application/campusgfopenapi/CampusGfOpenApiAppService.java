/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusgfopenapi;

import com.openhis.campus.application.campusgfopenapi.request.CreateAdjustRequestFromOpenApiRequest;
import com.openhis.campus.application.campusgfrequestrecord.CampusGfRequestRecordAppService;
import com.openhis.campus.application.campusgfrequestrecord.response.CreateAdjustRequestResponse;
import com.openhis.campus.domain.shared.CampusGfConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * 公费 Open API 应用服务。
 *
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/campusGfOpenApi/{methodName}}。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class CampusGfOpenApiAppService {

    private static final Logger log = LoggerFactory.getLogger(CampusGfOpenApiAppService.class);

    private final CampusGfRequestRecordAppService campusGfRequestRecordAppService;

    @Value("${campus.gf.open-api.security-key:}")
    private String configuredSecurityKey;

    public CreateAdjustRequestResponse createAdjustRequestFromOpenApi(
        @Valid CreateAdjustRequestFromOpenApiRequest request) {
        validateSecurityKey(request.getSecurityKey());
        resolveChargeMode(request);
        return campusGfRequestRecordAppService.createAdjustRequest(request);
    }

    private void validateSecurityKey(String securityKey) {
        if (configuredSecurityKey == null || configuredSecurityKey.isBlank()) {
            throw new BusinessException("GF_OPEN_API_DISABLED", "Open API 未配置 securityKey");
        }
        if (securityKey == null || !configuredSecurityKey.equals(securityKey)) {
            throw new BusinessException("GF_OPEN_API_UNAUTHORIZED", "Open API 鉴权失败");
        }
    }

    private void resolveChargeMode(CreateAdjustRequestFromOpenApiRequest request) {
        if (request.getChargeModeEnum() != null && !request.getChargeModeEnum().isBlank()) {
            CampusGfConstants.validateChargeMode(request.getChargeModeEnum());
            return;
        }
        if (Boolean.TRUE.equals(request.getLegacyDefaultModeCompat())) {
            log.warn("Open API createAdjustRequest missing chargeModeEnum; defaulting to SELF_PAY_GF (legacy compat)");
            request.setChargeModeEnum(CampusGfConstants.CHARGE_MODE_SELF_PAY_GF);
            return;
        }
        throw new BusinessException("GF_CHARGE_MODE_REQUIRED", "chargeModeEnum 必填");
    }
}
