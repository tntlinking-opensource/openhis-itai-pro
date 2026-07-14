/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.assembler;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.service.YbInvUploadQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 3504 采购退货关联原 3503 采购流水（{@code medins_prod_purc_no}）解析器。
 *
 * <p>通过退货行 originalBusNo 匹配原进货 supply_request 行，
 * 再从已成功 3503 上报审计中提取 {@code fixmedins_bchno}。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryPurchaseUploadReferenceResolver {

    private final YbInvUploadQueryService uploadQueryService;
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
        Optional<YbInvUpload> purchaseUpload = uploadQueryService.findLatestSuccessfulBySourceLine(
            tenantId,
            ChsInfNo.INVENTORY_PURCHASE,
            supplyRequestLineId,
            ChsInventoryConstants.UPLOAD_STATUS_SUCCESS);
        if (purchaseUpload.isEmpty()) {
            return trimToNull(fallbackBchno);
        }
        String fromRequest = extractFixmedinsBchno(purchaseUpload.get().getRequestJson());
        if (isNotEmpty(fromRequest)) {
            return fromRequest.trim();
        }
        if (isNotEmpty(purchaseUpload.get().getFixmedinsBchno())) {
            return purchaseUpload.get().getFixmedinsBchno().trim();
        }
        return trimToNull(fallbackBchno);
    }

    /**
     * 按退货行匹配原进货 supply_request 行 ID。
     *
     * @param returnLine 退货行
     * @return 匹配的进货行 ID，未匹配时返回 null
     */
    private Long resolveMatchingInboundLineId(SupplyRequestModel returnLine) {
        if (returnLine == null || isEmpty(returnLine.getOriginalBusNo())) {
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
            if (isNotEmpty(returnLine.getLotNumber())
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

    /**
     * 从 3503 请求报文中提取机构批次流水号。
     *
     * @param requestJson 请求报文 JSON
     * @return fixmedins_bchno，解析失败时返回 null
     */
    private static String extractFixmedinsBchno(String requestJson) {
        if (isEmpty(requestJson)) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(requestJson);
            JSONObject purcinfo = root.getJSONObject("purcinfo");
            if (purcinfo == null) {
                return null;
            }
            String bchno = purcinfo.getString("fixmedins_bchno");
            if (isEmpty(bchno)) {
                bchno = purcinfo.getString("fixmedinsBchno");
            }
            return bchno;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
