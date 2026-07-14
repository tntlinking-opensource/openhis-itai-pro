/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.support;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.bridge.inventory.assembler.ChsTraceCodeExtractAssembler;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import com.openhis.chs.domain.inventory.support.InventoryTracePolicySupport;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 进销存上报行追溯码策略与上报前校验（3501–3506 共用）。
 *
 * <p>追溯码不再单独暴露 SPI；由 {@code *UploadContextLoader} 组行后，
 * {@link com.openhis.chs.bridge.inventory.workflow.InventoryThirdPartyUploadOrchestrator} 出站前调用本类校验。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryUploadTraceLineSupport {

    private final ChsInventoryProperties inventoryProperties;

    /**
     * 为供应单行填充 {@code tracePolicy} 与 {@code drugTracCodgs}（药品场景）。
     *
     * @param medication 药品定义
     * @param unitCode   业务单位 code；空时回落默认就诊单位
     * @param traceNo    行上逗号分隔追溯码
     * @param cmd        待填充的上报行命令
     */
    public void applyMedicationSupplyLineTrace(
        MedicationDefinition medication,
        String unitCode,
        String traceNo,
        InvUploadLineCommand cmd
    ) {
        if (medication == null || cmd == null) {
            return;
        }
        if (isEmpty(cmd.getMedListCodg())) {
            cmd.setTracePolicy(ChsInventoryConstants.TRACE_POLICY_NO_YB_NO);
            cmd.setDrugTracCodgs(ChsTraceCodeExtractAssembler.splitTraceNoCommaString(traceNo));
            return;
        }
        String resolvedUnitCode = unitCode;
        if (isEmpty(resolvedUnitCode)) {
            resolvedUnitCode = medication.getDefEncounterUnitCode();
        }
        cmd.setTracePolicy(InventoryTracePolicySupport.resolve(
            medication.getYbNo(),
            medication.getCategoryCode(),
            medication.getDefEncounterUnitCode(),
            resolvedUnitCode,
            inventoryProperties));
        cmd.setDrugTracCodgs(ChsTraceCodeExtractAssembler.splitTraceNoCommaString(traceNo));
    }

    /**
     * 上报前追溯校验：应码未扫、销售码数与数量不一致等。
     *
     * @param line                 已组好的上报行
     * @param saleQuantityCheck    是否校验 3505/3506 销售数量与码数一致
     * @return 失败原因；{@code null} 表示可继续调用平台
     */
    public String resolveUploadTraceFailureMessage(InvUploadLineCommand line, boolean saleQuantityCheck) {
        if (line == null) {
            return null;
        }
        if (!ChsInventoryConstants.TRACE_POLICY_TRACE_REQUIRED.equals(line.getTracePolicy())) {
            return null;
        }
        if (inventoryProperties.isBlockSettleWithoutTrace() && !hasNonBlankTraceCode(line.getDrugTracCodgs())) {
            return "应扫码品种未录入追溯码，进销存上报已阻断（block-settle-without-trace）";
        }
        if (saleQuantityCheck
            && inventoryProperties.isEnforceTraceCountEqualsQty()
            && !traceCountMatchesSaleQuantity(line)) {
            return "trace code count mismatch";
        }
        return null;
    }

    /**
     * 判断追溯码列表中是否包含非空条目。
     *
     * @param traceCodes 追溯码列表
     * @return 存在非空追溯码时返回 {@code true}
     */
    private static boolean hasNonBlankTraceCode(List<String> traceCodes) {
        if (traceCodes == null || traceCodes.isEmpty()) {
            return false;
        }
        for (String code : traceCodes) {
            if (isNotEmpty(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验销售/退货数量与追溯码条数是否一致（3505/3506）。
     *
     * @param line 上报行
     * @return 一致时返回 {@code true}
     */
    private static boolean traceCountMatchesSaleQuantity(InvUploadLineCommand line) {
        BigDecimal saleQuantity = line.getSelRetnCnt();
        if (saleQuantity == null) {
            return false;
        }
        int expected = saleQuantity.intValue();
        int actual = 0;
        if (line.getDrugTracCodgs() != null) {
            for (String code : line.getDrugTracCodgs()) {
                if (isNotEmpty(code)) {
                    actual++;
                }
            }
        }
        return actual == expected;
    }
}
