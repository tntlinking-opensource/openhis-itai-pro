/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.common.assembler.option;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.enums.ChargeTypeCategory;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 费用性质（合同）下拉：按就诊挂号账户所绑合同分类收窄可选范围，供医嘱 init 复用。
 *
 * <p>规则：始终可选「自费」分类；若挂号非自费，另可选与挂号合同同 {@code category_enum} 的合同。
 * {@code value} 为合同主键字符串（落库 {@code contract_id}），{@code label} 为合同名称。</p>
 */
@Component
@RequiredArgsConstructor
public class ContractFeeNatureOptionLoader {

    /** 就诊账户读侧：解析挂号时写入的 {@code contract_no}。 */
    private final AccountQueryService accountQueryService;
    /** 合同读侧：租户费用性质主数据。 */
    private final ContractQueryService contractQueryService;

    /**
     * 按就诊挂号费用性质加载医嘱可选下拉（自费 + 挂号同分类）。
     *
     * @param patientId   患者标识（与挂号账户门控一致）
     * @param encounterId 就诊标识
     * @return 非 {@code null}；无有效合同时为空列表
     */
    public List<OptionDto> loadOptionsForEncounter(Long patientId, Long encounterId) {
        String registrationCategoryCode = resolveRegistrationCategoryCode(patientId, encounterId);
        String selfPayCategoryCode = ChargeTypeCategory.SELF_PAY.getCode();
        List<ContractListItemQueryModel> selectableRows = new ArrayList<>();
        // 遍历租户费用性质主数据，仅保留启用态且分类命中「自费或挂号分类」的行。
        for (ContractListItemQueryModel row : contractQueryService.listAllContracts()) {
            if (row == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(row.getActiveFlag())) {
                continue;
            }
            String categoryCode = trimToNull(row.getCategoryEnum());
            if (categoryCode == null) {
                continue;
            }
            if (selfPayCategoryCode.equals(categoryCode)) {
                selectableRows.add(row);
                continue;
            }
            if (registrationCategoryCode != null && registrationCategoryCode.equals(categoryCode)) {
                selectableRows.add(row);
            }
        }
        return OptionDtoAssembler.sortBySortAsc(mapContractRowsToOptions(selectableRows));
    }

    /**
     * 解析就诊挂号账户对应费用性质的分类码（{@link ChargeTypeCategory#getCode()}）。
     *
     * @param patientId   患者标识
     * @param encounterId 就诊标识
     * @return 挂号合同分类码；账户或合同缺失时回落为自费分类码
     */
    private String resolveRegistrationCategoryCode(Long patientId, Long encounterId) {
        String selfPayCategoryCode = ChargeTypeCategory.SELF_PAY.getCode();
        if (patientId == null || encounterId == null) {
            return selfPayCategoryCode;
        }
        Account registrationAccount = accountQueryService.getRegistrationAccount(patientId, encounterId);
        if (registrationAccount == null) {
            return selfPayCategoryCode;
        }
        String contractBusNo = trimToNull(registrationAccount.getContractNo());
        if (contractBusNo == null) {
            return selfPayCategoryCode;
        }
        Optional<Contract> registrationContract = contractQueryService.findOptionalContractEntityByBusNo(contractBusNo);
        if (registrationContract.isEmpty()) {
            return selfPayCategoryCode;
        }
        String categoryCode = trimToNull(registrationContract.get().getCategoryEnum());
        if (categoryCode == null) {
            return selfPayCategoryCode;
        }
        return categoryCode;
    }

    /**
     * 将合同简项映射为 {@link OptionDto}（B.10.5 统一下拉契约）。
     *
     * @param rows 合同查询行，允许为 {@code null} 或空
     * @return 非 {@code null}
     */
    private static List<OptionDto> mapContractRowsToOptions(List<ContractListItemQueryModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<OptionDto> options = new ArrayList<>();
        for (ContractListItemQueryModel row : rows) {
            if (row == null) {
                continue;
            }
            Long id = row.getId();
            if (id == null) {
                continue;
            }
            OptionDto option = new OptionDto();
            Integer displayOrder = row.getDisplayOrder();
            if (displayOrder != null) {
                option.setSort(displayOrder.longValue());
            } else {
                // 主数据未填展示序时，用主键保证前端稳定排序。
                option.setSort(id);
            }
            option.setValue(String.valueOf(id));
            option.setLabel(row.getName());
            option.setBusNo(row.getBusNo());
            options.add(option);
        }
        return options;
    }
}

