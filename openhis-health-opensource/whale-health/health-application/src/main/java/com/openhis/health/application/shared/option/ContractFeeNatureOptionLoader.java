/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.option;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.share.enums.AccountTypeCode;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 费用性质下拉：按就诊已开立账户装配（展示名为账户 {@code contract_no} 对应合同名）。
 *
 * <p>双账户时医保户排首位（默认选中）；单自费户时仅一项。{@code value} 仍为合同主键字符串
 * （医嘱行 {@code contract_id} 冗余落库），挂账真源由应用层按合同解析 {@code account_id}。</p>
 */
@Component
@RequiredArgsConstructor
public class ContractFeeNatureOptionLoader {

    /** 就诊账户读侧。 */
    private final AccountQueryService accountQueryService;
    /** 合同读侧：bus_no → 合同名/主键。 */
    private final ContractQueryService contractQueryService;

    /**
     * 按就诊已开立账户加载费用性质下拉（医保户优先，其次个人现金户）。
     *
     * @param patientId   患者标识（与挂号账户门控一致；本方法不落库）
     * @param encounterId 就诊标识
     * @return 非 {@code null}；无有效账户/合同时为空列表
     */
    public List<OptionDto> loadOptionsForEncounter(Long patientId, Long encounterId) {
        if (encounterId == null) {
            return Collections.emptyList();
        }
        // Pre：仅装配默认医保户与个人现金户（转自费/转医保双账户口径）
        List<Account> chargeAccounts = new ArrayList<>(2);
        Account insurance = accountQueryService.getYbAccount(encounterId);
        if (insurance != null) {
            chargeAccounts.add(insurance);
        }
        Account personalCash = accountQueryService.getSelfAccount(encounterId);
        // Pre：默认标记未命中时用挂号个人现金探测（与 patientId 门控一致）
        if (personalCash == null && patientId != null) {
            personalCash = accountQueryService.getRegistrationAccount(patientId, encounterId);
        }
        if (personalCash != null) {
            chargeAccounts.add(personalCash);
        }
        // 兼容：读侧未命中默认标记时回落就诊有效账户列表
        if (chargeAccounts.isEmpty()) {
            List<Account> all = accountQueryService.getAccountListByEncounter(encounterId);
            if (all != null) {
                for (Account row : all) {
                    if (row == null || row.getId() == null) {
                        continue;
                    }
                    String typeCode = trimToNull(row.getTypeCode());
                    if (AccountTypeCode.MEDICAL_INSURANCE.getCode().equals(typeCode)
                        || AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode().equals(typeCode)) {
                        chargeAccounts.add(row);
                    }
                }
            }
        }
        if (chargeAccounts.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> busNos = new LinkedHashSet<>();
        for (Account account : chargeAccounts) {
            if (account == null) {
                continue;
            }
            String busNo = trimToNull(account.getContractNo());
            if (busNo != null) {
                busNos.add(busNo);
            }
        }
        if (busNos.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Contract> contractByBusNo = new LinkedHashMap<>();
        for (Contract contract : contractQueryService.findAllContractEntitiesByBusNoIn(busNos)) {
            if (contract == null || trimToNull(contract.getBusNo()) == null) {
                continue;
            }
            contractByBusNo.put(contract.getBusNo().trim(), contract);
        }
        List<OptionDto> options = new ArrayList<>();
        long sort = 1L;
        // Asm：按账户顺序输出；同一合同只出一次（医保户优先）
        LinkedHashMap<Long, OptionDto> byContractId = new LinkedHashMap<>();
        for (Account account : chargeAccounts) {
            String busNo = trimToNull(account.getContractNo());
            if (busNo == null) {
                continue;
            }
            Contract contract = contractByBusNo.get(busNo);
            if (contract == null || contract.getId() == null) {
                continue;
            }
            if (byContractId.containsKey(contract.getId())) {
                continue;
            }
            OptionDto option = new OptionDto();
            option.setSort(sort);
            sort++;
            option.setValue(String.valueOf(contract.getId()));
            String label = trimToNull(contract.getName());
            if (label == null) {
                label = busNo;
            }
            option.setLabel(label);
            option.setBusNo(busNo);
            byContractId.put(contract.getId(), option);
            options.add(option);
        }
        return options;
    }
}
