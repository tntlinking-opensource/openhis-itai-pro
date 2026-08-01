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
 * 3504 采购退货关联原 3503 采购批次号解析器（吉林省）。
 *
 * <p>联调结论：省本级 3504 按原采购 {@code fixmedins_bchno} 定位 3503；退供单号（CRT）作
 * {@code fixmedins_bchno} 会报「未找到商品采购信息」。{@code medins_prod_purc_no} 与
 * {@code fixmedins_bchno} 均填原 3503 的 {@code fixmedins_bchno}（院内进货单号，如 CIR…）。</p>
 *
 * <p>通过退货行 {@code originalBusNo} 匹配原进货 supply_request 行，再从已成功 3503 上报审计提取批次号。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryPurchaseUploadReferenceResolver {

    private final YbInvUploadQueryService uploadQueryService;
    private final SupplyRequestQueryService supplyRequestQueryService;

    /**
     * 解析 3504 应对齐的原采购批次号（供 {@code fixmedins_bchno} 与 {@code medins_prod_purc_no}）。
     *
     * @param returnLine    退货出库行
     * @param fallbackBchno 无成功 3503 审计时的回落（应为进货单 busNo，禁止用退供 CRT）
     * @return 原 3503 {@code fixmedins_bchno}，解析失败时回落
     */
    public String resolvePurchaseFixmedinsBchnoForReturn(SupplyRequestModel returnLine, String fallbackBchno) {
        Long inboundLineId = resolveMatchingInboundLineId(returnLine);
        // 已匹配进货行：优先读成功 3503 审计中的 fixmedins_bchno
        if (inboundLineId != null) {
            return resolvePurchaseFixmedinsBchno(inboundLineId, fallbackBchno);
        }
        return trimToNull(fallbackBchno);
    }

    /**
     * 兼容旧调用名：语义同 {@link #resolvePurchaseFixmedinsBchnoForReturn}。
     *
     * @param returnLine    退货出库行
     * @param fallbackBchno 回落批次号
     * @return 原采购批次号
     */
    public String resolveMedinsProdPurcNoForReturn(SupplyRequestModel returnLine, String fallbackBchno) {
        return resolvePurchaseFixmedinsBchnoForReturn(returnLine, fallbackBchno);
    }

    /**
     * 按进货 supply_request 行 ID 解析原 3503 的 {@code fixmedins_bchno}。
     *
     * @param supplyRequestLineId 进货行主键
     * @param fallbackBchno       无成功审计时的回落
     * @return 原采购批次号
     */
    public String resolvePurchaseFixmedinsBchno(Long supplyRequestLineId, String fallbackBchno) {
        if (supplyRequestLineId == null) {
            return trimToNull(fallbackBchno);
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        Optional<YbInvUpload> purchaseUpload = uploadQueryService.findLatestSuccessfulBySourceLine(
            tenantId,
            ChsInfNo.INVENTORY_PURCHASE,
            supplyRequestLineId,
            ChsInventoryConstants.UPLOAD_STATUS_SUCCESS);
        // 无成功 3503：回落进货单号，避免误用退供 CRT
        if (purchaseUpload.isEmpty()) {
            return trimToNull(fallbackBchno);
        }
        String fromRequest = extractFixmedinsBchno(purchaseUpload.get().getRequestJson());
        // 优先报文内 purcinfo.fixmedins_bchno（与当时出站一致）
        if (isNotEmpty(fromRequest)) {
            return fromRequest.trim();
        }
        if (isNotEmpty(purchaseUpload.get().getFixmedinsBchno())) {
            return purchaseUpload.get().getFixmedinsBchno().trim();
        }
        return trimToNull(fallbackBchno);
    }

    /**
     * 兼容旧调用名：语义同 {@link #resolvePurchaseFixmedinsBchno}。
     *
     * @param supplyRequestLineId 进货行主键
     * @param fallbackBchno       回落批次号
     * @return 原采购批次号
     */
    public String resolveMedinsProdPurcNo(Long supplyRequestLineId, String fallbackBchno) {
        return resolvePurchaseFixmedinsBchno(supplyRequestLineId, fallbackBchno);
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
            // 仅药品行参与 3503/3504 关联
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getItemTable())) {
                continue;
            }
            // 仅药库进货入库类型
            if (!SupplyType.CABINET_INBOUND_RECEIPT.getCode().equals(row.getTypeEnum())) {
                continue;
            }
            // 品种须与退货行一致
            if (!Objects.equals(row.getItemId(), returnLine.getItemId())) {
                continue;
            }
            // 先记同品种任一进货行，批号精确匹配时再覆盖
            if (itemMatch == null) {
                itemMatch = row.getId();
            }
            // 批号完全一致则优先该进货行（同单多批场景）
            if (isNotEmpty(returnLine.getLotNumber())
                && returnLine.getLotNumber().trim().equals(trimToEmpty(row.getLotNumber()))) {
                exactMatch = row.getId();
                break;
            }
        }
        // 有批号精确命中则用之
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
            // 非 3503 结构或无 purcinfo 节点
            if (purcinfo == null) {
                return null;
            }
            String bchno = purcinfo.getString("fixmedins_bchno");
            // 兼容历史 camelCase 落库
            if (isEmpty(bchno)) {
                bchno = purcinfo.getString("fixmedinsBchno");
            }
            return bchno;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
