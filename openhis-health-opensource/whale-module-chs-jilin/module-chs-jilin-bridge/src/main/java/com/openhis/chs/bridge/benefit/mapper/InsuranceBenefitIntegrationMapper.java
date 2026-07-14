/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.benefit.mapper;

import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.chs.domain.outpatient.command.YbIdetInfoRow;
import com.openhis.chs.domain.outpatient.command.YbInsuInfoRow;
import com.openhis.chs.domain.outpatient.result.YbPersonInfoResult;
import com.openhis.chs.domain.outpatient.command.YbPersonQueryCommand;
import com.openhis.chs.domain.outpatient.command.YbTreatmentCheckCommand;
import com.openhis.chs.domain.outpatient.result.YbTreatmentCheckResult;
import com.openhis.chs.domain.outpatient.command.YbTreatmentInfoRow;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsurancePersonQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceTreatmentCheckExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceCoverageLineSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceIdentityLineSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsurancePersonQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceTreatmentCheckExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceTreatmentLineSnapshot;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

/**
 * Health ExtensionIn/Out ↔ CHS 平台 1101/2001 Command/Result 映射（无 IO）。
 *
 * <p>将 {@code health.integration.insurance.benefit} Extension 入参转换为 domain Command，
 * 再将平台查询结果映射回 ExtensionOut 快照对象，供读卡与待遇检查 Extension 使用。</p>
 */
@UtilityClass
public class InsuranceBenefitIntegrationMapper {

    /**
     * 将 Health Extension 读卡入参转换为 1101 查询 Command。
     *
     * @param command        Extension 就诊凭证与证件信息
     * @param contractReader 契约解析器
     * @param operatorId     当前操作员
     */
    public static YbPersonQueryCommand toPersonQueryCommand(
            InsurancePersonQueryExtensionIn command,
            ChsInsuranceContractReader contractReader,
            Long operatorId) {
        ChsContractSnapshot contract = contractReader.requireContract(null, command.getContractId());
        YbPersonQueryCommand platformCommand = new YbPersonQueryCommand();
        platformCommand.setContractId(contract.id());
        platformCommand.setContractBusNo(contractReader.requireContractBusNo(contract));
        platformCommand.setMdtrtCertType(command.getEncounterCredentialType());
        platformCommand.setMdtrtCertNo(command.getEncounterCredentialNo());
        platformCommand.setCardSn(command.getCardSerialNo());
        platformCommand.setCertno(command.getCertificateNo());
        platformCommand.setPsnCertType(command.getPersonCertificateType());
        platformCommand.setPsnName(command.getPersonName());
        platformCommand.setInsuplcAdmdvs(command.getInsurancePoolAreaCode());
        platformCommand.setOperatorId(operatorId);
        platformCommand.setTenantId(TenantUtils.getCurrentTenantId());
        return platformCommand;
    }

    /**
     * 将 Health Extension 待遇检查入参转换为 2001 查询 Command。
     *
     * @param command        Extension 人员编号、险种与医疗类别
     * @param contractReader 契约解析器
     * @param operatorId     当前操作员
     */
    public static YbTreatmentCheckCommand toTreatmentCheckCommand(
            InsuranceTreatmentCheckExtensionIn command,
            ChsInsuranceContractReader contractReader,
            Long operatorId) {
        ChsContractSnapshot contract = contractReader.requireContract(null, command.getContractId());
        YbTreatmentCheckCommand platformCommand = new YbTreatmentCheckCommand();
        platformCommand.setContractId(contract.id());
        platformCommand.setContractBusNo(contractReader.requireContractBusNo(contract));
        platformCommand.setPsnNo(command.getPsnNo());
        platformCommand.setInsutype(command.getInsutype());
        platformCommand.setInsuplcAdmdvs(command.getInsurancePoolAreaCode());
        String medType = "11";
        if (isNotEmpty(command.getMedType())) {
            medType = command.getMedType().trim();
        }
        platformCommand.setMedType(medType);
        platformCommand.setOperatorId(operatorId);
        platformCommand.setTenantId(TenantUtils.getCurrentTenantId());
        return platformCommand;
    }

    public static InsurancePersonQueryExtensionOut toPersonResult(YbPersonInfoResult platform) {
        InsurancePersonQueryExtensionOut result = new InsurancePersonQueryExtensionOut();
        result.setPsnNo(platform.getPsnNo());
        result.setInsuplcAdmdvs(platform.getInsuplcAdmdvs());
        result.setInsutype(platform.getInsutype());
        result.setBalc(platform.getBalc());
        result.setPsnName(platform.getPsnName());
        result.setCertno(platform.getCertno());
        result.setGend(platform.getGend());
        result.setBrdy(platform.getBrdy());
        result.setCoverageLines(toCoverageLines(platform.getInsuinfoList()));
        result.setIdentityLines(toIdentityLines(platform.getIdetinfoList()));
        return result;
    }

    public static InsuranceTreatmentCheckExtensionOut toTreatmentResult(YbTreatmentCheckResult platform) {
        InsuranceTreatmentCheckExtensionOut result = new InsuranceTreatmentCheckExtensionOut();
        result.setTreatmentLines(toTreatmentLines(platform.getTrtinfoList()));
        return result;
    }

    /**
     * 将 domain 险种信息行列表映射为参保信息快照列表。
     *
     * @param rows domain 险种信息行列表
     * @return 参保信息行快照列表
     */
    private static List<InsuranceCoverageLineSnapshot> toCoverageLines(List<YbInsuInfoRow> rows) {
        List<InsuranceCoverageLineSnapshot> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (YbInsuInfoRow row : rows) {
            InsuranceCoverageLineSnapshot snapshot = new InsuranceCoverageLineSnapshot();
            snapshot.setInsuplcAdmdvs(row.getInsuplcAdmdvs());
            snapshot.setInsutype(row.getInsutype());
            snapshot.setPsnType(row.getPsnType());
            snapshot.setPsnInsuStas(row.getPsnInsuStas());
            snapshot.setPsnInsuDate(row.getPsnInsuDate());
            snapshot.setPausInsuDate(row.getPausInsuDate());
            snapshot.setCvlservFlag(row.getCvlservFlag());
            snapshot.setEmpName(row.getEmpName());
            snapshot.setBalc(row.getBalc());
            list.add(snapshot);
        }
        return list;
    }

    /**
     * 将 domain 身份信息行列表映射为身份认定快照列表。
     *
     * @param rows domain 身份信息行列表
     * @return 身份认定行快照列表
     */
    private static List<InsuranceIdentityLineSnapshot> toIdentityLines(List<YbIdetInfoRow> rows) {
        List<InsuranceIdentityLineSnapshot> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (YbIdetInfoRow row : rows) {
            InsuranceIdentityLineSnapshot snapshot = new InsuranceIdentityLineSnapshot();
            snapshot.setPsnIdetType(row.getPsnIdetType());
            snapshot.setPsnTypeLv(row.getPsnTypeLv());
            snapshot.setMemo(row.getMemo());
            snapshot.setBegntime(row.getBegntime());
            snapshot.setEndtime(row.getEndtime());
            list.add(snapshot);
        }
        return list;
    }

    /**
     * 将 domain 待遇信息行列表映射为待遇检查快照列表。
     *
     * @param rows domain 待遇信息行列表
     * @return 待遇检查行快照列表
     */
    private static List<InsuranceTreatmentLineSnapshot> toTreatmentLines(List<YbTreatmentInfoRow> rows) {
        List<InsuranceTreatmentLineSnapshot> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (YbTreatmentInfoRow row : rows) {
            InsuranceTreatmentLineSnapshot snapshot = new InsuranceTreatmentLineSnapshot();
            snapshot.setPsnNo(row.getPsnNo());
            snapshot.setTrtChkType(row.getTrtChkType());
            snapshot.setFundPayType(row.getFundPayType());
            snapshot.setTrtEnjymntFlag(row.getTrtEnjymntFlag());
            snapshot.setBegndate(row.getBegndate());
            snapshot.setEnddate(row.getEnddate());
            snapshot.setTrtChkRslt(row.getTrtChkRslt());
            list.add(snapshot);
        }
        return list;
    }
}
