/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.account.service;

import com.openhis.health.domain.share.enums.AccountTypeCode;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 账户读侧查询服务。
 *
 * <p>职责：按就诊维度解析自费/医保等账户主键或实体，供结算、开票与费用归属使用。</p>
 *
 * <p>边界：只读；默认账户以 {@code defaultFlag=true} 与类型码联合定位；解析默认账户时仅命中
 * {@link PublicationStatus#ACTIVE} 主状态（禁止历史数字串 {@code "1"}）。</p>
 * <p>聚合边界：仅本聚合 {@link AccountRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class AccountQueryService {
    private final AccountRepository repository;

    /**
     * 解析就诊下默认自费挂账账户主键（与挂号开户、医嘱费用草稿同一 typeCode）。
     *
     * @param encounterId 就诊标识
     * @return 默认 {@link AccountTypeCode#PERSONAL_CASH_ACCOUNT} 主键；未开立时 {@code null}
     */
    public Long getSelfPayAccount(Long encounterId) {
        Account row = this.getSelfAccount(encounterId);
        if (row == null) {
            return null;
        }
        return row.getId();
    }

    /**
     * 解析就诊下默认医保账户主键。
     *
     * @param encounterId 就诊标识
     * @return 账户主键；未配置默认医保账户时返回 {@code null}
     */
    public Long getMedicalInsuranceAccount(Long encounterId) {
        Account row = this.getYbAccount(encounterId);
        if (row == null) {
            return null;
        }
        return row.getId();
    }

    /**
     * 按就诊与账户类型加载默认且有效的账户实体。
     *
     * @param encounterId 就诊标识
     * @param typeCode      账户类型编码（如自费、医保）
     * @return 账户实体；无默认有效行时返回 {@code null}
     */
    public Account getAccountByEncounterFlag(Long encounterId, String typeCode) {
        return repository
            .findFirstByEncounterIdAndTypeCodeAndDefaultFlagTrueAndStatusEnum(
                encounterId, typeCode, PublicationStatus.ACTIVE.getCode())
            .orElse(null);
    }

    /**
     * 按患者、就诊解析已开立的默认自费账户（门诊挂号预结算/缴费幂等探测与 chargeItem.accountId 同源）。
     *
     * @param patientId   患者标识
     * @param encounterId 就诊标识
     * @return 已存在的默认 {@link AccountTypeCode#PERSONAL_CASH_ACCOUNT}；未开立时 {@code null}
     */
    public Account getRegistrationAccount(Long patientId, Long encounterId) {
        return repository
            .findFirstByPatientIdAndEncounterIdAndTypeCode(patientId, encounterId, AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode())
            .filter(this::isEffectiveActiveAccount)
            .orElse(null);
    }

    /**
     * 加载就诊默认医保账户实体（{@link AccountTypeCode#MEDICAL_INSURANCE}）。
     *
     * @param encounterId 就诊标识
     * @return 医保默认账户；不存在时返回 {@code null}
     */
    public Account getYbAccount(Long encounterId) {
        return getAccountByEncounterFlag(encounterId, AccountTypeCode.MEDICAL_INSURANCE.getCode());
    }

    /**
     * 加载就诊默认个人现金账户实体（{@link AccountTypeCode#PERSONAL_CASH_ACCOUNT}）。
     *
     * @param encounterId 就诊标识
     * @return 个人现金默认账户；不存在时返回 {@code null}
     */
    public Account getSelfAccount(Long encounterId) {
        return getAccountByEncounterFlag(encounterId, AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode());
    }

    /**
     * 判断就诊是否同时开立默认自费与默认医保账户（医保挂号双账户场景，可互转记账）。
     *
     * <p>纯自费挂号仅存在 {@link AccountTypeCode#PERSONAL_CASH_ACCOUNT} 时返回 {@code false}，
     * 门诊收费 IF-OPC-04/05 须拒绝切换。</p>
     *
     * @param encounterId 就诊标识
     * @return 自费与医保默认账户均存在且有效时为 {@code true}
     */
    public boolean hasDualChargeAccounts(Long encounterId) {
        if (encounterId == null) {
            return false;
        }
        Long selfPayAccountId = this.getSelfPayAccount(encounterId);
        if (selfPayAccountId == null) {
            return false;
        }
        Long medicalInsuranceAccountId = this.getMedicalInsuranceAccount(encounterId);
        return medicalInsuranceAccountId != null;
    }

    /**
     * 列出就诊关联的有效账户行（含非默认）。
     *
     * @param encounterId 就诊标识
     * @return 有效账户列表，可能为空列表
     */
    public List<Account> getAccountListByEncounter(Long encounterId) {
        return repository.findAllByEncounterIdAndStatusEnum(encounterId, PublicationStatus.ACTIVE.getCode());
    }

    /**
     * 学生场景下解析默认自费账户主键（与 {@link #getSelfPayAccount(Long)} 语义一致，兼容历史命名）。
     *
     * @param encounterId 就诊标识
     * @return 自费账户主键；无则 {@code null}
     */
    public Long getStudentSelfAccount(Long encounterId) {
        return getSelfPayAccount(encounterId);
    }

    /**
     * 学生场景下解析默认医保账户主键（与 {@link #getMedicalInsuranceAccount(Long)} 语义一致，兼容历史命名）。
     *
     * @param encounterId 就诊标识
     * @return 医保账户主键；无则 {@code null}
     */
    public Long getStudentYbAccount(Long encounterId) {
        return getMedicalInsuranceAccount(encounterId);
    }

    /**
     * 判断账户是否为自费类型（按持久化 {@code typeCode} 判定）。
     *
     * @param accountId 账户主键
     * @return 记录存在且类型为自费时 {@code true}；不存在或非自费时 {@code false}
     */
    public boolean isSelfPay(Long accountId) {
        Account row = repository.findById(accountId).orElse(null);
        if (row == null) {
            return false;
        }
        return AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode().equals(row.getTypeCode());
    }

    /**
     * 按住院就诊主键批量解析个人现金账户（预交金在院队列，AS-IP-FIN-002 IF-IP-PREP-01）。
     *
     * <p>同一就诊仅保留一条有效 {@link AccountTypeCode#PERSONAL_CASH_ACCOUNT}；未开立不含键。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊主键 → 账户
     */
    public Map<Long, Account> mapRegistrationAccountsByEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Account> list = repository.findByEncounterIdInAndTypeCode(
            unique, AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode());
        Map<Long, Account> out = new HashMap<>(list.size() * 2);
        for (Account row : list) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            if (!this.isEffectiveActiveAccount(row)) {
                continue;
            }
            if (!out.containsKey(row.getEncounterId())) {
                out.put(row.getEncounterId(), row);
            }
        }
        return out;
    }

    /**
     * 按账户主键批量加载实体（收费明细解析费用性质等）。
     *
     * @param accountIds 账户主键集合
     * @return 主键 → 账户；未命中不包含键
     */
    public Map<Long, Account> mapAccountsByIds(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : accountIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Account> list = repository.findAllById(unique);
        Map<Long, Account> out = new HashMap<>(list.size() * 2);
        for (Account row : list) {
            if (row != null && row.getId() != null) {
                out.put(row.getId(), row);
            }
        }
        return out;
    }

    /**
     * 判断账户主状态是否为有效（仅接受 {@link PublicationStatus#ACTIVE} 的 code）。
     *
     * @param row 账户行
     * @return 有效时 true
     */
    private boolean isEffectiveActiveAccount(Account row) {
        if (row == null) {
            return false;
        }
        return PublicationStatus.ACTIVE.getCode().equals(row.getStatusEnum());
    }
}
