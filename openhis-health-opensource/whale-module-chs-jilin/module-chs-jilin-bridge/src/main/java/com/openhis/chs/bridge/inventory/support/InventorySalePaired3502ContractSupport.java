/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.support;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.bridge.catalog.ChsInsuranceContractReaderImpl;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.repository.YbInvUploadLookup;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.share.enums.ContractCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 销售配对 3502 出站契约解析（BR-CHS-INV-015）。
 *
 * <p><b>口径</b>：3505/3506 跟结算/支付平台（含工伤）；销售配对 3502(107/106)
 * 跟追溯码入库平台——市入市出、省入省出。无码或查不到入库归属时回落库房市优先。
 * 禁止因结算走工伤就把 3502 打到工伤平台。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventorySalePaired3502ContractSupport {

    private final YbInvUploadLookup uploadLookup;
    private final ChsInsuranceContractReaderImpl contractReader;

    /**
     * 解析销售配对 3502 应使用的出站契约。
     *
     * @param tenantId 租户
     * @param line3502 配对 3502 行（含 drugTracCodgs）
     * @return 非空医保机构契约（省/市等）；不会返回自费
     */
    public Contract resolveForSalePaired3502(Long tenantId, InvUploadLineCommand line3502) {
        List<String> codes = collectCodes(line3502);
        // Pre：有码 → 按入库成功 3502 归属契约（含软删 trace 历史）
        if (!codes.isEmpty() && tenantId != null) {
            String inboundBusNo = uploadLookup.findLatestInboundStockContractBusNo(tenantId, codes);
            if (isNotEmpty(inboundBusNo)) {
                Contract inbound = contractReader.requireContractEntity(inboundBusNo.trim(), null);
                // Gate：工伤不是库存进销存底账平台；误命中则回落市优先
                if (inbound != null && !isWorkInjuryContract(inbound)) {
                    return inbound;
                }
                log.warn(
                    "销售配对3502：码的入库契约不可用（空或工伤），回落市优先 inboundBusNo={} codeSample={}",
                    inboundBusNo,
                    codes.get(0));
            }
        }
        // Cmd：无码 / 无入库归属 / 工伤误命中 → 与库房默认一致（市优先）
        return contractReader.resolveOrDefault(null, null);
    }

    /**
     * 是否工伤费用性质（{@code WORKER_INSURANCE}）。
     *
     * @param contract 契约实体
     * @return 工伤 true
     */
    public static boolean isWorkInjuryContract(Contract contract) {
        if (contract == null) {
            return false;
        }
        String category = contract.getCategoryEnum();
        if (isEmpty(category)) {
            return false;
        }
        return ContractCategory.WORKER_INSURANCE.getCode().equals(category.trim());
    }

    /**
     * 收集行上追溯码（trim、去空）。
     *
     * @param line 3502 行；可空
     * @return 非空码列表
     */
    private static List<String> collectCodes(InvUploadLineCommand line) {
        List<String> out = new ArrayList<>();
        if (line == null || line.getDrugTracCodgs() == null) {
            return out;
        }
        for (String raw : line.getDrugTracCodgs()) {
            if (isEmpty(raw)) {
                continue;
            }
            String trimmed = raw.trim();
            if (isEmpty(trimmed)) {
                continue;
            }
            out.add(trimmed);
        }
        return out;
    }
}
