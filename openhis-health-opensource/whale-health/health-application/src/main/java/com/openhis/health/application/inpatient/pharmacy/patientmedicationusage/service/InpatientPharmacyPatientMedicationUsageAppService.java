/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.service;

import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.assembler.InpatientPatientMedicationUsageAssembler;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageDetailLineDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageSummaryLineDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageTotalsDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.mapper.InpatientPatientMedicationUsageMapper;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.request.LoadInpatientPatientMedicationUsagePageInitRequest;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.request.PageInpatientPatientMedicationUsagePatientsRequest;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.request.QueryInpatientPatientMedicationUsageRequest;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.response.LoadInpatientPatientMedicationUsagePageInitResponse;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.response.PageInpatientPatientMedicationUsagePatientsResponse;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.response.QueryInpatientPatientMedicationUsageResponse;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.support.ChargePricingView;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.support.InpatientPatientMedicationUsageChargePricingSupport;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.shared.validator.PatientEncounterConsistencyGate;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.InpatientPatientMedicationUsageDispenseQuery;
import com.openhis.health.domain.medication.medicationdispense.queries.model.InpatientPatientMedicationUsageDispenseRow;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.InpatientPharmacyPatientMedicationUsageConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 住院患者药品使用应用服务（AS-IP-PHA-005，IF-IP-PMU-01～03）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>读侧：住院患者分页经 {@link EncounterContextReader}；使用行经
 *       {@link MedicationDispenseQueryService#listInpatientPatientMedicationUsageDispenseRows}</li>
 *   <li>账单价：经 {@link InpatientPatientMedicationUsageChargePricingSupport}（历史 ChargeItem + 待结算合并）</li>
 *   <li>产品展示：经 {@link ProductDefinitionDisplayTerminologyAssembler#loadAsMap}</li>
 *   <li>无写路径、无 Cmd</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inpatientPharmacyPatientMedicationUsage/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientPharmacyPatientMedicationUsageAppService {

    /** 分页窗口门禁（IF-02）。 */
    private final PageWindowGate pageWindowGate;

    /** 患者与就诊交叉校验（IF-03）。 */
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /** 住院就诊上下文读。 */
    private final EncounterContextReader encounterContextReader;

    /** 药品发放使用行读。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 账单价批量装载。 */
    private final InpatientPatientMedicationUsageChargePricingSupport chargePricingSupport;

    /** 产品定义批量展示装载（`ProductDefinitionDisplayTerminologyAssembler#loadAsMap`）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** Request/Response 映射。 */
    private final InpatientPatientMedicationUsageMapper inpatientPatientMedicationUsageMapper;

    /** 明细/汇总/合计装配。 */
    private final InpatientPatientMedicationUsageAssembler inpatientPatientMedicationUsageAssembler;

    /**
     * IF-IP-PMU-01：住院患者药品使用页初始化。
     *
     * <p>下发默认当日日期区间、明细/汇总值集与展示发药状态三态说明（BR-IP-PMU-002）。</p>
     *
     * @param request 可空体占位请求
     * @return 初始化响应
     * @throws BusinessException EX-IP-PMU-101 未预期基础设施异常
     */
    public LoadInpatientPatientMedicationUsagePageInitResponse loadInpatientPatientMedicationUsagePageInit(
        LoadInpatientPatientMedicationUsagePageInitRequest request) {
        try {
            // Asm：写死默认当日日期区间与明细/汇总、待发/已发/已退选项（BR-IP-PMU-002）
            return this.inpatientPatientMedicationUsageMapper.buildPageInitResponse(LocalDate.now());
        } catch (RuntimeException ex) {
            // 初始化无业务失败路径：基础设施异常统一 EX-IP-PMU-101
            log.warn("住院患者药品使用页初始化失败", ex);
            throw new BusinessException(
                InpatientPharmacyPatientMedicationUsageConstants.EX_IP_PMU_101,
                "页面加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-PMU-02：分页检索住院在院患者候选。
     *
     * <p>关键字模糊姓名/住院号/床号；默认在院态；不按药房管辖收窄（BR-IP-PMU-009/010）。</p>
     *
     * @param request 关键字与分页窗口
     * @return 患者候选分页
     * @throws BusinessException EX-IP-PMU-101 分页非法或检索失败
     */
    public PageInpatientPatientMedicationUsagePatientsResponse pageInpatientPatientMedicationUsagePatients(
        PageInpatientPatientMedicationUsagePatientsRequest request) {
        // Gate：分页窗口必填且 ≥1
        this.pageWindowGate.requirePositivePageWindow(
            request,
            InpatientPharmacyPatientMedicationUsageConstants.EX_IP_PMU_101,
            "页面加载失败，请稍后重试");
        try {
            // Pre：Request → 住院上下文分页 Query（写入在院状态白名单）
            InpatientEncounterPageQuery query =
                this.inpatientPatientMedicationUsageMapper.toInpatientEncounterPageQuery(request);
            // Pre：分页加载在院住院上下文；不拼 location_id
            Page<InpatientEncounterContextQueryModel> page =
                this.encounterContextReader.pageInpatientEncounterContexts(query);
            // Asm：映射候选行（PagedResult 壳）
            return this.inpatientPatientMedicationUsageMapper.toPatientPageResponse(page);
        } catch (BusinessException ex) {
            // 已按 AS 抛出的业务码原样上抛（含分页 Gate）
            throw ex;
        } catch (RuntimeException ex) {
            // 检索链路未捕获异常 → EX-IP-PMU-101，避免前端看到框架文案
            log.warn("住院患者药品使用-患者检索失败", ex);
            throw new BusinessException(
                InpatientPharmacyPatientMedicationUsageConstants.EX_IP_PMU_101,
                "页面加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-PMU-03：查询住院患者药品使用（明细或汇总 + 合计净额）。
     *
     * @param request 就诊、患者、日期区间与展示模式
     * @return 明细或汇总行 + 合计
     * @throws BusinessException EX-IP-PMU-102/103/201/202
     */
    public QueryInpatientPatientMedicationUsageResponse queryInpatientPatientMedicationUsage(
        QueryInpatientPatientMedicationUsageRequest request) {
        // Gate：缺参、日期倒置、presentationMode 非法时拒绝进入 Pre（BR-IP-PMU-001/002/201/202）
        this.validateQueryUsageRequest(request);
        String presentationMode = request.getPresentationMode().trim();
        // Gate：住院上下文须存在（mustGet 隐含 IMP）；否则 EX-IP-PMU-102
        InpatientEncounterContextQueryModel context;
        try {
            context = this.encounterContextReader.mustGetInpatientContextByEncounterId(request.getEncounterId());
        } catch (BusinessException ex) {
            // 领域「就诊不存在」等统一映射为本页患者不可用提示
            throw new BusinessException(
                InpatientPharmacyPatientMedicationUsageConstants.EX_IP_PMU_102,
                "当前患者不可用，请重新选择");
        }
        // Gate：请求 patientId 须与就诊归属一致，防止串患查询
        this.patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            context.getPatientId(),
            InpatientPharmacyPatientMedicationUsageConstants.EX_IP_PMU_102,
            "当前患者不可用，请重新选择");
        try {
            // Pre：Request → 发放使用行领域 Query（时间窗；状态/类型由 QS 套默认白名单）
            InpatientPatientMedicationUsageDispenseQuery dispenseQuery =
                this.inpatientPatientMedicationUsageMapper.toUsageDispenseQuery(request);
            // Pre：计划发药时间轴闭区间 + 纳入状态/发放类型下推（BR-IP-PMU-003～005）
            List<InpatientPatientMedicationUsageDispenseRow> rows =
                this.medicationDispenseQueryService.listInpatientPatientMedicationUsageDispenseRows(dispenseQuery);
            // Pre：批量挂接历史账单价；缺价可空不阻断（BR-IP-PMU-012 / BR-PRICE-SRC-001）
            Map<Long, ChargePricingView> pricingByDispenseId =
                this.chargePricingSupport.loadPricingByDispenseIds(request.getEncounterId(), rows);
            // Pre：药品定义批量 IN，禁止 N+1
            Map<String, ProductDefinitionDisplayDto> productByKey = this.loadProductMap(rows);
            // Asm：合计净额始终执行，两种模式口径一致（BR-IP-PMU-008）
            InpatientPatientMedicationUsageTotalsDto totals =
                this.inpatientPatientMedicationUsageAssembler.buildTotals(rows, pricingByDispenseId);
            List<InpatientPatientMedicationUsageDetailLineDto> detailLines = List.of();
            List<InpatientPatientMedicationUsageSummaryLineDto> summaryLines = List.of();
            // Asm：DETAIL 与 SUMMARY 互斥；仅填充当前模式行列表
            if (InpatientPharmacyPatientMedicationUsageConstants.PRESENTATION_MODE_DETAIL.equals(presentationMode)) {
                // DETAIL：一行一发放、三态映射、账单金额原样（BR-IP-PMU-006）
                detailLines = this.inpatientPatientMedicationUsageAssembler.buildDetailLines(
                    rows, productByKey, pricingByDispenseId);
                totals.setLineCount(detailLines.size());
            } else {
                // SUMMARY：按 medicationId 净数量/净金额，不输出展示发药状态（BR-IP-PMU-007）
                summaryLines = this.inpatientPatientMedicationUsageAssembler.buildSummaryLines(
                    rows, productByKey, pricingByDispenseId);
                totals.setLineCount(summaryLines.size());
            }
            // Asm：空列表允许；合计可为 0
            return this.inpatientPatientMedicationUsageMapper.toQueryResponse(
                presentationMode, detailLines, summaryLines, totals);
        } catch (BusinessException ex) {
            // Gate/Pre 已抛业务码原样上抛
            throw ex;
        } catch (RuntimeException ex) {
            // 域查询/装配未捕获异常 → EX-IP-PMU-103
            log.warn("住院患者药品使用查询失败 encounterId={}", request.getEncounterId(), ex);
            throw new BusinessException(
                InpatientPharmacyPatientMedicationUsageConstants.EX_IP_PMU_103,
                "药品使用查询失败，请稍后重试");
        }
    }

    /**
     * Gate：校验查询请求必填、日期区间与展示模式（BR-IP-PMU-001/002/201/202）。
     *
     * @param request 查询请求
     * @throws BusinessException 缺参、日期非法或模式非法
     */
    private void validateQueryUsageRequest(QueryInpatientPatientMedicationUsageRequest request) {
        // 空体无法区分日期缺失；按日期非法提示
        if (request == null) {
            throw new BusinessException(
                InpatientPharmacyPatientMedicationUsageConstants.EX_IP_PMU_202,
                "请选择有效的查询日期");
        }
        // 禁止无就诊/患者即查（BR-IP-PMU-001/011 单就诊）
        if (request.getEncounterId() == null || request.getPatientId() == null) {
            throw new BusinessException(
                InpatientPharmacyPatientMedicationUsageConstants.EX_IP_PMU_102,
                "当前患者不可用，请重新选择");
        }
        // 日期闭区间两端必填（BR-IP-PMU-002）
        if (request.getDateFrom() == null || request.getDateTo() == null) {
            throw new BusinessException(
                InpatientPharmacyPatientMedicationUsageConstants.EX_IP_PMU_202,
                "请选择有效的查询日期");
        }
        // 起止倒置拒绝，避免放大时间窗误查
        if (request.getDateFrom().isAfter(request.getDateTo())) {
            throw new BusinessException(
                InpatientPharmacyPatientMedicationUsageConstants.EX_IP_PMU_202,
                "请选择有效的查询日期");
        }
        // presentationMode 空串无法区分明细/汇总
        if (StringUtils.isEmpty(request.getPresentationMode())) {
            throw new BusinessException(
                InpatientPharmacyPatientMedicationUsageConstants.EX_IP_PMU_201,
                "查询参数错误，请刷新页面");
        }
        String mode = request.getPresentationMode().trim();
        // 仅允许 DETAIL / SUMMARY
        if (!InpatientPharmacyPatientMedicationUsageConstants.PRESENTATION_MODE_DETAIL.equals(mode)
            && !InpatientPharmacyPatientMedicationUsageConstants.PRESENTATION_MODE_SUMMARY.equals(mode)) {
            throw new BusinessException(
                InpatientPharmacyPatientMedicationUsageConstants.EX_IP_PMU_201,
                "查询参数错误，请刷新页面");
        }
    }

    /**
     * Pre：按发放行 medicationId 批量装载产品展示 Map。
     *
     * @param rows 发放使用行
     * @return 复合键 → 产品展示 DTO
     */
    private Map<String, ProductDefinitionDisplayDto> loadProductMap(
        List<InpatientPatientMedicationUsageDispenseRow> rows) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        // 遍历发放行收集药品定义复合键，供批量 hydrate
        if (rows != null) {
            for (InpatientPatientMedicationUsageDispenseRow row : rows) {
                // 无药品主键的行无法挂产品展示，跳过
                if (row == null || row.getMedicationId() == null) {
                    continue;
                }
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
                lookup.setProductDefinitionId(row.getMedicationId());
                lookups.add(lookup);
            }
        }
        // 术语展示回填已由 Assembler 内 enrich；复合键口径同 ProductDefinitionKeyUtil
        return this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }
}
