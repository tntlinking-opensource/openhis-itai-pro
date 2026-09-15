/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.mrms;

import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案入/出院科别出站码解析（4401 {@code adm_caty}/{@code dscg_caty}、4101A {@code dscg_caty}）。
 *
 * <p>平台要求长度 1–10。真源为科室维护的<strong>医保编码</strong>{@code adm_organization.yb_no}
 *（如 {@code A50.13}）。首页若误存院内 {@code bus_no}（如 {@code OR0007.OR0022}），
 * 按 bus_no 反查机构取 {@code yb_no}；已是短码则原样上送。</p>
 */
@Component
@RequiredArgsConstructor
public class MedcasOrgCatyOutboundSupport {

    /** 医保科别码平台上限（长春 FSI「出院科别长度1-10」）。 */
    private static final int MAX_CATY_LEN = 10;

    private final OrganizationQueryService organizationQueryService;

    /**
     * 解析科别出站码。
     *
     * @param rawHomepageCaty 首页 {@code adm_caty_code}/{@code dscg_caty_code}（可能是 yb_no 或误存的 bus_no）
     * @param fieldLabel      校验文案用字段名（如 {@code dscg_caty}）
     * @return 长度 1–10 的医保编码；入参空则 null
     */
    public String resolveOutboundCaty(String rawHomepageCaty, String fieldLabel) {
        String raw = trimToNull(rawHomepageCaty);
        if (raw == null) {
            return null;
        }
        // Pre：若首页存的是院内 bus_no，反查科室医保编码
        Map<String, String> ybByBus = organizationQueryService.mapYbNoByBusNos(List.of(raw));
        String fromOrg = trimToNull(ybByBus.get(raw));
        if (fromOrg != null) {
            assertCatyLength(fromOrg, fieldLabel);
            return fromOrg;
        }
        // 已是短医保码（自动取值改 yb_no 后的存量）
        assertCatyLength(raw, fieldLabel);
        return raw;
    }

    /**
     * 断言科别码长度符合平台 1–10。
     *
     * @param caty       出站码
     * @param fieldLabel 字段名
     */
    private void assertCatyLength(String caty, String fieldLabel) {
        int len = caty.length();
        if (len < 1 || len > MAX_CATY_LEN) {
            throw new BusinessException(
                    MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION,
                    fieldLabel + " 须为科室医保编码（长度1-10），当前值超长或无效：" + caty
                            + "；请在科室维护「医保编码」后重新带入首页科别");
        }
    }
}
