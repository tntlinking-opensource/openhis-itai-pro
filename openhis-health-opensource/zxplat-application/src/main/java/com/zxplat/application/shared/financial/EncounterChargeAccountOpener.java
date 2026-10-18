/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.administration.account.command.SaveAccountByRegisterCommand;
import com.zxplat.domain.administration.account.entity.Account;
import com.zxplat.domain.administration.account.service.AccountDomainService;
import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.AccountTypeCode;
import com.zxplat.domain.share.enums.ContractCategory;
import com.zxplat.domain.share.utils.InsuranceContractCategorySupport;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 门诊挂号 / 住院登记共用：幂等开立就诊挂账账户。
 *
 * <p>口径：人人必有默认 {@link AccountTypeCode#PERSONAL_CASH_ACCOUNT}（{@code contract_no}=自费合同，供预交金余额）；
 * 省/市医保或工伤等并行费用性质另开 {@link AccountTypeCode#MEDICAL_INSURANCE}
 * （{@code contract_no}=挂号/入院所选合同，住院费用挂账真源）。已存在账户不改写 {@code contract_no}（历史错挂兼容）。</p>
 *
 * <p>禁止依赖 outpatient/inpatient feature DTO；仅编排 Account 写侧与自费合同解析。</p>
 */
@Component
@RequiredArgsConstructor
public class EncounterChargeAccountOpener {

    /** 账户写侧：幂等开立。 */
    private final AccountDomainService accountDomainService;
    /** 账户读侧：开立后回读挂账主键。 */
    private final AccountQueryService accountQueryService;
    /** 租户启用态自费费用性质（写入个人现金户 contract_no）。 */
    private final OutpatientSelfPayFeeNatureAccountResolver outpatientSelfPayFeeNatureAccountResolver;

    /**
     * 幂等确保就诊挂账账户齐备。
     *
     * @param patientId                 患者主键
     * @param encounterId               就诊主键
     * @param parallelFeeNatureContractBusNo 并行费用性质合同 bus_no（医保/工伤等）；仅现金户时可为 null
     * @param openMedicalInsuranceAccount    true=另开医保账户；false=仅个人现金
     * @param operatorId                操作人；可空
     */
    public void ensureEncounterChargeAccounts(
        Long patientId,
        Long encounterId,
        String parallelFeeNatureContractBusNo,
        boolean openMedicalInsuranceAccount,
        Long operatorId
    ) {
        this.ensureEncounterChargeAccounts(
            patientId, encounterId, parallelFeeNatureContractBusNo, openMedicalInsuranceAccount, null, operatorId);
    }

    /**
     * 幂等确保就诊挂账账户齐备（医保户可带险种码）。
     *
     * @param patientId                      患者主键
     * @param encounterId                    就诊主键
     * @param parallelFeeNatureContractBusNo 并行费用性质合同 bus_no（医保/工伤等）；仅现金户时可为 null
     * @param openMedicalInsuranceAccount    true=另开医保账户；false=仅个人现金
     * @param insutype                       险种码（310/390）；仅写入医保户；可空
     * @param operatorId                     操作人；可空
     */
    public void ensureEncounterChargeAccounts(
        Long patientId,
        Long encounterId,
        String parallelFeeNatureContractBusNo,
        boolean openMedicalInsuranceAccount,
        String insutype,
        Long operatorId
    ) {
        // Gate：患者/就诊缺失则无法开户
        if (patientId == null || encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ACCOUNT_INVALID_ARGUMENT, "开立就诊账户时患者或就诊不能为空");
        }
        // Cmd：个人现金户恒开；新开时 contract_no 固定自费（已存在则幂等跳过，不改历史）
        String selfPayBusNo = outpatientSelfPayFeeNatureAccountResolver.resolveSelfPayContractBusNo();
        this.openAccountIfAbsent(
            patientId,
            encounterId,
            AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode(),
            selfPayBusNo,
            null,
            operatorId);
        // Cmd：医保/工伤挂号另开 MEDICAL_INSURANCE，contract_no=所选费用性质
        if (!openMedicalInsuranceAccount) {
            return;
        }
        String insuranceBusNo = trimToNull(parallelFeeNatureContractBusNo);
        if (insuranceBusNo == null) {
            throw new BusinessException(
                ErrorCodeConstants.ACCOUNT_INVALID_ARGUMENT,
                "开立医保账户时费用性质合同号不能为空");
        }
        this.openAccountIfAbsent(
            patientId,
            encounterId,
            AccountTypeCode.MEDICAL_INSURANCE.getCode(),
            insuranceBusNo,
            trimToNull(insutype),
            operatorId);
    }

    /**
     * 解析挂号费挂账账户：医保支付挂号费走医保户，否则走个人现金户。
     *
     * @param encounterId      就诊主键
     * @param ybRegisterFee    true=挂号费走医保账户
     * @return 挂账账户；缺失时抛业务异常
     */
    public Account requireRegistrationFeeAccount(Long encounterId, boolean ybRegisterFee) {
        // Pre：勾选医保付挂号费 → MEDICAL_INSURANCE；否则 PERSONAL_CASH
        if (ybRegisterFee) {
            Account insurance = accountQueryService.getYbAccount(encounterId);
            if (insurance == null || insurance.getId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.ACCOUNT_NOT_FOUND,
                    "未开立医保账户，无法医保支付挂号费");
            }
            return insurance;
        }
        Account personalCash = accountQueryService.getSelfAccount(encounterId);
        if (personalCash == null || personalCash.getId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ACCOUNT_NOT_FOUND,
                "未开立个人现金账户，无法挂挂号费");
        }
        return personalCash;
    }

    /**
     * 按医嘱所选费用性质合同解析挂账账户（账单真源为 account_id）。
     *
     * <p>优先匹配账户 {@code contract_no} 与合同 bus_no；再按医保/自费类型回落；
     * 双账户时默认医保户，单户时个人现金户。</p>
     *
     * @param encounterId           就诊主键
     * @param contractBusNo         所选合同 bus_no；可空则取默认挂账户
     * @param preferInsuranceAccount 合同为空时是否优先医保户
     * @return 挂账账户主键
     */
    public Long resolveChargeAccountId(
        Long encounterId,
        String contractBusNo,
        boolean preferInsuranceAccount
    ) {
        // Gate：就诊缺失则无法解析挂账户
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ACCOUNT_INVALID_ARGUMENT, "解析挂账账户时就诊不能为空");
        }
        String busNo = trimToNull(contractBusNo);
        Account insurance = accountQueryService.getYbAccount(encounterId);
        Account personalCash = accountQueryService.getSelfAccount(encounterId);
        // Pre：合同号命中账户 contract_no 时精确挂账（医嘱显式选费用性质）
        if (busNo != null) {
            if (insurance != null && busNo.equals(trimToNull(insurance.getContractNo()))) {
                return insurance.getId();
            }
            if (personalCash != null && busNo.equals(trimToNull(personalCash.getContractNo()))) {
                return personalCash.getId();
            }
        }
        // Pre：合同未命中时：preferInsurance=true 优先医保户（饮片/西医默认）；false 优先个人现金（代煎/转自费目标户）
        if (preferInsuranceAccount && insurance != null && insurance.getId() != null) {
            return insurance.getId();
        }
        if (personalCash != null && personalCash.getId() != null) {
            return personalCash.getId();
        }
        // 仅有医保户、无个人现金时的兜底（极端缺户数据）
        if (insurance != null && insurance.getId() != null) {
            return insurance.getId();
        }
        throw new BusinessException(ErrorCodeConstants.ACCOUNT_NOT_FOUND, "就诊未解析到可用挂账账户");
    }

    /**
     * 按合同医保标志与分类判断是否需并行开立医保账户（省/市医保、职工/工伤等）。
     *
     * @param ybFlag       合同 yb_flag；可空
     * @param categoryEnum 合同 category_enum；可空
     * @return 需开 MEDICAL_INSURANCE 时 true
     */
    public boolean needsParallelMedicalInsuranceAccount(Boolean ybFlag, String categoryEnum) {
        // 省/市医保等标准分类
        if (InsuranceContractCategorySupport.isInsuranceContract(ybFlag, categoryEnum)) {
            return true;
        }
        String category = trimToNull(categoryEnum);
        // 无分类则仅开个人现金户
        if (category == null) {
            return false;
        }
        // 工伤/职工等历史码：前端兼认 WORKER_INSURANCE 与 "5"
        if (ContractCategory.WORKER_INSURANCE.getCode().equals(category)) {
            return true;
        }
        return "5".equals(category);
    }

    /**
     * 同就诊同类型账户不存在时开立一行。
     *
     * @param patientId   患者主键
     * @param encounterId 就诊主键
     * @param typeCode    账户类型码
     * @param contractNo  费用性质 bus_no
     * @param insutype    险种码；仅医保户有意义；可空
     * @param operatorId  操作人；可空
     */
    private void openAccountIfAbsent(
        Long patientId,
        Long encounterId,
        String typeCode,
        String contractNo,
        String insutype,
        Long operatorId
    ) {
        SaveAccountByRegisterCommand cmd = new SaveAccountByRegisterCommand();
        cmd.setPatientId(patientId);
        cmd.setEncounterId(encounterId);
        cmd.setTypeCode(typeCode);
        cmd.setDefaultFlag(Boolean.TRUE);
        cmd.setContractNo(contractNo);
        cmd.setInsutype(insutype);
        cmd.setOperatorId(operatorId);
        // Cmd：领域幂等——已存在同类型则跳过，不覆盖历史 contract_no；空 insutype 可补写
        accountDomainService.saveAccountByRegister(cmd);
    }
}
