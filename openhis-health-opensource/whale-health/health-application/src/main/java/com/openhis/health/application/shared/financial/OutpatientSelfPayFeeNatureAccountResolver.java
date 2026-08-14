/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ChargeTypeCategory;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 门诊「自费」费用性质合同只读解析（不写 {@code Account}）。
 *
 * <p>供挂号开个人现金户 {@code contract_no}、门诊代煎 SR {@code contract_id} 冗余等场景；
 * <b>禁止</b>开立或改写就诊账户。收银费用性质展示跟账户 {@code contract_no}，本类不做展示覆盖。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientSelfPayFeeNatureAccountResolver {

    /** 费用性质主数据。 */
    private final ContractQueryService contractQueryService;

    /**
     * 解析租户启用态「自费」费用性质合同主键。
     *
     * @return 自费合同主键
     * @throws BusinessException 未维护自费合同时
     */
    public Long resolveSelfPayContractId() {
        return requireSelfPayContract().contractId;
    }

    /**
     * 解析租户启用态「自费」费用性质合同业务号。
     *
     * @return 自费合同 bus_no
     * @throws BusinessException 未维护自费合同时
     */
    public String resolveSelfPayContractBusNo() {
        return requireSelfPayContract().busNo;
    }

    /**
     * 从费用性质主数据解析启用态自费合同（分类码 {@link ChargeTypeCategory#SELF_PAY}）。
     *
     * @return 自费合同引用
     * @throws BusinessException 主数据未维护自费合同时
     */
    private SelfPayContractRef requireSelfPayContract() {
        String selfPayCategory = ChargeTypeCategory.SELF_PAY.getCode();
        ContractListItemQueryModel best = null;
        for (ContractListItemQueryModel row : contractQueryService.listAllContracts()) {
            // 跳过无主键行
            if (row == null || row.getId() == null) {
                continue;
            }
            // 仅启用态费用性质可挂账/写冗余
            if (!Boolean.TRUE.equals(row.getActiveFlag())) {
                continue;
            }
            String category = trimToNull(row.getCategoryEnum());
            // 分类须为自费
            if (category == null || !selfPayCategory.equals(category)) {
                continue;
            }
            String busNo = trimToNull(row.getBusNo());
            // bus_no 为空无法写入账户 contract_no
            if (busNo == null) {
                continue;
            }
            if (best == null) {
                best = row;
                continue;
            }
            // 展示序更小者优先；同序取主键更小，保证稳定
            Integer bestOrder = best.getDisplayOrder();
            Integer rowOrder = row.getDisplayOrder();
            int bestSort = 0;
            if (bestOrder != null) {
                bestSort = bestOrder.intValue();
            }
            int rowSort = 0;
            if (rowOrder != null) {
                rowSort = rowOrder.intValue();
            }
            if (rowSort < bestSort) {
                best = row;
                continue;
            }
            if (rowSort == bestSort && row.getId() < best.getId()) {
                best = row;
            }
        }
        if (best == null) {
            throw new BusinessException(
                ErrorCodeConstants.CONTRACT_NOT_FOUND, "未维护启用态自费费用性质合同，无法解析自费合同");
        }
        return new SelfPayContractRef(best.getId(), trimToNull(best.getBusNo()));
    }

    /**
     * 自费合同主键与业务号。
     */
    private static final class SelfPayContractRef {
        private final Long contractId;
        private final String busNo;

        /**
         * @param contractId 合同主键
         * @param busNo      合同业务号
         */
        private SelfPayContractRef(Long contractId, String busNo) {
            this.contractId = contractId;
            this.busNo = busNo;
        }
    }
}
