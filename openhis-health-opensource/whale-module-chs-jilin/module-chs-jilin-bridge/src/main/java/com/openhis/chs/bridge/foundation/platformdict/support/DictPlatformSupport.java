/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.foundation.platformdict.support;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.catalog.access.ChsContractPropertiesAccess;
import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.chs.domain.share.constants.PlatformDictConstants;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.session.ChsSignCredentialResolver;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 1901 平台字典出站公共上下文。
 * <p>职责：契约解析、调用上下文构建及字典查询参数归一化。</p>
 */
@Component
@RequiredArgsConstructor
public class DictPlatformSupport {

    private static final String DEFAULT_QUERY_ADMDVS = "100000";

    private final ChsInsuranceContractReader contractReader;
    private final ChsContractPropertiesAccess contractPropertiesAccess;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;

    /** 解析契约快照与业务编号。 */
    public DictContractContext resolveContract(String contractBusNo, Long contractId) {
        ChsContractSnapshot contract = contractReader.requireContract(contractBusNo, contractId);
        String busNo = contractReader.requireContractBusNo(contract);
        return new DictContractContext(contract, busNo);
    }

    /** 构建含操作员与签到凭证的网关调用上下文。 */
    public InvokeContext buildInvokeContext(Long operatorId) {
        InvokeContext partial = InvokeContext.builder().operatorId(String.valueOf(operatorId)).build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .build();
    }

    /** 解析字典类型，空值时回退默认区划字典类型。 */
    public String resolveDictType(String dictType) {
        if (isNotEmpty(dictType)) {
            return dictType.trim();
        }
        return PlatformDictConstants.DEFAULT_ADMDVS_DICT_TYPE;
    }

    /** 解析查询区划，优先入参，其次契约配置，最后回退全国区划。 */
    public String resolveQueryAdmdvs(String queryAdmdvs, String contractBusNo) {
        if (isNotEmpty(queryAdmdvs)) {
            return queryAdmdvs.trim();
        }
        String admVs = contractPropertiesAccess.findAdmVs(contractBusNo).orElse(null);
        if (isNotEmpty(admVs)) {
            return admVs.trim();
        }
        return DEFAULT_QUERY_ADMDVS;
    }

    /** 解析分页大小，上限 1000。 */
    public int resolvePageSize(Integer pageSize) {
        if (pageSize != null && pageSize > 0) {
            return Math.min(pageSize, 1000);
        }
        return PlatformDictConstants.DEFAULT_PAGE_SIZE;
    }

    /** 归一化父节点编码（空值转空串并 trim）。 */
    public static String normalizeParent(String parentValue) {
        return StringUtils.defaultString(parentValue, "").trim();
    }

    public record DictContractContext(ChsContractSnapshot contract, String busNo) {
    }
}
