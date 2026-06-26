/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.domain.inventory.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.repository.YbInvUploadRepository;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 3506 关联原 3505 销售流水（{@code medins_prod_sel_no}）。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventorySaleUploadReferenceLoader {

    private final YbInvUploadRepository uploadRepository;

    /**
     * 优先取已成功 3505 上报报文中的 {@code rtal_docno}；否则回落发放单 busNo。
     */
    public String resolveMedinsProdSelNo(Long dispenseId, String fallbackBusNo) {
        if (dispenseId == null) {
            return trimToNull(fallbackBusNo);
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        Optional<YbInvUpload> saleUpload = uploadRepository
            .findFirstByTenantIdAndInfnoAndSourceLineIdAndUploadStatusOrderByIdDesc(
                tenantId,
                ChsInfNo.INVENTORY_SALE,
                dispenseId,
                ChsInventoryConstants.UPLOAD_STATUS_SUCCESS);
        if (saleUpload.isEmpty()) {
            return trimToNull(fallbackBusNo);
        }
        String fromRequest = extractRtalDocno(saleUpload.get().getRequestJson());
        if (StringUtils.isNotBlank(fromRequest)) {
            return fromRequest.trim();
        }
        return trimToNull(fallbackBusNo);
    }

    private static String extractRtalDocno(String requestJson) {
        if (StringUtils.isBlank(requestJson)) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(requestJson);
            JSONObject selinfo = root.getJSONObject("selinfo");
            if (selinfo == null) {
                return null;
            }
            String rtalDocno = selinfo.getString("rtal_docno");
            if (StringUtils.isBlank(rtalDocno)) {
                rtalDocno = selinfo.getString("rtalDocno");
            }
            return rtalDocno;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}