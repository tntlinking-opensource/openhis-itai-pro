/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.support;

import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ReturnableInboundLineDto;
import com.openhis.health.domain.custom.tracenumber.queries.model.InventoryItemTraceNumberQueryModel;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 退供应商 IF-WHS-RT-13：按「当前院内剩余码」预填追溯码（AS-INV-WHS-005 · BR-WHS-RT-008）。
 *
 * <p>有码批：源药库本库净量优先（且主档在院剩余 &gt; 0）；本库空则全院同品同批在院剩余。
 * 截取个数按 {@code min(可退余量, 源库可用量)} 与每码拆零比换算。无码批：清空、不校验。
 * 禁止回放进货单历史 {@code trace_no}。</p>
 */
@Component
@RequiredArgsConstructor
public class ReturnToSupplierRemainingTracePrefiller {

    /** 追溯读侧（仅本聚合 QueryService）。 */
    private final TraceNumberQueryService traceNumberQueryService;

    /**
     * 覆盖可退行上的追溯码：当前剩余码（本库优先 / 全院回落），按可退∩源库可用截取应退个数。
     *
     * @param lines             可退明细（Mapper 已映射；本方法会改写 {@code traceNo}）
     * @param sourceLocationId  退货源药库（= 进货目的库）
     */
    public void fillRemainingTraceNos(List<ReturnableInboundLineDto> lines, Long sourceLocationId) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (ReturnableInboundLineDto line : lines) {
            if (line == null) {
                continue;
            }
            // 默认清空：禁止保留进货行历史码串
            line.setTraceNo(null);
            if (sourceLocationId == null || line.getItemId() == null) {
                continue;
            }
            String itemTable = line.getItemTable();
            if (StringUtils.isEmpty(itemTable)) {
                itemTable = TableNameConstants.MED_MEDICATION_DEFINITION;
            }
            // 本期仅药品追溯；耗材等不预填
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable.trim())) {
                continue;
            }
            String lotNumber = line.getLotNumber();
            if (StringUtils.isEmpty(lotNumber)) {
                continue;
            }
            // 无码批：不校验、不预填
            if (!this.traceNumberQueryService.existsMasterByItemLot(itemTable, line.getItemId(), lotNumber)) {
                continue;
            }
            List<InventoryItemTraceNumberQueryModel> remaining =
                this.traceNumberQueryService.listInStockTraceNumbersByInventoryItemKey(
                    sourceLocationId,
                    itemTable,
                    line.getItemId(),
                    lotNumber.trim()
                );
            // 本库无在院剩余净量：回落全院在院且剩余 > 0（不看 loc）
            if (remaining == null || remaining.isEmpty()) {
                remaining = this.traceNumberQueryService.listInHospitalTraceNumbersByItemLot(
                    itemTable,
                    line.getItemId(),
                    lotNumber.trim()
                );
            }
            if (remaining == null || remaining.isEmpty()) {
                continue;
            }
            int takeCount = this.resolveTakeCount(line, remaining);
            List<String> codes = new ArrayList<>();
            int limit = Math.min(takeCount, remaining.size());
            for (int i = 0; i < limit; i++) {
                InventoryItemTraceNumberQueryModel row = remaining.get(i);
                if (row == null || StringUtils.isEmpty(row.getTraceNo())) {
                    continue;
                }
                codes.add(row.getTraceNo().trim());
            }
            if (codes.isEmpty()) {
                continue;
            }
            line.setTraceNo(String.join(GeneralSharedConstants.COMMA, codes));
        }
    }

    /**
     * 按可退∩源库可用拆零量与主档每码拆零比计算应退码个数；无法换算时取剩余码全量供用户裁剪。
     *
     * <p>可退余量按进货占用口径可能仍大于源库账面；截码须同时受源库可用约束，避免多带已调出/已发药码。</p>
     *
     * @param line      可退行（含可退余量、源库可用量）
     * @param remaining 已选出的剩余码列表（首码可带 {@code traceUnitRatio}）
     * @return 应截取个数
     */
    private int resolveTakeCount(
        ReturnableInboundLineDto line,
        List<InventoryItemTraceNumberQueryModel> remaining
    ) {
        BigDecimal splitCap = this.resolveSplitCapForTake(line);
        if (splitCap == null || splitCap.compareTo(BigDecimal.ZERO) <= 0) {
            return remaining.size();
        }
        InventoryItemTraceNumberQueryModel first = remaining.get(0);
        BigDecimal perCodeSplit = null;
        if (first != null) {
            perCodeSplit = first.getTraceUnitRatio();
        }
        if (perCodeSplit == null || perCodeSplit.compareTo(BigDecimal.ZERO) <= 0) {
            return remaining.size();
        }
        // 应退码数 = ceil(截码拆零上限 / 每码拆零)，至少 1
        int n = splitCap.divide(perCodeSplit, 6, RoundingMode.HALF_UP)
            .setScale(0, RoundingMode.CEILING)
            .intValue();
        if (n < 1) {
            return 1;
        }
        return n;
    }

    /**
     * 截码拆零上限：可退余量与源库可用量取较小正值；仅一侧有值则用该侧；皆空则 null（调用方取剩余全量）。
     *
     * @param line 可退行
     * @return 拆零上限；无法约束时 null
     */
    private BigDecimal resolveSplitCapForTake(ReturnableInboundLineDto line) {
        BigDecimal returnable = line.getReturnableQuantity();
        BigDecimal available = line.getSourceAvailableQuantity();
        boolean returnablePositive = returnable != null && returnable.compareTo(BigDecimal.ZERO) > 0;
        boolean availablePositive = available != null && available.compareTo(BigDecimal.ZERO) > 0;
        // 两侧皆有：取较小，防止进货可退口径大于源库账面时多带码
        if (returnablePositive && availablePositive) {
            if (returnable.compareTo(available) <= 0) {
                return returnable;
            }
            return available;
        }
        if (returnablePositive) {
            return returnable;
        }
        if (availablePositive) {
            return available;
        }
        return null;
    }
}
