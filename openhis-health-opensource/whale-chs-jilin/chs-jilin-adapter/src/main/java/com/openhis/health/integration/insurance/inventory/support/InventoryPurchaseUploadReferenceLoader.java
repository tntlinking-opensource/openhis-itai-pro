/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.domain.inventory.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.repository.YbInvUploadRepository;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 3504 关联原 3503 采购流水（{@code medins_prod_purc_no}）。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryPurchaseUploadReferenceLoader {

    private final YbInvUploadRepository uploadRepository;
    private final SupplyRequestQueryService supplyRequestQueryService;

    public String resolveMedinsProdPurcNoForReturn(SupplyRequestModel returnLine, String fallbackBchno) {
        Long inboundLineId = resolveMatchingInboundLineId(returnLine);
        if (inboundLineId != null) {
            return resolveMedinsProdPurcNo(inboundLineId, fallbackBchno);
        }
        return trimToNull(fallbackBchno);
    }

    public String resolveMedinsProdPurcNo(Long supplyRequestLineId, String fallbackBchno) {
        if (supplyRequestLineId == null) {
            return trimToNull(fallbackBchno);
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        Optional<YbInvUpload> purchaseUpload = uploadRepository
            .findFirstByTenantIdAndInfnoAndSourceLineIdAndUploadStatusOrderByIdDesc(
                tenantId,
                ChsInfNo.INVENTORY_PURCHASE,
                supplyRequestLineId,
                ChsInventoryConstants.UPLOAD_STATUS_SUCCESS);
        if (purchaseUpload.isEmpty()) {
            return trimToNull(fallbackBchno);
        }
        String fromRequest = extractFixmedinsBchno(purchaseUpload.get().getRequestJson());
        if (StringUtils.isNotBlank(fromRequest)) {
            return fromRequest.trim();
        }
        if (StringUtils.isNotBlank(purchaseUpload.get().getFixmedinsBchno())) {
            return purchaseUpload.get().getFixmedinsBchno().trim();
        }
        return trimToNull(fallbackBchno);
    }

    private Long resolveMatchingInboundLineId(SupplyRequestModel returnLine) {
        if (returnLine == null || StringUtils.isBlank(returnLine.getOriginalBusNo())) {
            return null;
        }
        SupplyByBusNoQuery query = new SupplyByBusNoQuery();
        query.setBusNo(returnLine.getOriginalBusNo().trim());
        List<SupplyRequestModel> inboundLines = supplyRequestQueryService.getSupplyByBusNo(query);
        Long exactMatch = null;
        Long itemMatch = null;
        for (SupplyRequestModel row : inboundLines) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getItemTable())) {
                continue;
            }
            if (!SupplyType.CABINET_INBOUND_RECEIPT.getCode().equals(row.getTypeEnum())) {
                continue;
            }
            if (!Objects.equals(row.getItemId(), returnLine.getItemId())) {
                continue;
            }
            if (itemMatch == null) {
                itemMatch = row.getId();
            }
            if (StringUtils.isNotBlank(returnLine.getLotNumber())
                && returnLine.getLotNumber().trim().equals(trimToEmpty(row.getLotNumber()))) {
                exactMatch = row.getId();
                break;
            }
        }
        if (exactMatch != null) {
            return exactMatch;
        }
        return itemMatch;
    }

    private static String extractFixmedinsBchno(String requestJson) {
        if (StringUtils.isBlank(requestJson)) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(requestJson);
            JSONObject purcinfo = root.getJSONObject("purcinfo");
            if (purcinfo == null) {
                return null;
            }
            String bchno = purcinfo.getString("fixmedins_bchno");
            if (StringUtils.isBlank(bchno)) {
                bchno = purcinfo.getString("fixmedinsBchno");
            }
            return bchno;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}