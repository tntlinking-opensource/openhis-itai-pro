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
import com.openhis.chs.domain.medicalinfo.access.Med5302AQueryInvoker;
import com.openhis.chs.domain.medicalinfo.command.QueryCum5206Command;
import com.openhis.chs.domain.medicalinfo.command.QueryOpspApproval5302ACommand;
import com.openhis.chs.domain.medicalinfo.command.QueryOpspFiling5301Command;
import com.openhis.chs.domain.medicalinfo.result.MedicalInfoQueryResult;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.entity.YbPersonCache;
import com.openhis.chs.domain.outpatient.service.YbClinicPreSettleQueryService;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.openhis.chs.domain.outpatient.service.YbPersonCacheDomainService;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterContextQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.infrastructure.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceCumulativeQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceOpspFilingQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsurancePersonQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceTreatmentCheckExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceCumulativeLineSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceCumulativeQueryExtensionOut;
import com.openhis.chs.domain.share.enums.MedType;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceEncounterPsnSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingEncounterSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingLineSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsurancePersonQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceTreatmentCheckExtensionOut;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

/**
 * 吉林医保门诊挂号待遇查询 SPI 实现。
 *
 * <p><b>SPI 接口</b>：{@link InsuranceRegisterBenefitQueryExtension}</p>
 * <p><b>Health 调用方</b>：{@code OutpatientRegistrationAppService}、{@code RegisterContractMatchSupport}</p>
 * <p><b>Bridge 委托</b>：{@link Op1101Invoker}、{@link Op2001Invoker}、{@link Med5301QueryInvoker}、
 * {@link Med5302AQueryInvoker}、{@link Med5206QueryInvoker}、{@link InsuranceBenefitIntegrationMapper}</p>
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean({
    Op1101Invoker.class,
    Op2001Invoker.class,
    Med5301QueryInvoker.class,
    Med5302AQueryInvoker.class,
    Med5206QueryInvoker.class
})
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceRegisterBenefitQueryExtensionImpl implements InsuranceRegisterBenefitQueryExtension {

    private final Op1101Invoker op1101Invoker;
    private final Op2001Invoker op2001Invoker;
    private final Med5301QueryInvoker med5301Invoker;
    private final Med5302AQueryInvoker med5302AInvoker;
    private final Med5206QueryInvoker med5206Invoker;
    private final ChsInsuranceContractReader contractReader;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final ChsContractPropertiesAccess contractPropertiesAccess;
    private final YbEncounterPlatformMapQueryService encounterPlatformMapQueryService;
    private final YbClinicPreSettleQueryService clinicPreSettleQueryService;
    private final YbPersonCacheDomainService personCacheDomainService;
    private final EncounterContextQueryService encounterContextQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;
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

    /**
     * 慢特病备案查询：【5301】主查清单，【5302A】仅补充慢/特 {@code medicalTypeCode}。
     *
     * <p>5301 失败则整单失败（对齐老系统可查省外备案）。5302A 分查失败只 warn，不影响 5301 行展示；
     * 无 5302A 标签时默认门诊慢病 {@code 140104}，特病侧命中则覆盖为 {@code 140201}。</p>
     */
    @Override
    public InsuranceOpspFilingQueryExtensionOut queryOpspFiling(InsuranceOpspFilingQueryExtensionIn command) {
        ChsContractSnapshot contract = contractReader.requireContract(null, command.getContractId());
        String busNo = contractReader.requireContractBusNo(contract);
        Long contractId = contract.id();
        String psnNo = command.getPsnNo();

        // Pre：5301 主查备案清单（失败向上抛，由调用方 warn 或转业务异常）
        LinkedHashMap<String, InsuranceOpspFilingLineSnapshot> merged = new LinkedHashMap<>();
        MedicalInfoQueryResult filing5301 = this.invokeOpspFiling5301(contractId, busNo, psnNo);
        List<Map<String, Object>> rows5301 = null;
        if (filing5301 != null) {
            rows5301 = filing5301.getRows();
        }
        this.putOpspLinesWithoutTag(merged, this.toOpspFilingLines(rows5301));

        // Pre：5302A 补充打标（单侧失败不影响已有 5301 行）
        try {
            this.apply5302AMedicalTypeTags(
                merged,
                this.invokeOpspApproval5302A(
                    contractId, busNo, psnNo, MedType.CHRONIC_DISEASE_OUTPATIENT.getCode()),
                MedType.CHRONIC_DISEASE_OUTPATIENT.getCode());
        } catch (RuntimeException ex) {
            log.warn("5302A 门诊慢病打标失败 contractId={} psnNo={}: {}", contractId, psnNo, ex.getMessage());
        }
        try {
            this.apply5302AMedicalTypeTags(
                merged,
                this.invokeOpspApproval5302A(
                    contractId, busNo, psnNo, MedType.SPECIAL_DISEASE_OUTPATIENT.getCode()),
                MedType.SPECIAL_DISEASE_OUTPATIENT.getCode());
        } catch (RuntimeException ex) {
            log.warn("5302A 门诊特病打标失败 contractId={} psnNo={}: {}", contractId, psnNo, ex.getMessage());
        }

        // Asm：无慢/特标签时默认门诊慢病，保证医生站可导入
        for (InsuranceOpspFilingLineSnapshot line : merged.values()) {
            if (line == null) {
                continue;
            }
            if (!isNotEmpty(line.getMedicalTypeCode())) {
                line.setMedicalTypeCode(MedType.CHRONIC_DISEASE_OUTPATIENT.getCode());
            }
        }

        InsuranceOpspFilingQueryExtensionOut result = new InsuranceOpspFilingQueryExtensionOut();
        result.setFilingLines(new ArrayList<>(merged.values()));
        return result;
    }

    /**
     * 调用平台【5301】人员慢特病备案查询。
     *
     * @param contractId 合同主键
     * @param busNo      合同 bus_no
     * @param psnNo      人员编号
     * @return 平台行结果
     */
    private MedicalInfoQueryResult invokeOpspFiling5301(Long contractId, String busNo, String psnNo) {
        QueryOpspFiling5301Command platformRequest = new QueryOpspFiling5301Command();
        platformRequest.setContractId(contractId);
        platformRequest.setContractBusNo(busNo);
        platformRequest.setPsnNo(psnNo);
        return med5301Invoker.invoke(platformRequest);
    }

    /**
     * 调用平台【5302A】门诊慢特病审批查询（按 med_type 分查）。
     *
     * @param contractId 合同主键
     * @param busNo      合同 bus_no
     * @param psnNo      人员编号
     * @param medType    140104 / 140201
     * @return 平台行结果
     */
    private MedicalInfoQueryResult invokeOpspApproval5302A(
        Long contractId, String busNo, String psnNo, String medType) {
        QueryOpspApproval5302ACommand platformRequest = new QueryOpspApproval5302ACommand();
        platformRequest.setContractId(contractId);
        platformRequest.setContractBusNo(busNo);
        platformRequest.setPsnNo(psnNo);
        platformRequest.setMedType(medType);
        return med5302AInvoker.invoke(platformRequest);
    }

    /**
     * 将 5301 备案行写入合并表（暂不打 medicalTypeCode）。
     *
     * <p>同病种码多条时保留结束日更晚（含空结束日=长期）的一条，避免历史过期行后写覆盖当前有效备案。</p>
     *
     * @param merged 病种码 → 行
     * @param lines  5301 解析行
     */
    private void putOpspLinesWithoutTag(
        LinkedHashMap<String, InsuranceOpspFilingLineSnapshot> merged,
        List<InsuranceOpspFilingLineSnapshot> lines) {
        for (InsuranceOpspFilingLineSnapshot line : lines) {
            if (line == null) {
                continue;
            }
            String code = line.getOpspDiseCode();
            if (code == null || code.isBlank()) {
                continue;
            }
            String key = code.trim();
            InsuranceOpspFilingLineSnapshot existing = merged.get(key);
            // 同码：取结束日更晚者，防止平台先有效后过期时被过期行覆盖。
            if (existing == null) {
                merged.put(key, line);
            } else {
                merged.put(key, this.preferOpspFilingLineByEndDate(existing, line));
            }
        }
    }

    /**
     * 用 5302A 分查结果给已有行打 {@code medicalTypeCode}；5301 未返回的码则追加。
     *
     * <p>同码先慢病后特病，特病覆盖慢病。5301 未命中时同码多条仍按结束日择优，避免只剩过期审批行。</p>
     *
     * @param merged          合并表
     * @param platform        5302A 结果
     * @param medicalTypeCode 本侧类别码
     */
    private void apply5302AMedicalTypeTags(
        LinkedHashMap<String, InsuranceOpspFilingLineSnapshot> merged,
        MedicalInfoQueryResult platform,
        String medicalTypeCode) {
        List<Map<String, Object>> rows = null;
        if (platform != null) {
            rows = platform.getRows();
        }
        List<InsuranceOpspFilingLineSnapshot> lines = this.toOpspFilingLines(rows);
        for (InsuranceOpspFilingLineSnapshot line : lines) {
            if (line == null) {
                continue;
            }
            String code = line.getOpspDiseCode();
            if (code == null || code.isBlank()) {
                continue;
            }
            String key = code.trim();
            InsuranceOpspFilingLineSnapshot existing = merged.get(key);
            // 已有同码：起止日择优后打本侧医疗类别（特病调用在后可覆盖慢病标签）。
            if (existing != null) {
                InsuranceOpspFilingLineSnapshot preferred =
                    this.preferOpspFilingLineByEndDate(existing, line);
                preferred.setMedicalTypeCode(medicalTypeCode);
                merged.put(key, preferred);
                continue;
            }
            line.setMedicalTypeCode(medicalTypeCode);
            merged.put(key, line);
        }
    }

    /**
     * 同病种码两条备案择优：结束日更晚者优先；结束日相同则开始日更晚者优先。
     *
     * <p>空结束日或无法解析视为长期有效（{@link LocalDate#MAX}），与医生站有效期校验「空结束日=有效」一致。</p>
     *
     * @param existing  合并表中已有行
     * @param candidate 新候选行
     * @return 应保留的行
     */
    private InsuranceOpspFilingLineSnapshot preferOpspFilingLineByEndDate(
        InsuranceOpspFilingLineSnapshot existing,
        InsuranceOpspFilingLineSnapshot candidate) {
        if (existing == null) {
            return candidate;
        }
        if (candidate == null) {
            return existing;
        }
        LocalDate existingEnd = this.parseOpspFilingDateOrMax(existing.getEnddate());
        LocalDate candidateEnd = this.parseOpspFilingDateOrMax(candidate.getEnddate());
        // 结束日更晚 → 保留候选（例如 2999-01-01 优于 2024-12-31）。
        if (candidateEnd.isAfter(existingEnd)) {
            return candidate;
        }
        if (candidateEnd.isBefore(existingEnd)) {
            return existing;
        }
        LocalDate existingBegin = this.parseOpspFilingDateOrMin(existing.getBegndate());
        LocalDate candidateBegin = this.parseOpspFilingDateOrMin(candidate.getBegndate());
        // 结束日相同：保留开始日更晚（当前周期）的备案。
        if (candidateBegin.isAfter(existingBegin)) {
            return candidate;
        }
        return existing;
    }

    /**
     * 解析备案日期；空或非法时返回 {@link LocalDate#MAX}（按长期有效处理）。
     *
     * @param raw 平台日期字符串（yyyy-MM-dd）
     * @return 解析结果或 MAX
     */
    private LocalDate parseOpspFilingDateOrMax(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.MAX;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            return LocalDate.MAX;
        }
    }

    /**
     * 解析备案开始日；空或非法时返回 {@link LocalDate#MIN}（并列比较时不抢优先）。
     *
     * @param raw 平台日期字符串（yyyy-MM-dd）
     * @return 解析结果或 MIN
     */
    private LocalDate parseOpspFilingDateOrMin(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.MIN;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            return LocalDate.MIN;
        }
    }

    /**
     * 本地：解析就诊侧慢特病备案查询所需 psn_no 与 contractId。
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
        // ③ 读卡缓存：挂号读卡已写 yb_person_cache，但自费挂号费未触发 2201 时仍可查 5302A。
        return resolveOpspFromPersonCache(encounterId, tenantId);
    }

    /**
     * 本地：按就诊批量回填医保人员编号与医疗类别（{@code yb_encounter_platform_map}）。
     *
     * <p>同一就诊多条映射时取首条非空 {@code psn_no}；仅读本地落库，不调平台。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊主键 → 展示快照；无映射不含键
     */
    @Override
    public Map<Long, InsuranceEncounterPsnSnapshot> mapEncounterPsnByEncounterIds(
        Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            return Collections.emptyMap();
        }
        List<YbEncounterPlatformMap> maps =
            encounterPlatformMapQueryService.listByEncounterIds(encounterIds, tenantId);
        if (maps == null || maps.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, InsuranceEncounterPsnSnapshot> result = new HashMap<>();
        // Loop：同一就诊仅保留首条有效人员编号
        for (YbEncounterPlatformMap row : maps) {
            if (row == null || row.getEncounterId() == null || !isNotEmpty(row.getPsnNo())) {
                continue;
            }
            if (result.containsKey(row.getEncounterId())) {
                continue;
            }
            InsuranceEncounterPsnSnapshot snapshot = new InsuranceEncounterPsnSnapshot();
            snapshot.setPsnNo(row.getPsnNo().trim());
            if (isNotEmpty(row.getMedType())) {
                String medType = row.getMedType().trim();
                snapshot.setMedType(medType);
                snapshot.setMedTypeDisplay(this.formatMedTypeDisplay(medType));
            }
            result.put(row.getEncounterId(), snapshot);
        }
        return result;
    }

    /**
     * 按就诊批量解析险种：账户 → 读卡缓存（含过期）→ 门诊预结算。
     */
    @Override
    public Map<Long, String> mapInsutypeByEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> out = new HashMap<>();
        // ① 就诊医保账户（若挂号/入院已落库）
        Map<Long, Account> miAccountByEncounterId =
            accountQueryService.mapMedicalInsuranceAccountsByEncounterIds(encounterIds);
        for (Map.Entry<Long, Account> entry : miAccountByEncounterId.entrySet()) {
            Account account = entry.getValue();
            if (entry.getKey() == null || account == null || !isNotEmpty(account.getInsutype())) {
                continue;
            }
            out.put(entry.getKey(), account.getInsutype().trim());
        }
        // ② 读卡缓存（含已过期，住院跨日仍可回显）
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            tenantId = 1L;
        }
        this.fillInsutypeFromPersonCache(encounterIds, tenantId, out);
        // ③ 门诊预结算快照回落
        this.fillInsutypeFromClinicPreSettle(encounterIds, tenantId, out);
        return out;
    }

    /**
     * 用人员缓存填充险种（仅补尚未有值的就诊）。
     */
    private void fillInsutypeFromPersonCache(
        Collection<Long> encounterIds, Long tenantId, Map<Long, String> out) {
        Set<Long> missing = new HashSet<>();
        for (Long encounterId : encounterIds) {
            if (encounterId != null && !out.containsKey(encounterId)) {
                missing.add(encounterId);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        Map<Long, Long> patientIdByEncounterId = encounterQueryService.mapPatientIdByEncounterIds(missing);
        Set<Long> patientIds = new HashSet<>(patientIdByEncounterId.values());
        Map<Long, Patient> patientById = patientQueryService.loadPatientEntityMapByIds(patientIds);
        Map<Long, String> contractNoByEncounterId =
            accountQueryService.mapPreferredContractNoByEncounterIds(missing);
        Set<String> contractBusNos = new HashSet<>();
        for (String busNo : contractNoByEncounterId.values()) {
            if (isNotEmpty(busNo)) {
                contractBusNos.add(busNo.trim());
            }
        }
        Map<String, Long> contractIdByBusNo = contractQueryService.mapContractIdByBusNoIn(contractBusNos);
        Map<String, String> insutypeByCacheKey = new HashMap<>();
        for (Long encounterId : missing) {
            Long patientId = patientIdByEncounterId.get(encounterId);
            if (patientId == null) {
                continue;
            }
            Patient patient = patientById.get(patientId);
            if (patient == null || !isNotEmpty(patient.getIdCard())) {
                continue;
            }
            String contractNo = contractNoByEncounterId.get(encounterId);
            if (!isNotEmpty(contractNo)) {
                continue;
            }
            Long contractId = contractIdByBusNo.get(contractNo.trim());
            if (contractId == null) {
                continue;
            }
            String idCard = patient.getIdCard().trim();
            String cacheKey = contractId + "\0" + idCard;
            if (insutypeByCacheKey.containsKey(cacheKey)) {
                String cached = insutypeByCacheKey.get(cacheKey);
                if (isNotEmpty(cached)) {
                    out.put(encounterId, cached);
                }
                continue;
            }
            YbPersonCache cache = personCacheDomainService
                .findLatestByContractIdAndIdCard(contractId, idCard, tenantId)
                .orElse(null);
            String insutype = null;
            if (cache != null && isNotEmpty(cache.getInsutype())) {
                insutype = cache.getInsutype().trim();
            }
            insutypeByCacheKey.put(cacheKey, insutype == null ? "" : insutype);
            if (insutype != null) {
                out.put(encounterId, insutype);
            }
        }
    }

    /**
     * 用门诊预结算快照按就诊回落险种（仅补尚未有值的就诊）。
     */
    private void fillInsutypeFromClinicPreSettle(
        Collection<Long> encounterIds, Long tenantId, Map<Long, String> out) {
        Set<Long> missing = new HashSet<>();
        for (Long encounterId : encounterIds) {
            if (encounterId != null && !out.containsKey(encounterId)) {
                missing.add(encounterId);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        List<YbClinicPreSettle> rows = clinicPreSettleQueryService.listByEncounterIds(missing, tenantId);
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (YbClinicPreSettle row : rows) {
            if (row == null || row.getEncounterId() == null || out.containsKey(row.getEncounterId())) {
                continue;
            }
            if (!isNotEmpty(row.getInsutype())) {
                continue;
            }
            out.put(row.getEncounterId(), row.getInsutype().trim());
        }
    }

    /**
     * 医疗类别 code →「code-名称」展示。
     *
     * @param medType 医疗类别 code
     * @return 展示文案
     */
    private String formatMedTypeDisplay(String medType) {
        MedType matched = MedType.fromCodeOptional(medType);
        if (matched == null) {
            return medType;
        }
        return matched.getCode() + "-" + matched.getLabel();
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
            // 5301: begndate；5302A 规范: begindate
            snapshot.setBegndate(stringValue(row, "begndate", "begnDate", "begindate", "beginDate"));
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
