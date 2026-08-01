/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.benefit;

import com.openhis.chs.bridge.benefit.mapper.InsuranceBenefitIntegrationMapper;
import com.openhis.chs.bridge.benefit.invoker.Op1101Invoker;
import com.openhis.chs.bridge.benefit.invoker.Op2001Invoker;
import com.openhis.chs.domain.catalog.access.ChsContractPropertiesAccess;
import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.chs.domain.medicalinfo.access.Med5206QueryInvoker;
import com.openhis.chs.domain.medicalinfo.access.Med5301QueryInvoker;
import com.openhis.chs.domain.medicalinfo.command.QueryCum5206Command;
import com.openhis.chs.domain.medicalinfo.command.QueryOpspFiling5301Command;
import com.openhis.chs.domain.medicalinfo.result.MedicalInfoQueryResult;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.entity.YbPersonCache;
import com.openhis.chs.domain.outpatient.service.YbClinicPreSettleQueryService;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.openhis.chs.domain.outpatient.service.YbPersonCacheDomainService;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterContextQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.infrastructure.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceCumulativeQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceOpspFilingQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsurancePersonQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceTreatmentCheckExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceCumulativeLineSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceCumulativeQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingEncounterSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingLineSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsurancePersonQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceTreatmentCheckExtensionOut;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

/**
 * 吉林医保门诊挂号待遇查询 SPI 实现。
 *
 * <p><b>SPI 接口</b>：{@link InsuranceRegisterBenefitQueryExtension}</p>
 * <p><b>Health 调用方</b>：{@code OutpatientRegistrationAppService}、{@code RegisterContractMatchSupport}</p>
 * <p><b>Bridge 委托</b>：{@link Op1101Invoker}、{@link Op2001Invoker}、{@link Med5301QueryInvoker}、
 * {@link Med5206QueryInvoker}、{@link InsuranceBenefitIntegrationMapper}</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean({Op1101Invoker.class, Op2001Invoker.class, Med5301QueryInvoker.class, Med5206QueryInvoker.class})
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceRegisterBenefitQueryExtensionImpl implements InsuranceRegisterBenefitQueryExtension {

    private final Op1101Invoker op1101Invoker;
    private final Op2001Invoker op2001Invoker;
    private final Med5301QueryInvoker med5301Invoker;
    private final Med5206QueryInvoker med5206Invoker;
    private final ChsInsuranceContractReader contractReader;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final ChsContractPropertiesAccess contractPropertiesAccess;
    private final YbEncounterPlatformMapQueryService encounterPlatformMapQueryService;
    private final YbClinicPreSettleQueryService clinicPreSettleQueryService;
    private final YbPersonCacheDomainService personCacheDomainService;
    private final EncounterContextQueryService encounterContextQueryService;
    private final AccountQueryService accountQueryService;
    private final ContractQueryService contractQueryService;

    /** 本地：返回 Extension 已启用。 */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /** 本地：判断合同是否为医保第三方合同。 */
    @Override
    public boolean supportsThirdPartyContract(Long contractId) {
        return contractReader.isInsuranceContract(contractId);
    }

    /** 【1101】查询参保人员基本信息及参保状态。 */
    @Override
    public InsurancePersonQueryExtensionOut queryPerson(InsurancePersonQueryExtensionIn command) {
        Long operatorId = currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录");
        return InsuranceBenefitIntegrationMapper.toPersonResult(
            op1101Invoker.invoke(
                InsuranceBenefitIntegrationMapper.toPersonQueryCommand(command, contractReader, operatorId)));
    }

    /** 【2001】检查人员待遇享受资格。 */
    @Override
    public InsuranceTreatmentCheckExtensionOut checkTreatment(InsuranceTreatmentCheckExtensionIn command) {
        Long operatorId = currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录");
        return InsuranceBenefitIntegrationMapper.toTreatmentResult(
            op2001Invoker.invoke(
                InsuranceBenefitIntegrationMapper.toTreatmentCheckCommand(command, contractReader, operatorId)));
    }

    /** 【5301】查询人员慢特病备案信息。 */
    @Override
    public InsuranceOpspFilingQueryExtensionOut queryOpspFiling(InsuranceOpspFilingQueryExtensionIn command) {
        ChsContractSnapshot contract = contractReader.requireContract(null, command.getContractId());
        QueryOpspFiling5301Command platformRequest = new QueryOpspFiling5301Command();
        platformRequest.setContractId(contract.id());
        platformRequest.setContractBusNo(contractReader.requireContractBusNo(contract));
        platformRequest.setPsnNo(command.getPsnNo());
        return toOpspFilingResult(med5301Invoker.invoke(platformRequest));
    }

    /**
     * 本地：解析就诊侧 5301 所需 psn_no 与 contractId。
     *
     * <p>优先级：{@code yb_encounter_platform_map}（2201 后）→ {@code yb_clinic_pre_settle}
     * （医保预结算后）→ 就诊费用性质 + 读卡当日 {@code yb_person_cache}（挂号读卡后、尚未平台建档）。</p>
     *
     * @param encounterId 就诊主键
     * @return 上下文；无可用人员编号时 {@code null}
     */
    @Override
    public InsuranceOpspFilingEncounterSnapshot resolveOpspFilingEncounter(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            return null;
        }
        // ① 平台就诊映射（2201 之后最完整）。
        InsuranceOpspFilingEncounterSnapshot fromMap = resolveOpspFromPlatformMap(encounterId, tenantId);
        if (fromMap != null) {
            return fromMap;
        }
        // ② 门诊预结算快照（挂号费/诊间医保预结算后）。
        InsuranceOpspFilingEncounterSnapshot fromPreSettle = resolveOpspFromPreSettle(encounterId, tenantId);
        if (fromPreSettle != null) {
            return fromPreSettle;
        }
        // ③ 读卡缓存：挂号读卡已写 yb_person_cache，但自费挂号费未触发 2201 时仍可查 5301。
        return resolveOpspFromPersonCache(encounterId, tenantId);
    }

    /**
     * 自 {@code yb_encounter_platform_map} 取首条有效 contractId+psnNo。
     */
    private InsuranceOpspFilingEncounterSnapshot resolveOpspFromPlatformMap(Long encounterId, Long tenantId) {
        List<YbEncounterPlatformMap> maps =
            encounterPlatformMapQueryService.listByEncounterIds(List.of(encounterId), tenantId);
        if (maps == null || maps.isEmpty()) {
            return null;
        }
        for (YbEncounterPlatformMap row : maps) {
            if (row.getContractId() == null || !isNotEmpty(row.getPsnNo())) {
                continue;
            }
            return toOpspFilingEncounterSnapshot(row.getContractId(), row.getPsnNo());
        }
        return null;
    }

    /**
     * 自 {@code yb_clinic_pre_settle} 取最新一条有效 contractId+psnNo。
     */
    private InsuranceOpspFilingEncounterSnapshot resolveOpspFromPreSettle(Long encounterId, Long tenantId) {
        List<YbClinicPreSettle> rows =
            clinicPreSettleQueryService.listByEncounterIds(List.of(encounterId), tenantId);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        for (YbClinicPreSettle row : rows) {
            if (row == null || row.getContractId() == null || !isNotEmpty(row.getPsnNo())) {
                continue;
            }
            return toOpspFilingEncounterSnapshot(row.getContractId(), row.getPsnNo());
        }
        return null;
    }

    /**
     * 自就诊费用性质 + 患者身份证命中读卡当日人员缓存。
     */
    private InsuranceOpspFilingEncounterSnapshot resolveOpspFromPersonCache(Long encounterId, Long tenantId) {
        OutpatientEncounterContextQueryModel encounter =
            encounterContextQueryService.getOutpatientContextByEncounterId(encounterId).orElse(null);
        if (encounter == null || !isNotEmpty(encounter.getIdCard())) {
            return null;
        }
        String contractBusNo =
            accountQueryService.mapPreferredContractNoByEncounterIds(List.of(encounterId)).get(encounterId);
        if (!isNotEmpty(contractBusNo)) {
            return null;
        }
        Contract contract = contractQueryService.findOptionalContractEntityByBusNo(contractBusNo.trim()).orElse(null);
        if (contract == null || contract.getId() == null
            || !contractReader.isInsuranceContract(contract.getId())) {
            return null;
        }
        YbPersonCache cache = personCacheDomainService
            .findValidByContractIdAndIdCard(contract.getId(), encounter.getIdCard().trim(), tenantId)
            .orElse(null);
        if (cache == null || !isNotEmpty(cache.getPsnNo())) {
            return null;
        }
        return toOpspFilingEncounterSnapshot(contract.getId(), cache.getPsnNo());
    }

    private static InsuranceOpspFilingEncounterSnapshot toOpspFilingEncounterSnapshot(
        Long contractId, String psnNo) {
        InsuranceOpspFilingEncounterSnapshot snapshot = new InsuranceOpspFilingEncounterSnapshot();
        snapshot.setContractId(contractId);
        snapshot.setPsnNo(psnNo.trim());
        return snapshot;
    }

    /** 【5206】查询人员累计信息（年度/月度累计）。 */
    @Override
    public InsuranceCumulativeQueryExtensionOut queryCumulative(InsuranceCumulativeQueryExtensionIn command) {
        ChsContractSnapshot contract = contractReader.requireContract(null, command.getContractId());
        QueryCum5206Command platformRequest = new QueryCum5206Command();
        platformRequest.setContractId(contract.id());
        platformRequest.setContractBusNo(contractReader.requireContractBusNo(contract));
        platformRequest.setPsnNo(command.getPsnNo());
        platformRequest.setCumYm(command.getCumYm());
        return toCumulativeResult(med5206Invoker.invoke(platformRequest));
    }

    /** 本地：从合同配置解析统筹区划代码，无网关出站。 */
    @Override
    public String resolveTreatmentAreaCode(Long contractId) {
        if (contractId == null) {
            return null;
        }
        ChsContractSnapshot contract = contractReader.requireContract(null, contractId);
        String contractBusNo = contractReader.requireContractBusNo(contract);
        return contractPropertiesAccess.findAdmVs(contractBusNo).orElse(null);
    }

    /**
     * 将【5301】平台查询结果映射为慢特病备案 ExtensionOut。
     *
     * @param platform 医保平台查询结果
     * @return 慢特病备案查询扩展出参
     */
    private InsuranceOpspFilingQueryExtensionOut toOpspFilingResult(MedicalInfoQueryResult platform) {
        InsuranceOpspFilingQueryExtensionOut result = new InsuranceOpspFilingQueryExtensionOut();
        List<Map<String, Object>> rows = null;
        if (platform != null) {
            rows = platform.getRows();
        }
        result.setFilingLines(toOpspFilingLines(rows));
        return result;
    }

    /**
     * 将平台行数据列表映射为慢特病备案行快照。
     *
     * @param rows 平台返回的行数据列表
     * @return 慢特病备案行快照列表
     */
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

    /**
     * 将【5206】平台查询结果映射为累计信息 ExtensionOut。
     *
     * @param platform 医保平台查询结果
     * @return 累计信息查询扩展出参
     */
    private InsuranceCumulativeQueryExtensionOut toCumulativeResult(MedicalInfoQueryResult platform) {
        InsuranceCumulativeQueryExtensionOut result = new InsuranceCumulativeQueryExtensionOut();
        List<Map<String, Object>> rows = null;
        if (platform != null) {
            rows = platform.getRows();
        }
        result.setCumulativeLines(toCumulativeLines(rows));
        return result;
    }

    /**
     * 将平台行数据列表映射为累计信息行快照。
     *
     * @param rows 平台返回的行数据列表
     * @return 累计信息行快照列表
     */
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

    /**
     * 从行数据中按候选键名依次取值，返回首个非空字符串。
     *
     * @param row  平台行数据
     * @param keys 候选字段名（按优先级排列）
     * @return 首个非空字符串值；均未命中时返回 {@code null}
     */
    private String stringValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null && isNotEmpty(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    /**
     * 从行数据中解析指定键的数值为 {@link BigDecimal}。
     *
     * @param row 平台行数据
     * @param key 字段名
     * @return 解析后的数值；缺失或格式非法时返回 {@code null}
     */
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
