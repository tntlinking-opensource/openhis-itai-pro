/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;

import com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.zxplat.application.shared.validator.CurrentManagedWardLocationIdsResolver;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryListAssembler;
import com.zxplat.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryWriteAssembler;
import com.zxplat.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryWriteAssembler.SummaryMergeGroup;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.CreateMedicationSummaryDispenseItemDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryPatientGroupDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryPatientPickupGroupDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipHeaderDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipLineDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.mapper.NurseStationDispenseSummaryMapper;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationCancelExecuteReturnPatientGroupDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.request.CancelMedicationSummaryRequest;
import com.zxplat.application.inpatient.nursestation.dispensesummary.request.CreateMedicationSummaryRequest;
import com.zxplat.application.inpatient.nursestation.dispensesummary.request.GetMedicationSummarySlipDetailRequest;
import com.zxplat.application.inpatient.nursestation.dispensesummary.request.GetMedicationSummarySlipPatientDetailRequest;
import com.zxplat.application.inpatient.nursestation.dispensesummary.request.ListCancelExecuteReturnMedicineLinesRequest;
import com.zxplat.application.inpatient.nursestation.dispensesummary.request.PageMedicationSummarySlipHeadersRequest;
import com.zxplat.application.inpatient.nursestation.dispensesummary.request.PagePendingDispenseSummaryLinesRequest;
import com.zxplat.application.inpatient.nursestation.dispensesummary.request.SubmitMedicationSummaryRequest;
import com.zxplat.application.inpatient.nursestation.dispensesummary.response.CancelMedicationSummaryResponse;
import com.zxplat.application.inpatient.nursestation.dispensesummary.response.CreateMedicationSummaryResponse;
import com.zxplat.application.inpatient.nursestation.dispensesummary.response.GetMedicationSummarySlipDetailResponse;
import com.zxplat.application.inpatient.nursestation.dispensesummary.response.GetMedicationSummarySlipPatientDetailResponse;
import com.zxplat.application.inpatient.nursestation.dispensesummary.response.ListCancelExecuteReturnMedicineLinesResponse;
import com.zxplat.application.inpatient.nursestation.dispensesummary.response.PageMedicationSummarySlipHeadersResponse;
import com.zxplat.application.inpatient.nursestation.dispensesummary.response.PagePendingDispenseSummaryLinesResponse;
import com.zxplat.application.inpatient.nursestation.dispensesummary.response.SubmitMedicationSummaryResponse;
import com.zxplat.application.shared.encounter.EncounterContextReader;
import com.zxplat.application.shared.nursestation.InpatientNurseStationAnesthesiaApplyScopeSupport;
import com.zxplat.application.inpatient.pharmacy.dispense.support.InpatientMedicationDispenseChargePricingLoader;
import com.zxplat.application.inpatient.pharmacy.summarydispense.support.MedicationSummaryApplicantDepartmentResolver;
import com.zxplat.application.inpatient.pharmacy.summarydispense.support.MedicationSummarySlipLoader;
import com.zxplat.application.shared.option.FrequencyOptionLoader;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.clinical.procedure.entity.Procedure;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.medication.medicationdispense.command.SummarizeMedicationDispensesCommand;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.query.MedicationDispensePickupPendingPageQuery;
import com.zxplat.domain.medication.medicationdispense.queries.model.MedicationDispensePickupPendingQueryModel;
import com.zxplat.domain.medication.medicationdispense.queries.model.MedicationDispenseSummaryItemQueryModel;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.medication.medicationrequest.entity.MedicationRequest;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.enums.EventStatus;
import com.zxplat.domain.share.enums.MedicationDispenseType;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipLineQueryModel;
import com.zxplat.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipQueryModel;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

/**
 * 住院护士站汇总领药应用服务（AS-IP-NUR-010，IF-IP-SUM-01～05、08～10）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li><b>IF-IP-SUM-01</b>：待汇总明细分页 + 就诊/医嘱/时间点分组展示（排除出院带药，**BR-IP-SUM-016**；
 *       不可拆口服长嘱执行已整包装落一行，列表按截止过滤即可）</li>
 *   <li><b>IF-IP-SUM-02/03</b>：已生成汇总单头/合并明细查询（列表与详情均按<strong>管辖病区</strong>收窄，
 *       发放行 encounter→WARD 轨迹反查 summaryNo；不改按药房拆单）</li>
 *   <li><b>IF-IP-SUM-09</b>：摆药单查询（按患者分组，同 medReqId 合并数量）</li>
 *   <li><b>IF-IP-SUM-10</b>：按患者只读查看<strong>取消执行</strong>产生的退药单（REFUND + CANCEL Procedure；
 *       不含药房确认写；写路径仍在 **AS-IP-NUR-012** / **AS-IP-PHA-003**）</li>
 *   <li><b>IF-IP-SUM-04</b>：按药房拆单、itemId+单价合并、拆零写 SupplyRequest（PENDING_APPROVAL）+ 发放行 SUMMARIZED；拒绝出院带药</li>
 *   <li><b>IF-IP-SUM-05</b>：汇总 Tab 发送 → submitApproval（APPROVAL）</li>
 *   <li><b>IF-IP-SUM-08</b>：待发送汇总单撤销 → 释放发放行 + 逻辑删除 Supply 汇总单</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/nurseStationDispenseSummary/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationDispenseSummaryAppService {

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 写路径医务人员/applicantId 解析。 */
    /** 护士站管辖病区收窄（BR-IP-SUM-001）。 */
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;
    /** 当前登录科室（汇总领药单申领科室）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    /** 科室名称。 */
    private final OrganizationQueryService organizationQueryService;
    /** 分页窗口 Gate（pageNo/pageSize 必填 IF）。 */
    private final PageWindowGate pageWindowGate;
    /** 住院就诊上下文批量加载。 */
    private final EncounterContextReader EncounterContextReader;
    /** 就诊读侧：按病区 WARD 轨迹反查 encounterId（IF-IP-SUM-02 管辖门控）。 */
    private final EncounterQueryService encounterQueryService;
    /** 药品申请执行列表（medReq 预解析）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 麻醉申请范围：默认排除麻醉药品；勾选后仅麻醉申请药品。 */
    private final InpatientNurseStationAnesthesiaApplyScopeSupport anesthesiaApplyScopeSupport;
    /** 发放读侧（待汇总分页、写前强加载）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 发放写侧（summarizeMedicationDispenses）。 */
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    /** 发放 ↔ 待结算账单只读协作（B.6.1.3）。 */
    private final InpatientMedicationDispenseChargePricingLoader inpatientMedicationDispenseChargePricingLoader;
    /** 汇总单头 SupplyRequest + SupplyDelivery 读编排（B.6.1.3）。 */
    private final MedicationSummarySlipLoader medicationSummarySlipLoader;
    /** 申领科室：病区主挂靠科室名（打印/详情同源）。 */
    private final MedicationSummaryApplicantDepartmentResolver medicationSummaryApplicantDepartmentResolver;
    /** Supply 读侧（汇总单头/明细、busNo 回读）。 */
    private final SupplyRequestQueryService supplyRequestQueryService;
    /** Supply 写侧（createSummarySupplyRequest / submitApproval / removeByBusNo）。 */
    private final SupplyRequestDomainService supplyRequestDomainService;
    /** 位置名称展示。 */
    private final LocationQueryService locationQueryService;
    /** 执行记录批量读（领药时间列 recorded_time / 取消执行 CANCEL）。 */
    private final ProcedureQueryService procedureQueryService;
    /** 收费明细只读（退药总价回显，含软删）。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 医务人员姓名（取消执行人）。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 药品目录批量读侧（展示 + unitBands）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** 用法等术语码表。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 频次主数据码表。 */
    private final FrequencyOptionLoader frequencyOptionLoader;
    /** Request/Query/Command/Response 映射。 */
    private final NurseStationDispenseSummaryMapper nurseStationDispenseSummaryMapper;
    /** 待汇总/汇总 Tab 列表分组装配。 */
    private final NurseStationDispenseSummaryListAssembler nurseStationDispenseSummaryListAssembler;
    /** 写路径分组合并与拆零数量换算。 */
    private final NurseStationDispenseSummaryWriteAssembler nurseStationDispenseSummaryWriteAssembler;

    /**
     * IF-IP-SUM-01：分页查询待汇总明细。
     *
     * @param request 多选就诊、中西药 Tab、长临、截止时间
     * @return 分页壳 + 按就诊分组明细
     */
    public PagePendingDispenseSummaryLinesResponse pagePendingDispenseSummaryLines(
        PagePendingDispenseSummaryLinesRequest request) {
        try {
            // Gate ①：encounterIds、medicineCategoryTab、therapyFilter、pageNo/pageSize
            this.assertPendingLinesRequestGate(request);
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
            // Gate ②：管理病区
            List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
            List<Long> requestedEncounterIds = this.distinctIds(request.getEncounterIds());
            // Gate ③：可见 encounter 收窄（BR-IP-SUM-001）
            InpatientEncounterPageQuery visibilityQuery = nurseStationDispenseSummaryMapper.toInpatientEncounterPageQuery(
                requestedEncounterIds, managedWardIds);
            List<InpatientEncounterContextQueryModel> visibleRows =
                EncounterContextReader.listInpatientEncounterContexts(visibilityQuery);
            List<Long> visibleEncounterIds = this.retainRequestedVisibleEncounterIds(requestedEncounterIds, visibleRows);
            if (visibleEncounterIds.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_101,
                    "请先选择患者或无权访问");
            }
            Map<Long, InpatientEncounterContextQueryModel> encounterById =
                this.indexEncounters(visibleRows, visibleEncounterIds);
            // Pre ④：护士站执行药嘱列表（VERIFIED + STOPPED）
            String therapyEnum = nurseStationDispenseSummaryMapper.resolveTherapyEnum(request.getTherapyFilter());
            List<MedicationRequestListQueryModel> executeOrders =
                medicationRequestQueryService.listInpatientNurseStationExecuteOrders(visibleEncounterIds, therapyEnum);
            // Pre ⑤：按 tcmFlag 收窄 medReqIds，并剔除出院带药（BR-IP-SUM-016）
            List<Long> resolvedMedReqIds = nurseStationDispenseSummaryListAssembler.resolveMedReqIds(
                executeOrders, request.getMedicineCategoryTab());
            Map<Long, MedicationRequestListQueryModel> medReqById = this.indexMedReqById(executeOrders);
            // Pre ⑤b：麻醉申请范围——仅 AA 前缀；默认排除，勾选后仅保留
            resolvedMedReqIds = anesthesiaApplyScopeSupport.filterMedReqIdsByAnesthesiaApplyScope(
                resolvedMedReqIds, medReqById, request.getAnesthesiaApplyOnly());
            // Pre ⑥⑦：域分页待汇总发放行（默认可拆：planned ≤ queryDeadline）
            Page<MedicationDispensePickupPendingQueryModel> page =
                medicationDispenseQueryService.pageMedicationDispensePickupPending(
                    nurseStationDispenseSummaryMapper.toPickupPendingPageQuery(
                        request, visibleEncounterIds, resolvedMedReqIds));
            // Pre ⑦b：跨聚合只读回填账单单价（PENDING_SETTLEMENT）
            inpatientMedicationDispenseChargePricingLoader.applyPendingSettlementUnitPricesToPickupPendingRows(
                page.getContent());
            // Pre ⑧：药品展示批量加载（执行已按整包装落一行时，列表仅截止过滤，不再整盒展多点补齐）
            Map<String, ProductDefinitionDisplayDto> productByKey =
                productDefinitionDisplayTerminologyAssembler.loadAsMap(
                    this.collectProductLookupsFromPendingLines(page.getContent(), List.of()));
            // Pre ⑨：药房展示批量加载
            Set<Long> locationIds = this.collectLocationIdsFromPendingPage(page);
            Map<Long, Location> locationById = nurseStationDispenseSummaryListAssembler.indexLocations(
                this.loadLocations(locationIds));
            // Pre ⑨b：批量挂接 Procedure.recorded_time（领药时间列展示；勾选仍锚 plannedDispenseTime）
            Map<Long, LocalDateTime> recordedTimeByProcedureId =
                this.indexRecordedTimeByProcedureIdFromPendingPage(page);
            // Asm ⑩：就诊→医嘱→时间点分组
            List<NurseStationDispenseSummaryPatientGroupDto> groups =
                nurseStationDispenseSummaryListAssembler.buildPatientGroups(
                    page, medReqById, productByKey, encounterById, locationById, recordedTimeByProcedureId);
            // Asm ⑪：分页响应
            return nurseStationDispenseSummaryMapper.toPagePendingDispenseSummaryLinesResponse(page, groups);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("pagePendingDispenseSummaryLines failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_102,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-SUM-10：按患者查询取消执行产生的退药清单（只读）。
     *
     * <p>口径（**BR-IP-SUM-019**）：仅 {@code dispense_enum=REFUND} 且状态为待退/已退、
     * 且 {@code procedure_id} 挂接 {@link EventStatus#CANCEL} 的发放行——即 **AS-IP-NUR-012**
     * 取消已核发执行写出的退药单。不展示药房确认按钮（确认仍在 **AS-IP-PHA-003**）。</p>
     *
     * @param request 已选就诊 + 中西药 Tab
     * @return 按就诊分组的退药行
     */
    public ListCancelExecuteReturnMedicineLinesResponse listCancelExecuteReturnMedicineLines(
        ListCancelExecuteReturnMedicineLinesRequest request) {
        try {
            // Gate ①：须选就诊且中西药 Tab 合法（EX-IP-SUM-101）
            this.assertCancelExecuteReturnLinesRequestGate(request);
            // Gate ②：管辖病区可见 encounter（BR-IP-SUM-001）
            List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
            List<Long> requestedEncounterIds = this.distinctIds(request.getEncounterIds());
            InpatientEncounterPageQuery visibilityQuery = nurseStationDispenseSummaryMapper.toInpatientEncounterPageQuery(
                requestedEncounterIds, managedWardIds);
            List<InpatientEncounterContextQueryModel> visibleRows =
                EncounterContextReader.listInpatientEncounterContexts(visibilityQuery);
            List<Long> visibleEncounterIds = this.retainRequestedVisibleEncounterIds(requestedEncounterIds, visibleRows);
            if (visibleEncounterIds.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_101,
                    "请先选择患者或无权访问");
            }
            Map<Long, InpatientEncounterContextQueryModel> encounterById =
                this.indexEncounters(visibleRows, visibleEncounterIds);
            // Pre ③：域侧收敛 REFUND + 待退/已退 + procedure_id 非空
            List<MedicationDispense> refundCandidates =
                medicationDispenseQueryService.listCancelExecuteRefundDispensesByEncounterIds(visibleEncounterIds);
            // Pre ④：仅保留挂接 CANCEL 执行终态的行（排除非取消执行路径）
            Map<Long, Procedure> cancelProcedureById = new HashMap<>();
            List<MedicationDispense> cancelExecuteRefunds =
                this.retainCancelProcedureRefundDispenses(refundCandidates, cancelProcedureById);
            // Pre ⑤：按中西药 Tab 收窄（对齐药嘱 tcmFlag）
            List<MedicationDispense> categoryFiltered =
                this.filterRefundDispensesByMedicineCategory(cancelExecuteRefunds, request.getMedicineCategoryTab());
            // Pre ⑥：药品目录 + 药房 + 取消执行人姓名 + 原账单总价
            Map<String, ProductDefinitionDisplayDto> productByKey =
                productDefinitionDisplayTerminologyAssembler.loadAsMap(
                    this.collectProductLookupsFromDispenses(categoryFiltered));
            Set<Long> locationIds = this.collectLocationIdsFromDispenses(categoryFiltered);
            Map<Long, Location> locationById = nurseStationDispenseSummaryListAssembler.indexLocations(
                this.loadLocations(locationIds));
            Map<Long, String> cancelOperatorNameById =
                this.indexCancelOperatorNames(cancelProcedureById, categoryFiltered);
            Map<Long, BigDecimal> totalPriceByDispenseId =
                this.mapCancelExecuteReturnTotalPriceByRefundDispenseId(categoryFiltered, cancelProcedureById);
            // Asm ⑦：按就诊（单患者）分组
            List<NurseStationCancelExecuteReturnPatientGroupDto> groups =
                nurseStationDispenseSummaryListAssembler.buildCancelExecuteReturnPatientGroups(
                    categoryFiltered,
                    encounterById,
                    productByKey,
                    locationById,
                    cancelProcedureById,
                    cancelOperatorNameById,
                    totalPriceByDispenseId);
            ListCancelExecuteReturnMedicineLinesResponse response =
                new ListCancelExecuteReturnMedicineLinesResponse();
            response.setPatientGroups(groups);
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("listCancelExecuteReturnMedicineLines failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_102,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-SUM-02：分页查询汇总单头。
     *
     * <p>仅返回发放行就诊落在当前操作者<strong>管辖病区</strong>内的汇总单（BR-IP-SUM-001）；
     * 与按药房拆单无关，不混入他病区护士生成的单。</p>
     *
     * @param request 药房、时段、关键字、分页
     * @return 汇总单头分页
     */
    public PageMedicationSummarySlipHeadersResponse pageMedicationSummarySlipHeaders(
        PageMedicationSummarySlipHeadersRequest request) {
        try {
            // Gate ①：分页参数
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientNurseStationConstants.EX_IP_SUM_102,
                "加载失败，请稍后重试");
            // Pre ②：管辖病区 → 就诊 → summaryNo 白名单（无管辖病区则空页）
            List<String> managedWardSummaryNos = this.resolveManagedWardVisibleSummaryNos();
            // 岗位无可见病区或病区下无汇总发放行：直接空页，禁止回落全院 type 27
            if (managedWardSummaryNos.isEmpty()) {
                return nurseStationDispenseSummaryMapper.toPageMedicationSummarySlipHeadersResponse(
                    Page.empty(), new ArrayList<>());
            }
            // Pre ③：仅当前端显式指定药房时收窄 purposeLocationIds
            List<Long> purposeLocationIds = this.resolveSummaryPurposeLocationIds(request);
            // Pre ④：Supply 列表 Query + 管辖病区 busNoIn 门控
            SupplyRequestDocumentListQuery listQuery = nurseStationDispenseSummaryMapper.toSupplyRequestDocumentListQuery(
                request, purposeLocationIds);
            listQuery.setBusNoIn(managedWardSummaryNos);
            // Pre ⑤：域分页汇总单头
            Page<MedicationSummarySlipQueryModel> page =
                medicationSummarySlipLoader.pageMedicationSummarySlips(listQuery);
            // Asm ⑥：单头 DTO + 展示 enrichment
            List<NurseStationDispenseSummarySlipHeaderDto> rows =
                nurseStationDispenseSummaryMapper.toSlipHeaderDtos(page);
            // Asm ⑥b：列表回填申领科室（病区所属科室，BR-IP-SUM-020；当前页规模）
            this.enrichSlipHeadersApplicantDepartment(rows);
            // Asm ⑦：分页响应
            return nurseStationDispenseSummaryMapper.toPageMedicationSummarySlipHeadersResponse(page, rows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("pageMedicationSummarySlipHeaders failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_102,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-SUM-03：查询汇总单合并明细。
     *
     * @param request summaryNo
     * @return 单头 + 合并明细（无批号）
     */
    public GetMedicationSummarySlipDetailResponse getMedicationSummarySlipDetail(
        GetMedicationSummarySlipDetailRequest request) {
        try {
            // Gate ①：summaryNo 非空
            if (request == null || StringUtils.isEmpty(request.getSummaryNo())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_103,
                    "汇总单不存在或已失效，请刷新");
            }
            String summaryNo = request.getSummaryNo().trim();
            // Gate ②：须落在管辖病区可见汇总单集合（与 IF-02 同口径，防越权读他病区单）
            this.assertSummaryNoVisibleUnderManagedWards(summaryNo);
            // Pre ③：合并明细行
            List<MedicationSummarySlipLineQueryModel> domainLines =
                supplyRequestQueryService.listMedicationSummarySlipLinesByBusNo(summaryNo);
            if (domainLines.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_103,
                    "汇总单不存在或已失效，请刷新");
            }
            // Pre ③b：跨聚合只读累加单价/总价（BR-IP-SUM-009）
            inpatientMedicationDispenseChargePricingLoader.enrichSummarySlipLineChargePricing(summaryNo, domainLines);
            // Pre ④：单头（复用 IF-02 装配）+ 申领科室（病区所属科室，BR-IP-SUM-020）
            NurseStationDispenseSummarySlipHeaderDto header = this.loadSlipHeaderBySummaryNo(summaryNo);
            this.enrichSlipHeaderApplicantDepartment(header, summaryNo);
            // Pre：申领人登录科室（requestingDepartmentName，与病区所属科室并存）
            header.setRequestingDepartmentName(this.resolveApplicantLoginDepartmentName(
                header.getApplicantId(), header.getConsumingOrganizationId()));
            // Pre ④：药品展示批量加载
            Map<String, ProductDefinitionDisplayDto> productByKey =
                productDefinitionDisplayTerminologyAssembler.loadAsMap(this.collectProductLookupsFromSlipLines(domainLines));
            // Asm ⑤：合并行 DTO（按拼音首字母序，BR-IP-SUM-020）
            List<NurseStationDispenseSummarySlipLineDto> lines =
                nurseStationDispenseSummaryListAssembler.buildSlipLineDtos(domainLines, productByKey);
            // Asm ⑥：详情响应
            return nurseStationDispenseSummaryMapper.toGetMedicationSummarySlipDetailResponse(summaryNo, header, lines);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("getMedicationSummarySlipDetail failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_102,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-SUM-09：查询汇总单摆药明细（按患者分组，同 medReqId 合并数量）。
     *
     * <p>与 IF-IP-SUM-03 的差异：03 按药品+单价合并看整单领药量；本接口回答「这张汇总单领回来的药分别给了哪些患者」，
     * 数量展示口径与 03 一致（{@code displayQuantityText} 操作/行单位直显，BR-IP-SUM-015）。</p>
     *
     * @param request summaryNo
     * @return 单头 + 患者分组摆药行
     */
    public GetMedicationSummarySlipPatientDetailResponse getMedicationSummarySlipPatientDetail(
        GetMedicationSummarySlipPatientDetailRequest request) {
        try {
            // Gate ①：summaryNo 非空
            if (request == null || StringUtils.isEmpty(request.getSummaryNo())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_103,
                    "汇总单不存在或已失效，请刷新");
            }
            String summaryNo = request.getSummaryNo().trim();
            // Gate ②：须落在管辖病区可见汇总单集合（与 IF-02 同口径）
            this.assertSummaryNoVisibleUnderManagedWards(summaryNo);
            // Pre ③：汇总单号下发放行（含 medReqId / 剂量用法频次）
            List<MedicationDispenseSummaryItemQueryModel> dispenseRows =
                medicationDispenseQueryService.listMedicationDispensesBySummaryNo(summaryNo);
            if (dispenseRows.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_103,
                    "汇总单不存在或已失效，请刷新");
            }
            // Pre ④：单头（复用 IF-02 装配；顺带校验 type 27 单仍存在）+ 申领科室
            NurseStationDispenseSummarySlipHeaderDto header = this.loadSlipHeaderBySummaryNo(summaryNo);
            // Pre ④：就诊上下文（床号/姓名）+ 病房（房间）名
            List<Long> encounterIds = this.collectEncounterIdsFromSummaryItems(dispenseRows);
            header.setApplicantDepartmentName(
                medicationSummaryApplicantDepartmentResolver.resolveByEncounterIds(encounterIds));
            Map<Long, InpatientEncounterContextQueryModel> encounterById =
                this.loadEncounterContextByIds(encounterIds);
            Map<Long, String> houseNameByEncounterId = this.resolveHouseNameByEncounterId(encounterById);
            // Pre ⑤：药品申请（剂量/用法/频次真源；发放行常未落这些字段）
            Map<Long, MedicationRequestListQueryModel> medReqById =
                this.indexMedReqById(medicationRequestQueryService.listMedicationRequestListModelsByIds(
                    this.collectMedReqIdsFromSummaryItems(dispenseRows)));
            // Pre ⑥：药品展示 + 用法/频次/剂量单位码表
            Map<String, ProductDefinitionDisplayDto> productByKey =
                productDefinitionDisplayTerminologyAssembler.loadAsMap(
                    this.collectProductLookupsFromSummaryItems(dispenseRows));
            Map<String, String> methodLabelByCode =
                terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
            Map<String, String> rateLabelByCode = frequencyOptionLoader.buildRateLabelMap();
            Map<String, String> doseUnitLabelByCode =
                terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
            // Asm ⑦：按患者 + medReqId 合并，回填剂量用法频次与病房床号
            List<NurseStationDispenseSummaryPatientPickupGroupDto> patientGroups =
                nurseStationDispenseSummaryListAssembler.buildPatientPickupGroups(
                    dispenseRows,
                    productByKey,
                    encounterById,
                    houseNameByEncounterId,
                    medReqById,
                    methodLabelByCode,
                    rateLabelByCode,
                    doseUnitLabelByCode);
            // Asm ⑧：响应
            return nurseStationDispenseSummaryMapper.toGetMedicationSummarySlipPatientDetailResponse(
                summaryNo, header, patientGroups);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("getMedicationSummarySlipPatientDetail failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_102,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-SUM-04：汇总保存（唯一写路径之一；同事务整批 BR-IP-SUM-012）。
     *
     * @param request 勾选发放行
     * @return 汇总单号列表
     */
    public CreateMedicationSummaryResponse createMedicationSummary(CreateMedicationSummaryRequest request) {
        // Gate ①：dispenseItems 1～500
        this.assertCreateSummaryRequestGate(request);
        // Gate ②：各 encounterId 可见性
        List<Long> encounterIds = this.collectEncounterIdsFromWriteRequest(request);
        this.requireVisibleEncounters(encounterIds);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_SUM_204,
            "未登录或登录已失效，无法汇总领药");
        Long applicantId = currentPractitionerResolver.requirePractitionerId(
            InpatientNurseStationConstants.EX_IP_SUM_204,
            "未解析到当前医务人员，无法汇总领药");
        List<Long> dispenseIds = this.collectDispenseIdsFromWriteRequest(request);
        try {
            // Pre ③：强加载发放实体
            List<MedicationDispense> dispenses = medicationDispenseQueryService.listMedicationDispensesByIds(dispenseIds);
            if (dispenses.size() != dispenseIds.size()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_201,
                    "所选药品已汇总，请勿重复操作");
            }
            // Pre ④：PREPARATION 且无 summaryNo
            this.assertAllDispensesSummarizable(dispenses);
            // Pre ④b：禁止出院带药进汇总（BR-IP-SUM-016 / BR-IP-ORD-029⑥）
            this.assertNoDischargeMedicationDispenses(dispenses);
            // Pre ⑤：ChargeItem 只读定价
            Map<Long, ChargeItem> chargeByDispenseId = this.loadChargeByDispenseId(request, dispenses);
            // Pre ⑥：药品 unitBands 源
            Map<String, ProductDefinitionDisplayDto> productByMedId =
                productDefinitionDisplayTerminologyAssembler.loadAsMap(this.collectProductLookupsFromDispenses(dispenses));
            // Pre ⑦：按药房+单价分组合并
            Map<Long, List<SummaryMergeGroup>> groupsByLocation =
                nurseStationDispenseSummaryWriteAssembler.buildMergeGroupsByLocationAndPrice(
                    dispenses, chargeByDispenseId, productByMedId);
            // Pre ⑧：unitBands 校验
            for (List<SummaryMergeGroup> groups : groupsByLocation.values()) {
                for (SummaryMergeGroup group : groups) {
                    String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                        TableNameConstants.MED_MEDICATION_DEFINITION, group.getMedicationId());
                    ProductDefinitionDisplayDto product = productByMedId.get(productKey);
                    nurseStationDispenseSummaryWriteAssembler.validateMergeGroupUnitBands(group, product);
                }
            }

            List<String> summaryNos = new ArrayList<>();
            // Cmd ⑨：循环每个药房组（9a～9b）
            for (Map.Entry<Long, List<SummaryMergeGroup>> entry : groupsByLocation.entrySet()) {
                Long locationId = entry.getKey();
                List<SummaryMergeGroup> locationGroups = entry.getValue();
                List<Long> locationDispenseIds = this.collectDispenseIdsFromGroups(locationGroups);
                // Cmd 9a～9b：预分配 MSO 单号 → 发放 summary_no 与 Supply bus_no 同源（AS-IP-NUR-010 §2.1）
                String summaryNo = supplyRequestDomainService.allocateBusNoForType(
                    SupplyType.MEDICATION_SUMMARY_ORDER.getCode());
                this.summarizeMedicationDispensesOrThrow201(
                    nurseStationDispenseSummaryMapper.toSummarizeMedicationDispensesCommand(
                        locationId, locationDispenseIds, operatorId, summaryNo));
                // Cmd 9b：SupplyRequest type 27（初态 PENDING_APPROVAL；不创建 SupplyDelivery）
                supplyRequestDomainService.createSummarySupplyRequest(
                    nurseStationDispenseSummaryMapper.toCreateSummarySupplyRequestCommand(
                        summaryNo,
                        locationGroups,
                        applicantId,
                        operatorId,
                        managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery()));
                summaryNos.add(summaryNo);
            }
            // Asm ⑩：写响应
            return nurseStationDispenseSummaryMapper.toCreateMedicationSummaryResponse(summaryNos);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("createMedicationSummary failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_204,
                "汇总保存失败，请检查所选明细状态");
        }
    }

    /**
     * IF-IP-SUM-05：发送汇总领药单（submitApproval → APPROVAL）。
     *
     * @param request 汇总单号列表
     * @return 已发送汇总单号
     */
    public SubmitMedicationSummaryResponse submitMedicationSummary(SubmitMedicationSummaryRequest request) {
        this.assertSubmitSummaryRequestGate(request);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_SUM_205,
            "未登录或登录已失效，无法发送汇总单");
        Long applicantId = currentPractitionerResolver.requirePractitionerId(
            InpatientNurseStationConstants.EX_IP_SUM_205,
            "未解析到当前医务人员，无法发送汇总单");
        List<String> summaryNos = this.distinctSummaryNos(request.getSummaryNos());
        try {
            // Gate：一次解析管辖病区可见单号，批量发送均须落在集合内
            Set<String> visibleSummaryNos = new HashSet<>(this.resolveManagedWardVisibleSummaryNos());
            for (String summaryNo : summaryNos) {
                this.assertSummaryNoInVisibleSet(summaryNo, visibleSummaryNos);
                this.assertMedicationSummarySlipSubmittable(summaryNo);
                supplyRequestDomainService.submitApproval(
                    nurseStationDispenseSummaryMapper.toSubmitApprovalCommand(
                        summaryNo,
                        applicantId,
                        operatorId,
                        managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery()));
            }
            return nurseStationDispenseSummaryMapper.toSubmitMedicationSummaryResponse(summaryNos);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("submitMedicationSummary failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_205,
                "发送失败，请检查汇总单状态");
        }
    }

    /**
     * IF-IP-SUM-08：撤销待发送汇总领药单（释放发放行 + 逻辑删除 Supply 汇总单）。
     *
     * <p>仅 {@link SupplyStatus#PENDING_APPROVAL} 可撤销；已发送/已驳回/已核发单拒绝。</p>
     *
     * @param request 汇总单号列表
     * @return 已撤销汇总单号
     */
    public CancelMedicationSummaryResponse cancelMedicationSummary(CancelMedicationSummaryRequest request) {
        this.assertCancelSummaryRequestGate(request);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_SUM_206,
            "未登录或登录已失效，无法撤销汇总单");
        List<String> summaryNos = this.distinctSummaryNos(request.getSummaryNos());
        try {
            // Gate：一次解析管辖病区可见单号，批量撤销均须落在集合内
            Set<String> visibleSummaryNos = new HashSet<>(this.resolveManagedWardVisibleSummaryNos());
            for (String summaryNo : summaryNos) {
                this.assertSummaryNoInVisibleSet(summaryNo, visibleSummaryNos);
                // Pre：仅待发送（PENDING_APPROVAL）type 27 汇总单允许撤销
                this.assertMedicationSummarySlipCancellable(summaryNo);
                // Cmd ①：发放行 SUMMARIZED → PREPARATION，清 summary_no
                medicationDispenseDomainService.cancelMedicationDispenseSummary(
                    nurseStationDispenseSummaryMapper.toCancelMedicationDispenseSummaryCommand(summaryNo, operatorId));
                // Cmd ②：同 bus_no 汇总 Supply 行逻辑删除
                supplyRequestDomainService.removeByBusNo(
                    nurseStationDispenseSummaryMapper.toRemoveByBusNoCommand(summaryNo, operatorId));
            }
            return nurseStationDispenseSummaryMapper.toCancelMedicationSummaryResponse(summaryNos);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("cancelMedicationSummary failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_206,
                "撤销失败，仅待发送汇总单可撤销");
        }
    }

    /**
     * 待发药汇总明细分页请求门禁：须选就诊、合法药品分类 Tab 与长临筛选项（EX-IP-SUM-101）。
     *
     * @param request 分页请求
     * @throws BusinessException EX-IP-SUM-101 参数无效或无权
     */
    private void assertPendingLinesRequestGate(PagePendingDispenseSummaryLinesRequest request) {
        if (request == null
            || request.getEncounterIds() == null
            || request.getEncounterIds().isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
        }
        String tab = trimToEmpty(request.getMedicineCategoryTab());
        if (!InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_WEST.equals(tab)
            && !InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_TCM.equals(tab)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
        }
        String therapy = trimToEmpty(request.getTherapyFilter());
        if (!this.isAllowedTherapyFilter(therapy)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
        }
    }

    /**
     * 取消执行退药清单请求门禁：须选就诊且中西药 Tab 合法（EX-IP-SUM-101）。
     *
     * @param request IF-IP-SUM-10 请求
     * @throws BusinessException EX-IP-SUM-101 参数无效
     */
    private void assertCancelExecuteReturnLinesRequestGate(ListCancelExecuteReturnMedicineLinesRequest request) {
        if (request == null
            || request.getEncounterIds() == null
            || request.getEncounterIds().isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
        }
        String tab = trimToEmpty(request.getMedicineCategoryTab());
        if (!InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_WEST.equals(tab)
            && !InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_TCM.equals(tab)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
        }
    }

    /**
     * 仅保留挂接 CANCEL 执行终态的退药发放行（BR-IP-SUM-019 / BR-IP-CXL-010）。
     *
     * @param candidates             域侧 REFUND 候选行
     * @param cancelProcedureByIdOut 输出：CANCEL Procedure 索引（供取消人/时间点/总价反查）
     * @return 取消执行退药行；无命中时空列表
     */
    private List<MedicationDispense> retainCancelProcedureRefundDispenses(
        List<MedicationDispense> candidates,
        Map<Long, Procedure> cancelProcedureByIdOut) {
        List<MedicationDispense> retained = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            return retained;
        }
        Set<Long> procedureIds = new HashSet<>();
        for (MedicationDispense dispense : candidates) {
            if (dispense != null && dispense.getProcedureId() != null) {
                procedureIds.add(dispense.getProcedureId());
            }
        }
        if (procedureIds.isEmpty()) {
            return retained;
        }
        Map<Long, Procedure> procedureById = new HashMap<>();
        for (Procedure procedure : procedureQueryService.listByIds(procedureIds)) {
            if (procedure != null && procedure.getId() != null) {
                procedureById.put(procedure.getId(), procedure);
            }
        }
        for (MedicationDispense dispense : candidates) {
            if (dispense == null || dispense.getProcedureId() == null) {
                continue;
            }
            Procedure procedure = procedureById.get(dispense.getProcedureId());
            // 无 CANCEL 终态 → 不是取消执行写出的退药单，剔除
            if (procedure == null) {
                continue;
            }
            if (!EventStatus.CANCEL.getCode().equals(procedure.getStatusEnum())) {
                continue;
            }
            retained.add(dispense);
            if (cancelProcedureByIdOut != null) {
                cancelProcedureByIdOut.put(procedure.getId(), procedure);
            }
        }
        return retained;
    }

    /**
     * 批量解析取消执行人姓名（CANCEL.recorder_id → Practitioner.name）。
     *
     * @param cancelProcedureById CANCEL 索引
     * @param refundDispenses     退药行（用于收窄用到的 CANCEL）
     * @return recorderId → 姓名
     */
    private Map<Long, String> indexCancelOperatorNames(
        Map<Long, Procedure> cancelProcedureById,
        List<MedicationDispense> refundDispenses) {
        Map<Long, String> nameById = new HashMap<>();
        if (cancelProcedureById == null || cancelProcedureById.isEmpty()
            || refundDispenses == null || refundDispenses.isEmpty()) {
            return nameById;
        }
        Set<Long> recorderIds = new HashSet<>();
        for (MedicationDispense dispense : refundDispenses) {
            if (dispense == null || dispense.getProcedureId() == null) {
                continue;
            }
            Procedure cancelProcedure = cancelProcedureById.get(dispense.getProcedureId());
            if (cancelProcedure != null && cancelProcedure.getRecorderId() != null) {
                recorderIds.add(cancelProcedure.getRecorderId());
            }
        }
        if (recorderIds.isEmpty()) {
            return nameById;
        }
        for (Practitioner practitioner : practitionerQueryService.findAllByIds(recorderIds)) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            nameById.put(practitioner.getId(), practitioner.getName());
        }
        return nameById;
    }

    /**
     * 退药行 → 原 COMPLETED 槽位账单总价（绝对值；含取消后软删 ChargeItem）。
     *
     * <p>路径：退药.procedureId → CANCEL.refundId（原 COMPLETED）→ 同 medReqId 的原正向发放
     * → 该发放锚点上的 ChargeItem.total_price 求和。</p>
     *
     * @param refundDispenses     取消执行退药行
     * @param cancelProcedureById CANCEL 索引
     * @return 退药 dispenseId → 总价
     */
    private Map<Long, BigDecimal> mapCancelExecuteReturnTotalPriceByRefundDispenseId(
        List<MedicationDispense> refundDispenses,
        Map<Long, Procedure> cancelProcedureById) {
        Map<Long, BigDecimal> totalByRefundDispenseId = new HashMap<>();
        if (refundDispenses == null || refundDispenses.isEmpty()
            || cancelProcedureById == null || cancelProcedureById.isEmpty()) {
            return totalByRefundDispenseId;
        }
        Set<Long> completedProcedureIds = new HashSet<>();
        for (MedicationDispense refund : refundDispenses) {
            if (refund == null || refund.getProcedureId() == null) {
                continue;
            }
            Procedure cancelProcedure = cancelProcedureById.get(refund.getProcedureId());
            if (cancelProcedure != null && cancelProcedure.getRefundId() != null) {
                completedProcedureIds.add(cancelProcedure.getRefundId());
            }
        }
        if (completedProcedureIds.isEmpty()) {
            return totalByRefundDispenseId;
        }
        // Pre：原 COMPLETED 槽位正向发放（按 procedure_id）
        List<MedicationDispense> originDispenses =
            medicationDispenseQueryService.listMedicationDispensesByProcedureIds(completedProcedureIds);
        Map<String, Long> originDispenseIdByCompletedProcAndMedReq = new HashMap<>();
        for (MedicationDispense origin : originDispenses) {
            if (origin == null || origin.getId() == null || origin.getProcedureId() == null) {
                continue;
            }
            // 退药对冲行本身也可能挂在 CANCEL；此处只要正向行（非 REFUND）
            if (MedicationDispenseType.REFUND.equals(origin.getDispenseEnum())) {
                continue;
            }
            String key = origin.getProcedureId() + "#" + origin.getMedReqId();
            originDispenseIdByCompletedProcAndMedReq.put(key, origin.getId());
        }
        // Pre：含软删账单（取消执行已软删 ORDER_EXECUTE）
        List<ChargeItem> charges =
            chargeItemQueryService.listChargeItemsByProcedureIdsIncludingDeleted(completedProcedureIds);
        Map<Long, BigDecimal> totalByOriginDispenseId = new HashMap<>();
        for (ChargeItem charge : charges) {
            if (charge == null || charge.getDispenseId() == null || charge.getTotalPrice() == null) {
                continue;
            }
            BigDecimal absTotal = charge.getTotalPrice().abs();
            BigDecimal prior = totalByOriginDispenseId.get(charge.getDispenseId());
            if (prior == null) {
                totalByOriginDispenseId.put(charge.getDispenseId(), absTotal);
            } else {
                totalByOriginDispenseId.put(charge.getDispenseId(), prior.add(absTotal));
            }
        }
        for (MedicationDispense refund : refundDispenses) {
            if (refund == null || refund.getId() == null || refund.getProcedureId() == null) {
                continue;
            }
            Procedure cancelProcedure = cancelProcedureById.get(refund.getProcedureId());
            if (cancelProcedure == null || cancelProcedure.getRefundId() == null) {
                continue;
            }
            String key = cancelProcedure.getRefundId() + "#" + refund.getMedReqId();
            Long originDispenseId = originDispenseIdByCompletedProcAndMedReq.get(key);
            if (originDispenseId == null) {
                continue;
            }
            BigDecimal total = totalByOriginDispenseId.get(originDispenseId);
            if (total != null) {
                totalByRefundDispenseId.put(refund.getId(), total);
            }
        }
        return totalByRefundDispenseId;
    }

    /**
     * 按中西药 Tab 收窄退药行（对齐原药嘱 tcmFlag）。
     *
     * @param dispenses           已确认为取消执行退药的行
     * @param medicineCategoryTab west / tcm
     * @return 收窄后的行
     */
    private List<MedicationDispense> filterRefundDispensesByMedicineCategory(
        List<MedicationDispense> dispenses,
        String medicineCategoryTab) {
        List<MedicationDispense> filtered = new ArrayList<>();
        if (dispenses == null || dispenses.isEmpty()) {
            return filtered;
        }
        Set<Long> medReqIds = new LinkedHashSet<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense != null && dispense.getMedReqId() != null) {
                medReqIds.add(dispense.getMedReqId());
            }
        }
        Map<Long, MedicationRequest> medReqById = medicationRequestQueryService.mapEntitiesByIds(medReqIds);
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null) {
                continue;
            }
            MedicationRequest medReq = null;
            if (dispense.getMedReqId() != null) {
                medReq = medReqById.get(dispense.getMedReqId());
            }
            // 药嘱缺失或非当前中西药 Tab → 不展示
            if (!nurseStationDispenseSummaryListAssembler.matchesMedicineCategoryTab(medReq, medicineCategoryTab)) {
                continue;
            }
            filtered.add(dispense);
        }
        return filtered;
    }

    /**
     * 从发放实体收集 locationId（药房展示）。
     *
     * @param dispenses 发放行
     * @return locationId 集合
     */
    private Set<Long> collectLocationIdsFromDispenses(List<MedicationDispense> dispenses) {
        Set<Long> ids = new HashSet<>();
        if (dispenses == null || dispenses.isEmpty()) {
            return ids;
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense != null && dispense.getLocationId() != null) {
                ids.add(dispense.getLocationId());
            }
        }
        return ids;
    }

    /**
     * 创建汇总单请求门禁：明细非空、批量上限与 dispenseId/encounterId 成对（EX-IP-SUM-204）。
     *
     * @param request 创建请求
     * @throws BusinessException EX-IP-SUM-204 明细无效或超批量
     */
    private void assertCreateSummaryRequestGate(CreateMedicationSummaryRequest request) {
        if (request == null
            || request.getDispenseItems() == null
            || request.getDispenseItems().isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_204,
                "汇总申请失败，请检查所选明细状态");
        }
        if (request.getDispenseItems().size() > InpatientNurseStationConstants.SUMMARY_WRITE_BATCH_MAX) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_204,
                "汇总保存失败，请检查所选明细状态");
        }
        for (CreateMedicationSummaryDispenseItemDto item : request.getDispenseItems()) {
            if (item == null || item.getDispenseId() == null || item.getEncounterId() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_204,
                    "汇总保存失败，请检查所选明细状态");
            }
        }
    }

    /**
     * 发送汇总单请求门禁：汇总单号非空且单次不超过 50 张（EX-IP-SUM-205）。
     *
     * @param request 发送请求
     * @throws BusinessException EX-IP-SUM-205 参数无效或超批量
     */
    private void assertSubmitSummaryRequestGate(SubmitMedicationSummaryRequest request) {
        if (request == null
            || request.getSummaryNos() == null
            || request.getSummaryNos().isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_205,
                "发送失败，请至少选择一张汇总单");
        }
        if (request.getSummaryNos().size() > 50) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_205,
                "发送失败，单次最多发送 50 张汇总单");
        }
        for (String summaryNo : request.getSummaryNos()) {
            if (StringUtils.isEmpty(summaryNo)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_205,
                    "发送失败，汇总单号不能为空");
            }
        }
    }

    /**
     * 撤销汇总单请求门禁：汇总单号非空且单次不超过 50 张（EX-IP-SUM-206）。
     *
     * @param request 撤销请求
     * @throws BusinessException EX-IP-SUM-206 参数无效或超批量
     */
    private void assertCancelSummaryRequestGate(CancelMedicationSummaryRequest request) {
        if (request == null
            || request.getSummaryNos() == null
            || request.getSummaryNos().isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_206,
                "撤销失败，请至少选择一张汇总单");
        }
        if (request.getSummaryNos().size() > 50) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_206,
                "撤销失败，单次最多撤销 50 张汇总单");
        }
        for (String summaryNo : request.getSummaryNos()) {
            if (StringUtils.isEmpty(summaryNo)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_206,
                    "撤销失败，汇总单号不能为空");
            }
        }
    }

    /**
     * 汇总单须为 type=27 且 Supply 状态为待发送方可撤销（EX-IP-SUM-103/206）。
     *
     * @param summaryNo 汇总单 bus_no
     * @throws BusinessException 单不存在、非汇总类型或状态不可撤销
     */
    private void assertMedicationSummarySlipCancellable(String summaryNo) {
        SupplyByBusNoQuery query = new SupplyByBusNoQuery();
        query.setBusNo(summaryNo.trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(query);
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_103,
                "汇总单不存在或已失效，请刷新");
        }
        boolean hasType27 = false;
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.MEDICATION_SUMMARY_ORDER.getCode().equals(line.getTypeEnum())) {
                continue;
            }
            hasType27 = true;
            if (!SupplyStatus.PENDING_APPROVAL.getCode().equals(line.getStatusEnum())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_206,
                    "撤销失败，仅待发送汇总单可撤销");
            }
        }
        if (!hasType27) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_103,
                "汇总单不存在或已失效，请刷新");
        }
    }

    /**
     * 汇总单须存在且含 type=27 汇总 Supply 行方可发送（EX-IP-SUM-103）。
     *
     * @param summaryNo 汇总单 bus_no
     * @throws BusinessException 单不存在或非汇总类型
     */
    private void assertMedicationSummarySlipSubmittable(String summaryNo) {
        SupplyByBusNoQuery query = new SupplyByBusNoQuery();
        query.setBusNo(summaryNo.trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(query);
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_103,
                "汇总单不存在或已失效，请刷新");
        }
        boolean hasType27 = false;
        for (SupplyRequestModel line : lines) {
            if (line != null && SupplyType.MEDICATION_SUMMARY_ORDER.getCode().equals(line.getTypeEnum())) {
                hasType27 = true;
                break;
            }
        }
        if (!hasType27) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_103,
                "汇总单不存在或已失效，请刷新");
        }
    }

    /**
     * 待发药行须全部为 PREPARATION 且未绑定 summaryNo（EX-IP-SUM-201）。
     *
     * @param dispenses 待汇总发放行
     * @throws BusinessException 已汇总或状态不可汇总
     */
    private void assertAllDispensesSummarizable(List<MedicationDispense> dispenses) {
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_201,
                    "所选药品已汇总，请勿重复操作");
            }
            if (!DispenseStatus.PREPARATION.equals(dispense.getStatusEnum())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_201,
                    "所选药品已汇总，请勿重复操作");
            }
            if (StringUtils.isNotEmpty(dispense.getSummaryNo())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_201,
                    "所选药品已汇总，请勿重复操作");
            }
        }
    }

    /**
     * 勾选发放行不得挂接出院带药药嘱（**BR-IP-SUM-016** / **BR-IP-ORD-029⑥** → EX-IP-SUM-207）。
     *
     * <p>出院带药执行后仅走药房按患者逐行发药（AS-IP-PHA-001），禁止写入汇总领药单。</p>
     *
     * @param dispenses 待汇总发放行
     * @throws BusinessException 任一行对应 {@link TherapyType#DISCHARGE_MEDICATION}
     */
    private void assertNoDischargeMedicationDispenses(List<MedicationDispense> dispenses) {
        Set<Long> medReqIds = new HashSet<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense != null && dispense.getMedReqId() != null) {
                medReqIds.add(dispense.getMedReqId());
            }
        }
        // 无药嘱锚则无法判定 therapy；交由后续账单/合并校验拒绝
        if (medReqIds.isEmpty()) {
            return;
        }
        List<MedicationRequest> medReqs = medicationRequestQueryService.listByIds(medReqIds);
        for (MedicationRequest medReq : medReqs) {
            // 出院带药不得汇总：患者须到药房按人发药
            if (medReq != null
                && TherapyType.DISCHARGE_MEDICATION.getCode().equals(medReq.getTherapyEnum())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_207,
                    "出院带药不可汇总领药，请到药房按患者发药");
            }
        }
    }

    /**
     * 调用领域汇总发放并映射无效状态为 EX-IP-SUM-201。
     *
     * @param command 领域汇总命令
     * @return 新生成 summaryNo
     * @throws BusinessException EX-IP-SUM-201 重复汇总；其他领域码透传
     */
    private String summarizeMedicationDispensesOrThrow201(SummarizeMedicationDispensesCommand command) {
        try {
            return medicationDispenseDomainService.summarizeMedicationDispenses(command);
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_STATUS.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_201,
                    "所选药品已汇总，请勿重复操作");
            }
            throw ex;
        }
    }

    /**
     * 按就诊分组加载待发药对应待结算 ChargeItem（缺账单抛 EX-IP-SUM-203）。
     *
     * @param request   创建请求（含 encounter-dispense 对）
     * @param dispenses 已加载发放行
     * @return dispenseId → ChargeItem
     * @throws BusinessException EX-IP-SUM-203 缺执行账单
     */
    private Map<Long, ChargeItem> loadChargeByDispenseId(
        CreateMedicationSummaryRequest request,
        List<MedicationDispense> dispenses) {
        Map<Long, List<Long>> dispenseIdsByEncounter = new HashMap<>();
        for (CreateMedicationSummaryDispenseItemDto item : request.getDispenseItems()) {
            if (item == null || item.getEncounterId() == null || item.getDispenseId() == null) {
                continue;
            }
            List<Long> bucket = dispenseIdsByEncounter.get(item.getEncounterId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                dispenseIdsByEncounter.put(item.getEncounterId(), bucket);
            }
            bucket.add(item.getDispenseId());
        }
        Map<Long, ChargeItem> chargeByDispenseId =
            inpatientMedicationDispenseChargePricingLoader.mapPendingSettlementChargeByDispenseIds(
                dispenseIdsByEncounter);
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getId() == null) {
                continue;
            }
            if (!chargeByDispenseId.containsKey(dispense.getId())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_203,
                    "缺少执行账单，无法汇总，请确认医嘱已执行");
            }
        }
        return chargeByDispenseId;
    }

    /**
     * 汇总单号去重并保持请求顺序。
     *
     * @param summaryNos 原始单号列表
     * @return 去重后的单号
     */
    private List<String> distinctSummaryNos(List<String> summaryNos) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (summaryNos != null) {
            for (String summaryNo : summaryNos) {
                if (StringUtils.isNotEmpty(summaryNo)) {
                    set.add(summaryNo.trim());
                }
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 解析当前操作者管辖病区下可见的汇总单号白名单。
     *
     * <p>路径：管辖启用病区 → WARD 轨迹就诊 → 发放行 {@code summary_no} 去重。
     * 无管辖病区或病区下无汇总发放行时返回空列表（调用方须空页/拒绝，禁止回落全院）。</p>
     *
     * @return 可见汇总单号；永不为 {@code null}
     */
    private List<String> resolveManagedWardVisibleSummaryNos() {
        List<Location> managedWards = managedWardLocationIdsResolver.listManagedActiveWardsOrEmpty();
        List<Long> wardIds = new ArrayList<>();
        if (managedWards != null) {
            for (Location ward : managedWards) {
                if (ward != null && ward.getId() != null) {
                    wardIds.add(ward.getId());
                }
            }
        }
        // 无可见病区：岗位未绑定或均已停用
        if (wardIds.isEmpty()) {
            return List.of();
        }
        List<Long> encounterIds = encounterQueryService.listEncounterIdsByWardLocationIds(wardIds);
        // 管辖病区下无任何就诊轨迹：无可归属汇总单
        if (encounterIds.isEmpty()) {
            return List.of();
        }
        return medicationDispenseQueryService.listDistinctSummaryNosByEncounterIds(encounterIds);
    }

    /**
     * 断言汇总单落在管辖病区可见范围（与 IF-IP-SUM-02 同口径；越权视为单不存在）。
     *
     * @param summaryNo 汇总单号
     * @throws BusinessException EX-IP-SUM-103 不可见或不存在
     */
    private void assertSummaryNoVisibleUnderManagedWards(String summaryNo) {
        this.assertSummaryNoInVisibleSet(summaryNo, new HashSet<>(this.resolveManagedWardVisibleSummaryNos()));
    }

    /**
     * 断言单号落在已解析的管辖病区可见集合内（批量发送/撤销复用同一集合，避免重复反查）。
     *
     * @param summaryNo          汇总单号
     * @param visibleSummaryNos  管辖病区可见单号集合
     * @throws BusinessException EX-IP-SUM-103 不可见或不存在
     */
    private void assertSummaryNoInVisibleSet(String summaryNo, Set<String> visibleSummaryNos) {
        if (StringUtils.isEmpty(summaryNo)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_103,
                "汇总单不存在或已失效，请刷新");
        }
        // 可见集合为空或未命中：视为无权/不存在
        if (visibleSummaryNos == null || !visibleSummaryNos.contains(summaryNo.trim())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_103,
                "汇总单不存在或已失效，请刷新");
        }
    }

    /**
     * 为汇总单头回填申领科室（病区主挂靠科室名；打印 IF-IP-SUM-07 依赖）。
     *
     * @param header    已装配的单头；null 时跳过
     * @param summaryNo 汇总单号（用于反查发放行就诊）
     */
    private void enrichSlipHeaderApplicantDepartment(
        NurseStationDispenseSummarySlipHeaderDto header,
        String summaryNo) {
        if (header == null || StringUtils.isEmpty(summaryNo)) {
            return;
        }
        List<MedicationDispenseSummaryItemQueryModel> dispenseRows =
            medicationDispenseQueryService.listMedicationDispensesBySummaryNo(summaryNo);
        List<Long> encounterIds = this.collectEncounterIdsFromSummaryItems(dispenseRows);
        header.setApplicantDepartmentName(
            medicationSummaryApplicantDepartmentResolver.resolveByEncounterIds(encounterIds));
    }

    /**
     * 为当前页汇总单头批量回填申领科室（列表 IF-IP-SUM-02；按页规模逐单解析）。
     *
     * @param rows 单头列表；null/空跳过
     */
    private void enrichSlipHeadersApplicantDepartment(List<NurseStationDispenseSummarySlipHeaderDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (NurseStationDispenseSummarySlipHeaderDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            this.enrichSlipHeaderApplicantDepartment(row, row.getBusNo());
        }
    }

    /**
     * 按 bus_no 精确加载汇总单头 DTO（EX-IP-SUM-103 不存在时抛错）。
     *
     * @param summaryNo 汇总单号
     * @return 匹配的汇总单头
     * @throws BusinessException 单不存在或已失效
     */
    private NurseStationDispenseSummarySlipHeaderDto loadSlipHeaderBySummaryNo(String summaryNo) {
        PageMedicationSummarySlipHeadersRequest pageRequest = new PageMedicationSummarySlipHeadersRequest();
        pageRequest.setPageNo(1);
        pageRequest.setPageSize(1);
        pageRequest.setKeyword(summaryNo);
        SupplyRequestDocumentListQuery listQuery = nurseStationDispenseSummaryMapper.toSupplyRequestDocumentListQuery(
            pageRequest, null);
        Page<MedicationSummarySlipQueryModel> page = medicationSummarySlipLoader.pageMedicationSummarySlips(listQuery);
        List<NurseStationDispenseSummarySlipHeaderDto> headers =
            nurseStationDispenseSummaryMapper.toSlipHeaderDtos(page);
        if (headers.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_103,
                "汇总单不存在或已失效，请刷新");
        }
        for (NurseStationDispenseSummarySlipHeaderDto header : headers) {
            if (header != null && summaryNo.equals(header.getBusNo())) {
                return header;
            }
        }
        return headers.get(0);
    }

    /**
     * 汇总领药单申领科室：优先单据上记下的登录科室，历史单回落申领人默认登录科室。
     *
     * @param applicantId             申领人（点汇总/发送的医务人员）
     * @param consumingOrganizationId 点汇总或发送时写入的登录科室；历史单可空
     * @return 科室名称；无法解析时为空串
     */
    private String resolveApplicantLoginDepartmentName(Long applicantId, Long consumingOrganizationId) {
        Long organizationId = consumingOrganizationId;
        if (organizationId == null && applicantId != null) {
            Practitioner applicant = practitionerQueryService.findById(applicantId);
            if (applicant != null) {
                organizationId = applicant.getOrgId();
            }
        }
        if (organizationId == null) {
            return "";
        }
        Map<Long, String> nameById = organizationQueryService.mapNamesByIds(List.of(organizationId));
        String name = nameById.get(organizationId);
        if (name == null) {
            return "";
        }
        return name.trim();
    }

    /**
     * 解析汇总单列表查询的目的地 location 过滤（单选非 0 时返回单元素列表）。
     *
     * @param request 列表请求
     * @return purposeLocationId 列表；未指定时 null 表示不过滤
     */
    private List<Long> resolveSummaryPurposeLocationIds(PageMedicationSummarySlipHeadersRequest request) {
        Long purposeLocationId = null;
        if (request != null) {
            purposeLocationId = request.getPurposeLocationId();
        }
        if (purposeLocationId != null && purposeLocationId.longValue() != 0L) {
            List<Long> single = new ArrayList<>();
            single.add(purposeLocationId);
            return single;
        }
        return null;
    }

    /**
     * 断言请求 encounter 均在护士站可见病区范围内（EX-IP-SUM-101）。
     *
     * @param encounterIds 待校验就诊 id
     * @throws BusinessException 空列表或含不可见就诊
     */
    private void requireVisibleEncounters(List<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
        }
        List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        InpatientEncounterPageQuery query = nurseStationDispenseSummaryMapper.toInpatientEncounterPageQuery(
            encounterIds, managedWardIds);
        List<InpatientEncounterContextQueryModel> visibleRows =
            EncounterContextReader.listInpatientEncounterContexts(query);
        List<Long> visibleEncounterIds = this.retainRequestedVisibleEncounterIds(encounterIds, visibleRows);
        if (visibleEncounterIds.size() != encounterIds.size()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
        }
    }

    /**
     * 从待发药分页结果收集药品定义批量查询键（去重）。
     *
     * @param page 待发药分页
     * @return ProductDefinition 批量 lookup 列表
     */
    private List<ProductDefinitionBatchLookup> collectProductLookupsFromPendingPage(
        Page<MedicationDispensePickupPendingQueryModel> page) {
        if (page == null) {
            return this.collectProductLookupsFromPendingLines(null, null);
        }
        return this.collectProductLookupsFromPendingLines(page.getContent(), null);
    }

    /**
     * 从待汇总扁平行收集药品定义批量查询键（去重）。
     *
     * @param primaryLines 主结果行
     * @param extraLines   附加行；可空
     * @return ProductDefinition 批量 lookup 列表
     */
    private List<ProductDefinitionBatchLookup> collectProductLookupsFromPendingLines(
        List<MedicationDispensePickupPendingQueryModel> primaryLines,
        List<MedicationDispensePickupPendingQueryModel> extraLines) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        this.appendProductLookupsFromPendingLines(primaryLines, lookups, dedupe);
        this.appendProductLookupsFromPendingLines(extraLines, lookups, dedupe);
        return lookups;
    }

    /**
     * 将扁平行中的药品 id 追加进 lookup 列表（同复合键去重）。
     *
     * @param lines   待汇总行
     * @param lookups 输出 lookup
     * @param dedupe  已见复合键
     */
    private void appendProductLookupsFromPendingLines(
        List<MedicationDispensePickupPendingQueryModel> lines,
        List<ProductDefinitionBatchLookup> lookups,
        Set<String> dedupe) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (MedicationDispensePickupPendingQueryModel row : lines) {
            if (row == null || row.getMedicationId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getMedicationId());
            if (dedupe.add(key)) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
                lookup.setProductDefinitionId(row.getMedicationId());
                lookups.add(lookup);
            }
        }
    }

    /**
     * 从汇总单明细行收集药品定义批量查询键（去重）。
     *
     * @param domainLines 汇总单明细读模型
     * @return ProductDefinition 批量 lookup 列表
     */
    private List<ProductDefinitionBatchLookup> collectProductLookupsFromSlipLines(
        List<MedicationSummarySlipLineQueryModel> domainLines) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        if (domainLines == null) {
            return lookups;
        }
        for (MedicationSummarySlipLineQueryModel row : domainLines) {
            if (row == null || row.getItemId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getItemId());
            if (dedupe.add(key)) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
                lookup.setProductDefinitionId(row.getItemId());
                lookups.add(lookup);
            }
        }
        return lookups;
    }

    /**
     * 从发放实体列表收集药品定义批量查询键（去重）。
     *
     * @param dispenses 发放行
     * @return ProductDefinition 批量 lookup 列表
     */
    private List<ProductDefinitionBatchLookup> collectProductLookupsFromDispenses(List<MedicationDispense> dispenses) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        if (dispenses == null) {
            return lookups;
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getMedicationId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, dispense.getMedicationId());
            if (dedupe.add(key)) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
                lookup.setProductDefinitionId(dispense.getMedicationId());
                lookups.add(lookup);
            }
        }
        return lookups;
    }

    /**
     * 从汇总单发放明细收集药品定义批量查询键（去重；IF-IP-SUM-09）。
     *
     * @param dispenseRows 汇总单号下发放行
     * @return ProductDefinition 批量 lookup 列表
     */
    private List<ProductDefinitionBatchLookup> collectProductLookupsFromSummaryItems(
        List<MedicationDispenseSummaryItemQueryModel> dispenseRows) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        if (dispenseRows == null) {
            return lookups;
        }
        for (MedicationDispenseSummaryItemQueryModel row : dispenseRows) {
            if (row == null || row.getMedicationId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getMedicationId());
            if (dedupe.add(key)) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
                lookup.setProductDefinitionId(row.getMedicationId());
                lookups.add(lookup);
            }
        }
        return lookups;
    }

    /**
     * 从汇总单发放明细收集 encounterId（去重保序；IF-IP-SUM-09）。
     *
     * @param dispenseRows 汇总单号下发放行
     * @return encounterId 列表
     */
    private List<Long> collectEncounterIdsFromSummaryItems(
        List<MedicationDispenseSummaryItemQueryModel> dispenseRows) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (dispenseRows == null) {
            return new ArrayList<>();
        }
        for (MedicationDispenseSummaryItemQueryModel row : dispenseRows) {
            if (row != null && row.getEncounterId() != null) {
                ids.add(row.getEncounterId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 从汇总单发放明细收集 medReqId（去重保序；IF-IP-SUM-09）。
     *
     * @param dispenseRows 汇总单号下发放行
     * @return medReqId 列表
     */
    private List<Long> collectMedReqIdsFromSummaryItems(
        List<MedicationDispenseSummaryItemQueryModel> dispenseRows) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (dispenseRows == null) {
            return new ArrayList<>();
        }
        for (MedicationDispenseSummaryItemQueryModel row : dispenseRows) {
            if (row != null && row.getMedReqId() != null) {
                ids.add(row.getMedReqId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 按就诊活动床解析病房（HOUSE）名称：床位 parentId → 病房名。
     *
     * @param encounterById 就诊上下文
     * @return encounterId → 病房名
     */
    private Map<Long, String> resolveHouseNameByEncounterId(
        Map<Long, InpatientEncounterContextQueryModel> encounterById) {
        Map<Long, String> houseNameByEncounterId = new HashMap<>();
        if (encounterById == null || encounterById.isEmpty()) {
            return houseNameByEncounterId;
        }
        Set<Long> bedIds = new HashSet<>();
        for (InpatientEncounterContextQueryModel encounter : encounterById.values()) {
            if (encounter != null && encounter.getBedId() != null) {
                bedIds.add(encounter.getBedId());
            }
        }
        if (bedIds.isEmpty()) {
            return houseNameByEncounterId;
        }
        Map<Long, Location> bedById = locationQueryService.loadLocationEntityMapByIds(bedIds);
        Set<Long> houseIds = new HashSet<>();
        for (Location bed : bedById.values()) {
            if (bed != null && bed.getParentId() != null) {
                houseIds.add(bed.getParentId());
            }
        }
        Map<Long, String> houseNameById = locationQueryService.mapNamesByIds(houseIds);
        for (Map.Entry<Long, InpatientEncounterContextQueryModel> entry : encounterById.entrySet()) {
            InpatientEncounterContextQueryModel encounter = entry.getValue();
            if (encounter == null || encounter.getBedId() == null) {
                continue;
            }
            Location bed = bedById.get(encounter.getBedId());
            if (bed == null || bed.getParentId() == null) {
                continue;
            }
            String houseName = houseNameById.get(bed.getParentId());
            if (StringUtils.isNotEmpty(houseName)) {
                houseNameByEncounterId.put(entry.getKey(), houseName);
            }
        }
        return houseNameByEncounterId;
    }

    /**
     * 按就诊 id 批量加载住院上下文（床号/姓名；IF-IP-SUM-09 展示用，不二次做病区门控）。
     *
     * @param encounterIds 就诊 id
     * @return encounterId → 上下文
     */
    private Map<Long, InpatientEncounterContextQueryModel> loadEncounterContextByIds(List<Long> encounterIds) {
        Map<Long, InpatientEncounterContextQueryModel> map = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return map;
        }
        InpatientEncounterPageQuery query =
            nurseStationDispenseSummaryMapper.toInpatientEncounterPageQuery(encounterIds, null);
        List<InpatientEncounterContextQueryModel> rows =
            EncounterContextReader.listInpatientEncounterContexts(query);
        return this.indexEncounters(rows, encounterIds);
    }

    /**
     * 从待发药分页收集 locationId 集合（展示用）。
     *
     * @param page 待发药分页
     * @return locationId 集合
     */
    private Set<Long> collectLocationIdsFromPendingPage(Page<MedicationDispensePickupPendingQueryModel> page) {
        Set<Long> ids = new HashSet<>();
        if (page == null || page.getContent() == null) {
            return ids;
        }
        for (MedicationDispensePickupPendingQueryModel row : page.getContent()) {
            if (row != null && row.getLocationId() != null) {
                ids.add(row.getLocationId());
            }
        }
        return ids;
    }

    /**
     * 待汇总页按 procedureId 批量挂接 {@code recorded_time}（领药时间列展示轴）。
     *
     * <p>勾选/截止逻辑仍锚 {@code plannedDispenseTime}；本方法仅补展示用执行时刻。</p>
     *
     * @param page 待汇总发放分页
     * @return procedureId → recorded_time；无 procedure 或无 recorded 时不入 Map
     */
    private Map<Long, LocalDateTime> indexRecordedTimeByProcedureIdFromPendingPage(
        Page<MedicationDispensePickupPendingQueryModel> page) {
        Map<Long, LocalDateTime> recordedByProcedureId = new HashMap<>();
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            return recordedByProcedureId;
        }
        Set<Long> procedureIds = new HashSet<>();
        for (MedicationDispensePickupPendingQueryModel row : page.getContent()) {
            if (row != null && row.getProcedureId() != null) {
                procedureIds.add(row.getProcedureId());
            }
        }
        if (procedureIds.isEmpty()) {
            return recordedByProcedureId;
        }
        List<Procedure> procedures = procedureQueryService.listByIds(procedureIds);
        for (Procedure procedure : procedures) {
            if (procedure == null || procedure.getId() == null || procedure.getRecordedTime() == null) {
                continue;
            }
            recordedByProcedureId.put(procedure.getId(), procedure.getRecordedTime());
        }
        return recordedByProcedureId;
    }

    /**
     * 按 id 逐条加载 Location 实体（可选存在）。
     *
     * @param locationIds 位置 id 集合
     * @return 已存在的 Location 列表
     */
    private List<Location> loadLocations(Set<Long> locationIds) {
        List<Location> locations = new ArrayList<>();
        if (locationIds == null) {
            return locations;
        }
        for (Long locationId : locationIds) {
            if (locationId == null) {
                continue;
            }
            locationQueryService.findOptionalById(locationId).ifPresent(locations::add);
        }
        return locations;
    }

    /**
     * 从创建汇总请求收集 dispenseId（去重保序）。
     *
     * @param request 创建请求
     * @return dispenseId 列表
     */
    private List<Long> collectDispenseIdsFromWriteRequest(CreateMedicationSummaryRequest request) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (CreateMedicationSummaryDispenseItemDto item : request.getDispenseItems()) {
            if (item != null && item.getDispenseId() != null) {
                ids.add(item.getDispenseId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 从创建汇总请求收集 encounterId（去重保序）。
     *
     * @param request 创建请求
     * @return encounterId 列表
     */
    private List<Long> collectEncounterIdsFromWriteRequest(CreateMedicationSummaryRequest request) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (CreateMedicationSummaryDispenseItemDto item : request.getDispenseItems()) {
            if (item != null && item.getEncounterId() != null) {
                ids.add(item.getEncounterId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 从合并分组收集全部 dispenseId（去重保序）。
     *
     * @param groups 汇总合并分组
     * @return dispenseId 列表
     */
    private List<Long> collectDispenseIdsFromGroups(List<SummaryMergeGroup> groups) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (groups == null) {
            return new ArrayList<>();
        }
        for (SummaryMergeGroup group : groups) {
            if (group == null || group.getDispenseIds() == null) {
                continue;
            }
            for (Long dispenseId : group.getDispenseIds()) {
                if (dispenseId != null) {
                    ids.add(dispenseId);
                }
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 医嘱执行列表 → id 索引 Map。
     *
     * @param executeOrders 医嘱读模型列表
     * @return medReqId → 读模型
     */
    private Map<Long, MedicationRequestListQueryModel> indexMedReqById(
        List<MedicationRequestListQueryModel> executeOrders) {
        Map<Long, MedicationRequestListQueryModel> map = new HashMap<>();
        if (executeOrders == null) {
            return map;
        }
        for (MedicationRequestListQueryModel row : executeOrders) {
            if (row != null && row.getId() != null) {
                map.put(row.getId(), row);
            }
        }
        return map;
    }

    /**
     * 可见就诊上下文 → encounterId 索引（限 visibleEncounterIds 子集）。
     *
     * @param visibleRows          可见就诊行
     * @param visibleEncounterIds  允许的 encounterId
     * @return encounterId → 上下文
     */
    private Map<Long, InpatientEncounterContextQueryModel> indexEncounters(
        List<InpatientEncounterContextQueryModel> visibleRows,
        List<Long> visibleEncounterIds) {
        Map<Long, InpatientEncounterContextQueryModel> map = new HashMap<>();
        Set<Long> idSet = new HashSet<>(visibleEncounterIds);
        if (visibleRows == null) {
            return map;
        }
        for (InpatientEncounterContextQueryModel row : visibleRows) {
            if (row != null && row.getEncounterId() != null && idSet.contains(row.getEncounterId())) {
                map.put(row.getEncounterId(), row);
            }
        }
        return map;
    }

    /**
     * Long id 列表去重并保持顺序。
     *
     * @param ids 原始 id 列表
     * @return 去重后的 id
     */
    private List<Long> distinctIds(List<Long> ids) {
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    set.add(id);
                }
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 按请求顺序保留可见 encounter 交集（护士站病区过滤后）。
     *
     * @param requestedEncounterIds 请求就诊 id
     * @param visibleRows           可见就诊上下文
     * @return 可见且被请求的 encounterId（保序）
     */
    private List<Long> retainRequestedVisibleEncounterIds(
        List<Long> requestedEncounterIds,
        List<InpatientEncounterContextQueryModel> visibleRows) {
        if (requestedEncounterIds == null || requestedEncounterIds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> visibleIdSet = new HashSet<>();
        if (visibleRows != null) {
            for (InpatientEncounterContextQueryModel row : visibleRows) {
                if (row != null && row.getEncounterId() != null) {
                    visibleIdSet.add(row.getEncounterId());
                }
            }
        }
        List<Long> result = new ArrayList<>();
        for (Long encounterId : requestedEncounterIds) {
            if (encounterId != null && visibleIdSet.contains(encounterId)) {
                result.add(encounterId);
            }
        }
        return result;
    }

    /**
     * 长临筛选项是否合法（全部/长期/临时）。
     *
     * @param therapy 筛选项 code
     * @return 允许为 true
     */
    private boolean isAllowedTherapyFilter(String therapy) {
        if (InpatientNurseStationConstants.THERAPY_FILTER_ALL.equals(therapy)) {
            return true;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_LONG.equals(therapy)) {
            return true;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_TEMP.equals(therapy)) {
            return true;
        }
        return false;
    }
}
