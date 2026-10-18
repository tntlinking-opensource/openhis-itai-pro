/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.catalog.access.ChsContractPropertiesAccess;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;
import com.zxplat.plugin.chs.domain.inventory.queries.QueryPlatformInvQuery;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.session.ChsSignCredentialResolver;
import com.zxplat.plugin.chs.gateway.support.ChsCurrentOperatorResolver;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 进销存出站公共上下文（契约解析、操作员、9001 签到凭证）。
 * <p>供 {@code Inv*Invoker} 与 {@link Inv35xxQueryInvoker} 复用，避免各 Invoker 重复装配契约与签到逻辑。</p>
 */
@Component
@RequiredArgsConstructor
public class InvPlatformSupport {

    private final ChsInsuranceContractReader contractReader;
    private final ChsContractPropertiesAccess contractPropertiesAccess;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;

    /** 解析契约快照、业务编号与定点机构编码。 */
    public InvContractContext resolveContract(String contractBusNo, Long contractId) {
        ChsContractSnapshot contract = contractReader.requireContract(contractBusNo, contractId);
        String busNo = contractReader.requireContractBusNo(contract);
        String fixmedinsCode = contractPropertiesAccess.findFixmedinsCode(busNo).orElse("").trim();
        return new InvContractContext(contract, busNo, fixmedinsCode);
    }

    /** 组装 gateway {@link InvokeContext}（含签到后的 operatorId/operatorName）；须当前登录。 */
    public InvokeContext buildInvokeContext() {
        return buildInvokeContext(null, null);
    }

    /**
     * 组装 gateway {@link InvokeContext}；异步路径传入调用线程快照操作人，避免 {@code requireOperatorId}。
     *
     * @param operatorId   已捕获操作人主键；空则回落当前登录
     * @param operatorName 操作人姓名；可空
     * @return 签到后的调用上下文
     */
    public InvokeContext buildInvokeContext(Long operatorId, String operatorName) {
        Long resolvedOperatorId = operatorId;
        // Gate：同步运维台无快照时仍要求登录
        if (resolvedOperatorId == null) {
            resolvedOperatorId = currentOperatorResolver.requireOperatorId("请先登录后再操作进销存");
        }
        InvokeContext.InvokeContextBuilder partialBuilder = InvokeContext.builder()
            .operatorId(String.valueOf(resolvedOperatorId));
        if (isNotEmpty(operatorName)) {
            partialBuilder.operatorName(operatorName.trim());
        }
        InvokeContext partial = partialBuilder.build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .build();
    }

    /** 校验 3508–3513 平台查询入参（3512/3513 与 3508–3511 规则不同）。 */
    public void assertPlatformQueryRequest(String infno, QueryPlatformInvQuery request) {
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "查询参数不能为空");
        }
        if (ChsInfNo.INVENTORY_INBOUND_TRACE_QUERY.equals(infno)
            || ChsInfNo.INVENTORY_SALE_TRACE_QUERY.equals(infno)) {
            boolean hasKey = isNotEmpty(request.getMedListCodg())
                || isNotEmpty(request.getMedinsListCodg())
                || isNotEmpty(request.getFixmedinsBchno())
                || isNotEmpty(request.getDrugTracCodg())
                || isNotEmpty(request.getCertno());
            if (!hasKey) {
                throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID,
                    "3512/3513 至少填写 med_list_codg、medins_list_codg、fixmedins_bchno、drug_trac_codg 或 certno 之一");
            }
            return;
        }
        if (isEmpty(request.getBegndate()) || isEmpty(request.getEnddate())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "begndate/enddate 不能为空");
        }
        if (isEmpty(request.getMedinsListCodg()) && isEmpty(request.getFixmedinsBchno())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID,
                "medins_list_codg 与 fixmedins_bchno 至少填写一项");
        }
    }

    /** 契约快照 + 业务编号 + 定点机构编码。 */
    public record InvContractContext(ChsContractSnapshot contract, String busNo, String fixmedinsCode) {
    }
}
