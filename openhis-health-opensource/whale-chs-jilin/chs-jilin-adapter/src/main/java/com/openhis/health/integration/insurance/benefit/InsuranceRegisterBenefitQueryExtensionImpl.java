/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.benefit;

import com.openhis.chs.domain.catalog.port.ChsContractPropertiesPort;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryCum5206Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryOpspFiling5301Request;
import com.openhis.chs.domain.medicalinfo.dto.response.MedicalInfoQueryResult;
import com.openhis.chs.domain.medicalinfo.facade.ChsMedicalInfoQueryOperations;
import com.openhis.chs.domain.outpatient.dto.YbIdetInfoRow;
import com.openhis.chs.domain.outpatient.dto.YbInsuInfoRow;
import com.openhis.chs.domain.outpatient.dto.YbPersonInfoResult;
import com.openhis.chs.domain.outpatient.dto.YbPersonQueryCommand;
import com.openhis.chs.domain.outpatient.dto.YbTreatmentCheckCommand;
import com.openhis.chs.domain.outpatient.dto.YbTreatmentCheckResult;
import com.openhis.chs.domain.outpatient.dto.YbTreatmentInfoRow;
import com.openhis.chs.domain.outpatient.facade.ChsOutpatientSettleOperations;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.integration.insurance.benefit.command.InsuranceCumulativeQueryCommand;
import com.openhis.health.integration.insurance.benefit.command.InsuranceOpspFilingQueryCommand;
import com.openhis.health.integration.insurance.benefit.command.InsurancePersonQueryCommand;
import com.openhis.health.integration.insurance.benefit.command.InsuranceTreatmentCheckCommand;
import com.openhis.health.integration.insurance.benefit.result.InsuranceCumulativeLineSnapshot;
import com.openhis.health.integration.insurance.benefit.result.InsuranceCumulativeQueryResult;
import com.openhis.health.integration.insurance.benefit.result.InsuranceCoverageLineSnapshot;
import com.openhis.health.integration.insurance.benefit.result.InsuranceIdentityLineSnapshot;
import com.openhis.health.integration.insurance.benefit.result.InsuranceOpspFilingLineSnapshot;
import com.openhis.health.integration.insurance.benefit.result.InsuranceOpspFilingQueryResult;
import com.openhis.health.integration.insurance.benefit.result.InsurancePersonQueryResult;
import com.openhis.health.integration.insurance.benefit.result.InsuranceTreatmentCheckResult;
import com.openhis.health.integration.insurance.benefit.result.InsuranceTreatmentLineSnapshot;
import com.openhis.health.integration.insurance.support.InsuranceContractResolver;
import com.openhis.whale.chs.jilin.contracts.client.ChsOutpatientSettlementClient;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 门诊挂号待遇查询 ↔ 吉林医保平台查询的 Extension 桥接层。
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(ChsOutpatientSettlementClient.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceRegisterBenefitQueryExtensionImpl implements InsuranceRegisterBenefitQueryExtension {

    private final ChsOutpatientSettleOperations outpatientSettleOperations;
    private final ChsMedicalInfoQueryOperations medicalInfoQueryOperations;
    private final InsuranceContractResolver contractResolver;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final ChsContractPropertiesPort contractPropertiesPort;
    private final ChsOutpatientSettlementClient chsOutpatientSettlementClient;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean supportsThirdPartyContract(Long contractId) {
        return chsOutpatientSettlementClient.isInsuranceContract(contractId);
    }

    @Override
    public InsurancePersonQueryResult queryPerson(InsurancePersonQueryCommand command) {
        Contract contract = contractResolver.requireContract(null, command.getContractId());
        YbPersonQueryCommand platformCommand = new YbPersonQueryCommand();
        platformCommand.setContractId(contract.getId());
        platformCommand.setContractBusNo(contractResolver.requireContractBusNo(contract));
        platformCommand.setMdtrtCertType(command.getEncounterCredentialType());
        platformCommand.setMdtrtCertNo(command.getEncounterCredentialNo());
        platformCommand.setCardSn(command.getCardSerialNo());
        platformCommand.setCertno(command.getCertificateNo());
        platformCommand.setPsnCertType(command.getPersonCertificateType());
        platformCommand.setPsnName(command.getPersonName());
        platformCommand.setInsuplcAdmdvs(command.getInsurancePoolAreaCode());
        platformCommand.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        platformCommand.setTenantId(TenantUtils.getCurrentTenantId());
        return toPersonResult(outpatientSettleOperations.getPersonInfo(platformCommand));
    }

    @Override
    public InsuranceTreatmentCheckResult checkTreatment(InsuranceTreatmentCheckCommand command) {
        Contract contract = contractResolver.requireContract(null, command.getContractId());
        YbTreatmentCheckCommand platformCommand = new YbTreatmentCheckCommand();
        platformCommand.setContractId(contract.getId());
        platformCommand.setContractBusNo(contractResolver.requireContractBusNo(contract));
        platformCommand.setPsnNo(command.getPsnNo());
        platformCommand.setInsutype(command.getInsutype());
        platformCommand.setInsuplcAdmdvs(command.getInsurancePoolAreaCode());
        String medType = "11";
        if (StringUtils.isNotBlank(command.getMedType())) {
            medType = command.getMedType().trim();
        }
        platformCommand.setMedType(medType);
        platformCommand.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        platformCommand.setTenantId(TenantUtils.getCurrentTenantId());
        return toTreatmentResult(outpatientSettleOperations.checkPersonTreatment(platformCommand));
    }

    @Override
    public InsuranceOpspFilingQueryResult queryOpspFiling(InsuranceOpspFilingQueryCommand command) {
        Contract contract = contractResolver.requireContract(null, command.getContractId());
        QueryOpspFiling5301Request platformRequest = new QueryOpspFiling5301Request();
        platformRequest.setContractId(contract.getId());
        platformRequest.setContractBusNo(contractResolver.requireContractBusNo(contract));
        platformRequest.setPsnNo(command.getPsnNo());
        return toOpspFilingResult(medicalInfoQueryOperations.queryOpspFiling5301(platformRequest));
    }

    @Override
    public InsuranceCumulativeQueryResult queryCumulative(InsuranceCumulativeQueryCommand command) {
        Contract contract = contractResolver.requireContract(null, command.getContractId());
        QueryCum5206Request platformRequest = new QueryCum5206Request();
        platformRequest.setContractId(contract.getId());
        platformRequest.setContractBusNo(contractResolver.requireContractBusNo(contract));
        platformRequest.setPsnNo(command.getPsnNo());
        platformRequest.setCumYm(command.getCumYm());
        return toCumulativeResult(medicalInfoQueryOperations.queryCum5206(platformRequest));
    }

    @Override
    public String resolveTreatmentAreaCode(Long contractId) {
        if (contractId == null) {
            return null;
        }
        Contract contract = contractResolver.requireContract(null, contractId);
        String contractBusNo = contractResolver.requireContractBusNo(contract);
        return contractPropertiesPort.findAdmVs(contractBusNo).orElse(null);
    }

    private InsurancePersonQueryResult toPersonResult(YbPersonInfoResult platform) {
        InsurancePersonQueryResult result = new InsurancePersonQueryResult();
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

    private List<InsuranceCoverageLineSnapshot> toCoverageLines(List<YbInsuInfoRow> rows) {
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

    private List<InsuranceIdentityLineSnapshot> toIdentityLines(List<YbIdetInfoRow> rows) {
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

    private InsuranceTreatmentCheckResult toTreatmentResult(YbTreatmentCheckResult platform) {
        InsuranceTreatmentCheckResult result = new InsuranceTreatmentCheckResult();
        result.setTreatmentLines(toTreatmentLines(platform.getTrtinfoList()));
        return result;
    }

    private List<InsuranceTreatmentLineSnapshot> toTreatmentLines(List<YbTreatmentInfoRow> rows) {
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

    private InsuranceOpspFilingQueryResult toOpspFilingResult(MedicalInfoQueryResult platform) {
        InsuranceOpspFilingQueryResult result = new InsuranceOpspFilingQueryResult();
        List<Map<String, Object>> rows = null;
        if (platform != null) {
            rows = platform.getRows();
        }
        result.setFilingLines(toOpspFilingLines(rows));
        return result;
    }

    private List<InsuranceOpspFilingLineSnapshot> toOpspFilingLines(List<Map<String, Object>> rows) {
        List<InsuranceOpspFilingLineSnapshot> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            InsuranceOpspFilingLineSnapshot snapshot = new InsuranceOpspFilingLineSnapshot();
            snapshot.setOpspDiseCode(stringValue(row, "opsp_dise_code", "opspDiseCode"));
            snapshot.setOpspDiseName(stringValue(row, "opsp_dise_name", "opspDiseName"));
            snapshot.setBegndate(stringValue(row, "begndate", "begnDate"));
            snapshot.setEnddate(stringValue(row, "enddate", "endDate"));
            list.add(snapshot);
        }
        return list;
    }

    private InsuranceCumulativeQueryResult toCumulativeResult(MedicalInfoQueryResult platform) {
        InsuranceCumulativeQueryResult result = new InsuranceCumulativeQueryResult();
        List<Map<String, Object>> rows = null;
        if (platform != null) {
            rows = platform.getRows();
        }
        result.setCumulativeLines(toCumulativeLines(rows));
        return result;
    }

    private List<InsuranceCumulativeLineSnapshot> toCumulativeLines(List<Map<String, Object>> rows) {
        List<InsuranceCumulativeLineSnapshot> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            InsuranceCumulativeLineSnapshot snapshot = new InsuranceCumulativeLineSnapshot();
            snapshot.setInsutype(stringValue(row, "insutype"));
            snapshot.setYear(stringValue(row, "year"));
            snapshot.setCumYm(stringValue(row, "cum_ym", "cumYm"));
            snapshot.setCumTypeCode(stringValue(row, "cum_type_code", "cumTypeCode"));
            snapshot.setCum(decimalValue(row, "cum"));
            list.add(snapshot);
        }
        return list;
    }

    private String stringValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private BigDecimal decimalValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
