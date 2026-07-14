/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.service.YbInvUploadQueryService;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 3506 销售退货关联原 3505 销售流水（{@code medins_prod_sel_no}）解析器。
 *
 * <p>优先从已成功 3505 上报审计（{@code yb_inv_upload}）报文中提取 {@code rtal_docno}；
 * 无历史记录时回落发药单 busNo。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventorySaleUploadReferenceResolver {

    private final YbInvUploadQueryService uploadQueryService;

    /**
     * 优先取已成功 3505 上报报文中的 {@code rtal_docno}；否则回落发放单 busNo */
    public String resolveMedinsProdSelNo(Long dispenseId, String fallbackBusNo) {
        if (dispenseId == null) {
            return trimToNull(fallbackBusNo);
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        Optional<YbInvUpload> saleUpload = uploadQueryService.findLatestSuccessfulBySourceLine(
            tenantId,
            ChsInfNo.INVENTORY_SALE,
            dispenseId,
            ChsInventoryConstants.UPLOAD_STATUS_SUCCESS);
        if (saleUpload.isEmpty()) {
            return trimToNull(fallbackBusNo);
        }
        String fromRequest = extractRtalDocno(saleUpload.get().getRequestJson());
        if (isNotEmpty(fromRequest)) {
            return fromRequest.trim();
        }
        return trimToNull(fallbackBusNo);
    }

    /**
     * 从 3505 请求报文中提取零售单据号。
     *
     * @param requestJson 请求报文 JSON
     * @return rtal_docno，解析失败时返回 null
     */
    private static String extractRtalDocno(String requestJson) {
        if (isEmpty(requestJson)) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(requestJson);
            JSONObject selinfo = root.getJSONObject("selinfo");
            if (selinfo == null) {
                return null;
            }
            String rtalDocno = selinfo.getString("rtal_docno");
            if (isEmpty(rtalDocno)) {
                rtalDocno = selinfo.getString("rtalDocno");
            }
            return rtalDocno;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
