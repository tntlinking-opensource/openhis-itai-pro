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

package com.openhis.health.application.inpatient.doctorstation.advice.service;

import com.openhis.health.module.common.assembler.advice.ServiceApplyFormOrchestrationSupport;
import com.openhis.health.module.common.assembler.option.ContractFeeNatureOptionLoader;
import com.openhis.health.module.common.assembler.option.FrequencyOptionLoader;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.module.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.inpatient.doctorstation.advice.assembler.InpatientDoctorStationAdviceCatalogAssembler;
import com.openhis.health.application.inpatient.doctorstation.advice.assembler.InpatientDoctorStationAdviceListAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.ServiceApplyFormAssembler;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceCatalogRowDto;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceResourceKeyDto;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceRowDto;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.StopLongTermAdviceLineRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.mapper.InpatientDoctorStationAdviceMapper;
import com.openhis.health.application.inpatient.doctorstation.advice.port.LisPacsAdviceApplyCommand;
import com.openhis.health.application.inpatient.doctorstation.advice.port.LisPacsApplyGateway;
import com.openhis.health.application.inpatient.doctorstation.advice.query.InpatientDoctorStationAdviceOrderBindQueryService;
import com.openhis.health.application.inpatient.doctorstation.advice.request.CreateDischargeInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.GetInpatientDoctorStationAdviceOrderBindInfoRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.InitInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.ListActivityExecutionLocationsRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.ListInpatientDoctorStationServiceApplyFormsRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.SaveInpatientDoctorStationServiceApplyFormRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.response.PageInpatientDoctorStationAdviceResponse;
import com.openhis.health.domain.share.constants.DoctorStationAdviceScopeTab;
import com.openhis.health.application.inpatient.doctorstation.advice.request.SaveInpatientDoctorStationAdviceDraftBatchRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.SignInpatientDoctorStationAdviceBatchRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.StopLongTermInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.UpdateInpatientDoctorStationMedicationAdviceGroupsRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.WithdrawInpatientDoctorStationAdviceBatchRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.response.ActivityExecutionLocationRuleRowDto;
import com.openhis.health.application.inpatient.doctorstation.advice.response.CreateDischargeInpatientDoctorStationAdviceResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.GetInpatientDoctorStationAdviceOrderBindInfoResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.InitInpatientDoctorStationAdviceResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.ListActivityExecutionLocationsResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.request.ListInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.response.ListInpatientDoctorStationServiceApplyFormsResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.SaveInpatientDoctorStationServiceApplyFormResponse;
import com.openhis.health.domain.administration.specimendefinition.queries.SearchSpecimenDefinitionQuery;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionSearchItemQueryModel;
import com.openhis.health.domain.administration.specimendefinition.service.SpecimenDefinitionQueryService;
import com.openhis.health.domain.diagnosis.bodystructure.queries.model.BodyStructureListItemQueryModel;
import com.openhis.health.domain.diagnosis.bodystructure.service.BodyStructureQueryService;
import com.openhis.health.module.common.assembler.option.OptionDtoAssembler;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.openhis.health.application.inpatient.doctorstation.advice.response.ListInpatientDoctorStationAdviceResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.PageInpatientDoctorStationAdviceCatalogResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.SaveInpatientDoctorStationAdviceDraftBatchResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.SignInpatientDoctorStationAdviceBatchResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.StopLongTermInpatientDoctorStationAdviceResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.UpdateInpatientDoctorStationMedicationAdviceGroupsResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.WithdrawInpatientDoctorStationAdviceBatchResponse;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleListQueryModel;
import com.openhis.health.domain.custom.activityexecutionlocation.service.ActivityExecutionLocationQueryService;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryDomainService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestDetailQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.module.common.assembler.order.InpatientOrderListMergeAssembler;
import com.openhis.health.module.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionCatalogPageLoader;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.module.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院医生站医嘱开立应用编排。
 *
 * <p>草稿/签发：住院长临医嘱均仅开立/签发 Request，不在签发相位建发放与费用（门诊临嘱不变）。</p>
 * <p>停嘱/撤回：长临均仅更新请求状态（不在签发相位建发放/费用）。</p>
 * <p>目录与列表：可开立目录分页、医嘱统一列表与 LIS/PACS 申请对接。</p>
 */
@Service
@RequiredArgsConstructor
public class InpatientDoctorStationAdviceAppService {

    private final CurrentOperatorResolver currentOperatorResolver;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final InpatientOrderListMergeAssembler inpatientOrderListMergeAssembler;
    private final InpatientDoctorStationAdviceMapper inpatientDoctorStationAdviceMapper;
    private final InpatientDoctorStationAdviceListAssembler inpatientDoctorStationAdviceListAssembler;
    private final InpatientDoctorStationAdviceCatalogAssembler inpatientDoctorStationAdviceCatalogAssembler;
    private final InpatientDoctorStationAdviceOrderBindQueryService inpatientDoctorStationAdviceOrderBindQueryService;
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final BusinessSequenceService businessSequenceService;
    private final LisPacsApplyGateway lisPacsApplyGateway;
    private final CodeSystemQueryService codeSystemQueryService;
    /** 用药频次下拉：读 cus_frequency 启用态。 */
    private final FrequencyOptionLoader frequencyOptionLoader;
    /** 费用性质（合同）下拉：按就诊挂号分类收窄。 */
    private final ContractFeeNatureOptionLoader contractFeeNatureOptionLoader;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ActivityExecutionLocationQueryService activityExecutionLocationQueryService;
    private final LocationQueryService locationQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final EncounterHistoryDomainService encounterHistoryDomainService;
    private final ServiceApplyFormOrchestrationSupport serviceApplyFormOrchestrationSupport;
    private final ServiceApplyFormAssembler serviceApplyFormAssembler;
    private final BodyStructureQueryService bodyStructureQueryService;
    private final SpecimenDefinitionQueryService specimenDefinitionQueryService;
    /**
     * IF-IP-DOR-22：分页查询患者住院医嘱（单接口分 Tab：当前医嘱 / 历史医嘱；全类型统一列表）。
     *
     * <p>读口径与 IF-IP-DOR-10 一致（药品 + 诊疗）；是否按处方展示由前端处理。</p>
     *
     * @param request 就诊、患者、{@code adviceScopeTabCode}、可选长临/科室/状态过滤及分页
     * @return 医嘱统一列表行分页
     * @throws BusinessException 就诊不存在或患者与就诊不一致
     */
    public PageInpatientDoctorStationAdviceResponse pageInpatientDoctorStationAdvice(
        PageInpatientDoctorStationAdviceRequest request) {
        // Gate：与列表/目录一致，先校验住院快照与患者卡片一致，再加载医嘱读模型。
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        Long currentUserId = currentOperatorResolver.findOperatorIdOrNull();
        List<InpatientDoctorStationAdviceRowDto> allRows;
        // 分支：HISTORY 走患者维度跨就诊；其余（含 null）默认当前就诊全类型医嘱。
        if (DoctorStationAdviceScopeTab.HISTORY.equals(request.getAdviceScopeTabCode())) {
            // Pre：历史 Tab 按患者跨就诊拉全类型医嘱，排除当前工作站就诊。
            LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
            // Pre：药/诊两类请求并集收集历史就诊 id（住院医嘱列表不含耗材）。
            encounterIds.addAll(medicationRequestQueryService.listDistinctEncounterIdsByPatientForDoctorStation(
                request.getPatientId(), request.getEncounterId()));
            encounterIds.addAll(serviceRequestQueryService.listDistinctEncounterIdsByPatientForDoctorStation(
                request.getPatientId(), request.getEncounterId()));
            allRows = new ArrayList<>();
            // Loop：逐历史就诊 merge 骨架后再装配，继承长临/科室/状态过滤。
            for (Long historyEncounterId : encounterIds) {
                List<InpatientOrderListSkeleton> orders = inpatientOrderListMergeAssembler.mergeDoctorStationOrders(
                    historyEncounterId, request.getTherapyTypeCode());
                allRows.addAll(inpatientDoctorStationAdviceListAssembler.assembleAdviceQueryRows(
                    historyEncounterId,
                    request.getPatientId(),
                    orders,
                    currentUserId,
                    request.getOrderingDepartmentId(),
                    request.getRequestStatusEnumIn()));
            }
            // Asm：跨就诊结果按开立时间倒序，同时间再按就诊倒序，便于医嘱查询时间线展示。
            allRows.sort(Comparator
                .comparing(InpatientDoctorStationAdviceRowDto::getAuthoredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(InpatientDoctorStationAdviceRowDto::getEncounterId, Comparator.nullsLast(Comparator.reverseOrder())));
        } else {
            // Pre：当前 Tab 仅装配当前就诊全类型医嘱。
            List<InpatientOrderListSkeleton> orders = inpatientOrderListMergeAssembler.mergeDoctorStationOrders(
                request.getEncounterId(), request.getTherapyTypeCode());
            allRows = inpatientDoctorStationAdviceListAssembler.assembleAdviceQueryRows(
                request.getEncounterId(),
                request.getPatientId(),
                orders,
                currentUserId,
                request.getOrderingDepartmentId(),
                request.getRequestStatusEnumIn());
        }
        // Asm：全量行内存分页，与 IF-IP-DOR-10 行 DTO 同构下发。
        return PagedResultBuilder.buildFromFullList(
            new PageInpatientDoctorStationAdviceResponse(),
            allRows,
            request.getPageNo(),
            request.getBoundedPageSize());
    }

    /**
     * 医嘱开立页初始化：频次、给药途径与费用性质（合同）下拉。
     *
     * @param request 就诊 id、患者 id（Gate 用）
     * @return 频次、给药途径与费用性质 {@code OptionDto} 列表
     * @throws BusinessException 快照缺失或患者与读模型不一致
     */
    public InitInpatientDoctorStationAdviceResponse initInpatientDoctorStationAdvice(InitInpatientDoctorStationAdviceRequest request) {
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        InitInpatientDoctorStationAdviceResponse resp = new InitInpatientDoctorStationAdviceResponse();
        resp.setMedicationRateOptions(frequencyOptionLoader.loadActiveRateOptions());
        resp.setMedicationRouteOptions(codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.METHOD_CODE));
        resp.setFeeNatureOptions(
            contractFeeNatureOptionLoader.loadOptionsForEncounter(request.getPatientId(), request.getEncounterId()));
        resp.setBodyStructureOptions(loadBodyStructureOptions());
        resp.setSpecimenDefinitionOptions(loadSpecimenDefinitionOptions());
        return resp;
    }

    /**
     *
     * <p>只读接口：按活动定义 id 拉取全院执行科室/机构规则，并批量回填展示名称；
     * 不含库存 Gate，供开立诊疗前选择执行地点。</p>
     *
     * @param request 就诊上下文与 {@code activityDefinitionId}
     * @return 可执行科室规则行列表
     * @throws BusinessException 快照或患者门控失败
     */
    public ListActivityExecutionLocationsResponse listActivityExecutionLocations(ListActivityExecutionLocationsRequest request) {
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        ProductDefinitionDisplayDto activityProduct =
            loadSingleProduct(TableNameConstants.WKF_ACTIVITY_DEFINITION, request.getActivityDefinitionId());
        List<ExecutionRuleListQueryModel> ruleModels =
            activityExecutionLocationQueryService.listExecutionRulesForActivityResolution(
                request.getActivityDefinitionId(),
                activityProduct.getCategoryCode());
        Set<Long> orgIds = new HashSet<>();
        Set<Long> locIds = new HashSet<>();
        for (ExecutionRuleListQueryModel row : ruleModels) {
            if (row.getExecutionOrgId() != null) {
                orgIds.add(row.getExecutionOrgId());
            }
            if (row.getExecutionLocationId() != null) {
                locIds.add(row.getExecutionLocationId());
            }
        }
        Map<Long, String> orgNameById = organizationQueryService.mapNamesByIds(orgIds);
        Map<Long, String> locNameById = locationQueryService.mapNamesByIds(locIds);
        List<ActivityExecutionLocationRuleRowDto> rows = new ArrayList<>();
        for (ExecutionRuleListQueryModel src : ruleModels) {
            ActivityExecutionLocationRuleRowDto line = new ActivityExecutionLocationRuleRowDto();
            line.setId(src.getId());
            line.setActivityDefinitionId(src.getActivityDefinitionId());
            line.setActivityCategoryCode(src.getActivityCategoryCode());
            line.setExecutionOrgId(src.getExecutionOrgId());
            if (src.getExecutionOrgId() != null) {
                line.setExecutionOrgName(orgNameById.get(src.getExecutionOrgId()));
            }
            line.setExecutionLocationId(src.getExecutionLocationId());
            if (src.getExecutionLocationId() != null) {
                line.setExecutionLocationName(locNameById.get(src.getExecutionLocationId()));
            }
            line.setStartTime(src.getStartTime());
            line.setEndTime(src.getEndTime());
            line.setPriority(src.getPriority());
            line.setDisplayOrder(src.getDisplayOrder());
            line.setRemark(src.getRemark());
            rows.add(line);
        }
        ListActivityExecutionLocationsResponse resp = new ListActivityExecutionLocationsResponse();
        resp.setRules(rows);
        return resp;
    }

    /**
     * IF-IP-DOR-10：加载住院医生站医嘱统一列表。
     *
     * <p>只读接口：以 OrderInfo 读模型（Q-ORD-007）为主数据源，叠加护士执行后费用归并、术语与发放库位展示；
     * 可按长临类型筛选；返回全量列表（非 DB 分页）。</p>
     *
     * @param request 就诊 id、患者 id、可选 {@code therapyTypeCode} 筛选
     * @return 医嘱统一列表行（{@code content} 承载行集合）
     * @throws BusinessException 快照、患者或列表 Gate 失败
     */
    public ListInpatientDoctorStationAdviceResponse listInpatientDoctorStationAdvice(ListInpatientDoctorStationAdviceRequest request) {
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        inpatientDoctorStationAdviceMapper.validateListRequest(request.getEncounterId(), request.getPatientId());
        Long currentUserId = currentOperatorResolver.findOperatorIdOrNull();
        List<InpatientOrderListSkeleton> orders = inpatientOrderListMergeAssembler.mergeDoctorStationOrders(
            request.getEncounterId(), request.getTherapyTypeCode());
        List<InpatientDoctorStationAdviceRowDto> rows = inpatientDoctorStationAdviceListAssembler.assembleRows(
            request.getEncounterId(), request.getPatientId(), orders, currentUserId);
        return PagedResultBuilder.buildList(new ListInpatientDoctorStationAdviceResponse(), rows);
    }

    /**
     *
     * <p>只读接口：在产品定义读模型上分页检索药品与诊疗（不含耗材），
     * 装配定价、库存展示等住院场景字段；不校验就诊可写态。</p>
     *
     * @param request 分页、关键字、目录筛选及就诊上下文
     * @return 可开立目录分页行（{@code pageNo}/{@code pageSize} 经 {@link PagedResultBuilder} 装配）
     * @throws BusinessException 快照或患者门控失败
     */
    public PageInpatientDoctorStationAdviceCatalogResponse pageInpatientDoctorStationAdviceCatalog(
        PageInpatientDoctorStationAdviceCatalogRequest request
    ) {
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        ProductDefinitionPageQuery pageQuery = inpatientDoctorStationAdviceMapper.toProductDefinitionPageQuery(request);
        Page<ProductDefinitionDisplayDto> page = productDefinitionCatalogPageLoader.pageProductDefinition(pageQuery);
        List<InpatientDoctorStationAdviceCatalogRowDto> rows =
            inpatientDoctorStationAdviceCatalogAssembler.assemblePageContent(request, page, snapshot);
        return PagedResultBuilder.build(new PageInpatientDoctorStationAdviceCatalogResponse(), rows, page);
    }

    /**
     * IF-IP-DOR-23：保存检验/检查/治疗申请单（整张）；编辑时删旧 DRAFT 后取新 {@code AR} 号。
     *
     * @param request 就诊、患者、类别、整单执行科室、整单诊断绑定（可选）与明细行
     * @return 新申请单号与新建 requestId 列表
     * @throws BusinessException 门控失败、旧号校验失败、定价缺失或域校验失败
     */
    @Transactional(rollbackFor = Exception.class)
    public SaveInpatientDoctorStationServiceApplyFormResponse saveInpatientDoctorStationServiceApplyForm(
        SaveInpatientDoctorStationServiceApplyFormRequest request
    ) {
        // Gate：住院 IMP 门控 + 患者一致性。
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(
            request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        // Cmd：共享编排 saveApplyForm；住院不在暂存相位挂费用。
        ServiceApplyFormOrchestrationSupport.SaveApplyFormResult saved = serviceApplyFormOrchestrationSupport.saveApplyForm(
            request.getEncounterId(),
            request.getPatientId(),
            request.getCategoryEnum(),
            request.getOrgId(),
            request.getConditionDefinitionId(),
            request.getEncounterDiagnosisId(),
            request.getPreviousPrescriptionNo(),
            serviceApplyFormAssembler.toLineSpecs(request.getLines()),
            operatorId,
            null);
        // Asm：返回新号与 requestId 列表。
        SaveInpatientDoctorStationServiceApplyFormResponse resp = new SaveInpatientDoctorStationServiceApplyFormResponse();
        resp.setPrescriptionNo(saved.getPrescriptionNo());
        resp.setRequestIds(saved.getRequestIds());
        return resp;
    }

    /**
     * IF-IP-DOR-24：按类别查询当前就诊 {@code AR} 前缀申请单列表。
     *
     * @param request 就诊、患者与 categoryEnum
     * @return 按 prescriptionNo 分组的申请单头列表
     * @throws BusinessException 快照或患者门控失败
     */
    public ListInpatientDoctorStationServiceApplyFormsResponse listInpatientDoctorStationServiceApplyForms(
        ListInpatientDoctorStationServiceApplyFormsRequest request
    ) {
        // Gate：住院 IMP 门控。
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        // Pre + Asm：按 AR 前缀与 category 过滤后分组装配。
        ListInpatientDoctorStationServiceApplyFormsResponse resp = new ListInpatientDoctorStationServiceApplyFormsResponse();
        resp.setForms(serviceApplyFormAssembler.toHeaderDtos(
            serviceApplyFormOrchestrationSupport.listApplyForms(
                request.getEncounterId(), request.getCategoryEnum())));
        return resp;
    }

    /**
     *
     * <p>只读接口：按绑定类型解析业务项号或主键，返回用法→物资或诊疗模板子项绑定行，
     * 供开立页联动展示；不写库。</p>
     *
     * @param request 绑定类型、项号/主键、机构单元 id
     * @return 绑定行列表
     * @throws BusinessException 项号无法解析或绑定类型非法
     */
    public GetInpatientDoctorStationAdviceOrderBindInfoResponse getInpatientDoctorStationAdviceOrderBindInfo(
        GetInpatientDoctorStationAdviceOrderBindInfoRequest request
    ) {
        String sourceItemKey = inpatientDoctorStationAdviceMapper.resolveOrderBindSourceItemKey(
            request.getBindType(), request.getItemNo(), request.getItemId());
        GetInpatientDoctorStationAdviceOrderBindInfoResponse resp = new GetInpatientDoctorStationAdviceOrderBindInfoResponse();
        resp.setBindRows(inpatientDoctorStationAdviceOrderBindQueryService.listBindingsBySourceItemKey(
            request.getBindType(), sourceItemKey, request.getOrganizationUnitId()));
        return resp;
    }

    /**
     *
     * <p>写接口：按行操作码 CREATE/UPDATE/DELETE 分轨处理药品与诊疗草稿。
     * 长期/临时均仅维护 Request 草稿，不在暂存相位挂费用或发放（BR-IP-ORD-005/006）。
     * 单行失败则整批随事务回滚。</p>
     *
     * @param request 批次行列表与就诊上下文
     * @return 成功标记与本批新建药品请求 id
     * @throws BusinessException 可写态/操作人/行操作码等门控失败
     */
    public SaveInpatientDoctorStationAdviceDraftBatchResponse saveInpatientDoctorStationAdviceDraftBatch(
        SaveInpatientDoctorStationAdviceDraftBatchRequest request
    ) {
        // Gate：住院 IMP 可写态 + 操作人
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        Long orgId = snapshot.getOrganizationId();
        List<Long> newMedIds = new ArrayList<>();
        // Cmd：按行 CREATE/UPDATE/DELETE 分轨，长临均仅维护 Request
        for (InpatientDoctorStationAdviceDraftLineRequest line : request.getLines()) {
            String op = StringUtils.upperCase(StringUtils.trimToEmpty(line.getLineOp()));
            if (DoctorStationAdviceConstants.LINE_OP_CREATE.equals(op)) {
                handleCreateLine(request, line, operatorId, orgId, newMedIds);
            } else if (DoctorStationAdviceConstants.LINE_OP_UPDATE.equals(op)) {
                handleUpdateLine(request, line, operatorId, orgId);
            } else if (DoctorStationAdviceConstants.LINE_OP_DELETE.equals(op)) {
                handleDeleteLine(request, line, operatorId);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_LINE_OP_PREFIX + line.getLineOp());
            }
        }
        // Asm：返回本批新建药品 requestId
        SaveInpatientDoctorStationAdviceDraftBatchResponse resp = new SaveInpatientDoctorStationAdviceDraftBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setNewMedicationRequestIds(newMedIds);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * IF-IP-DOR-15：批次签发药品/诊疗请求。
     *
     * <p>写接口：住院长临均仅 sign 请求（处方号/签署编码），不在签发相位建发放与费用；
     * FIFO 与 ORDER_EXECUTE 费用在护士执行相位落库（AS-IP-NUR-004）。</p>
     *
     * @param request 签发目标列表与就诊上下文
     * @return 药品处方号映射
     * @throws BusinessException 门控失败或产品表不支持
     */
    public SignInpatientDoctorStationAdviceBatchResponse signInpatientDoctorStationAdviceBatch(
        SignInpatientDoctorStationAdviceBatchRequest request
    ) {
        // Gate：住院 IMP 可写态 + 操作人
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        Map<Long, String> rxMap = new HashMap<>();
        // Cmd：长临均 sign 请求，不在签发相位建发放/费用
        for (InpatientDoctorStationAdviceResourceKeyDto target : request.getSignTargets()) {
            String pt = normalizeProductTableName(target.getProductTableName());
            if (isMedicationProductTable(pt)) {
                signMedicationTarget(request, target, operatorId, rxMap);
            } else if (isActivityProductTable(pt)) {
                signServiceTarget(request, target, operatorId);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + target.getProductTableName());
            }
        }
        // Asm：返回处方号映射
        SignInpatientDoctorStationAdviceBatchResponse resp = new SignInpatientDoctorStationAdviceBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setPrescriptionNoByMedicationRequestId(rxMap);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * IF-IP-DOR-16：批次签退已签发医嘱。
     *
     * <p>写接口：住院长临药品均仅 signOff 请求；诊疗 withdrawSigned（BR-IP-ORD-009）。</p>
     *
     * @param request 签退目标列表与就诊上下文
     * @return 成功标记
     * @throws BusinessException 可写态/操作人门控失败或产品表不支持
     */
    public WithdrawInpatientDoctorStationAdviceBatchResponse withdrawInpatientDoctorStationAdviceBatch(
        WithdrawInpatientDoctorStationAdviceBatchRequest request
    ) {
        // Gate：住院 IMP 可写态 + 操作人
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        // Cmd：长临药品 signOff；诊疗 withdrawSigned
        for (InpatientDoctorStationAdviceResourceKeyDto target : request.getTargets()) {
            String pt = normalizeProductTableName(target.getProductTableName());
            if (isMedicationProductTable(pt)) {
                // 住院长临药品签退：仅 signOff 请求（不在签发相位建发放/费用）。
                medicationRequestDomainService.signOffMedicationOrder(
                    inpatientDoctorStationAdviceMapper.toSignOffMedicationOrderCommand(target.getRequestId(), operatorId));
            } else if (isActivityProductTable(pt)) {
                serviceRequestDomainService.withdrawSignedServiceRequest(
                    inpatientDoctorStationAdviceMapper.toWithdrawServiceRequestCommand(target.getRequestId(), operatorId));
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + target.getProductTableName());
            }
        }
        WithdrawInpatientDoctorStationAdviceBatchResponse resp = new WithdrawInpatientDoctorStationAdviceBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     *
     * <p>写接口：支持先清组再赋组，两段调用域 {@code adjustMedicationOrderGroup}；
     * 仅作用于药品请求，不涉及诊疗或费用直接变更。</p>
     *
     * @param request 待清组 id 列表、待赋组载荷与就诊上下文
     * @return 成功标记
     * @throws BusinessException 可写态或操作人门控失败
     */
    public UpdateInpatientDoctorStationMedicationAdviceGroupsResponse updateInpatientDoctorStationMedicationAdviceGroups(
        UpdateInpatientDoctorStationMedicationAdviceGroupsRequest request
    ) {
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        if (request.getClearGroupIds() != null && !request.getClearGroupIds().isEmpty()) {
            medicationRequestDomainService.adjustMedicationOrderGroup(
                inpatientDoctorStationAdviceMapper.toAdjustMedicationGroupCommand(request.getClearGroupIds(), null, operatorId));
        }
        if (request.getAssignGroup() != null && request.getAssignGroup().getMedicationRequestIds() != null) {
            medicationRequestDomainService.adjustMedicationOrderGroup(
                inpatientDoctorStationAdviceMapper.toAdjustMedicationGroupCommand(
                    request.getAssignGroup().getMedicationRequestIds(), request.getAssignGroup().getGroupId(), operatorId));
        }
        UpdateInpatientDoctorStationMedicationAdviceGroupsResponse resp = new UpdateInpatientDoctorStationMedicationAdviceGroupsResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     *
     * <p>写接口：仅允许长期药品/诊疗停嘱；临时医嘱须走 DELETE 或签退路径。
     * 按行分轨调用域 {@code stopLongTermMedicationOrder} / {@code stopServiceRequest}。</p>
     *
     * @param request 停嘱行列表（含产品表名与请求 id）及就诊上下文
     * @return 成功标记
     * @throws BusinessException 非长期医嘱、行列表为空或门控失败
     */
    public StopLongTermInpatientDoctorStationAdviceResponse stopLongTermInpatientDoctorStationAdvice(
        StopLongTermInpatientDoctorStationAdviceRequest request
    ) {
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_STOP_LONG_TERM_LINES_REQUIRED);
        }
        for (StopLongTermAdviceLineRequest line : request.getLines()) {
            String pt = normalizeProductTableName(line.getProductTableName());
            if (isMedicationProductTable(pt)) {
                assertLongTermMedication(line.getRequestId());
                medicationRequestDomainService.stopLongTermMedicationOrder(
                    inpatientDoctorStationAdviceMapper.toStopLongTermMedicationOrderCommand(line, operatorId));
            } else if (isActivityProductTable(pt)) {
                assertLongTermService(request.getEncounterId(), line.getRequestId());
                serviceRequestDomainService.stopServiceRequest(
                    inpatientDoctorStationAdviceMapper.toStopServiceRequestCommand(line, operatorId));
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + line.getProductTableName());
            }
        }
        StopLongTermInpatientDoctorStationAdviceResponse resp = new StopLongTermInpatientDoctorStationAdviceResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     *
     * <p>写接口：创建出院类 ServiceRequest 并记录就诊历程出院里程碑；
     * 同一 App 事务内完成，重复转科/出院申请会被拒绝。</p>
     *
     * @param request 出院医嘱内容与就诊上下文
     * @return 新建的诊疗申请 id 与就诊历程 id
     * @throws BusinessException 重复出院/转科申请或门控失败
     */
    public CreateDischargeInpatientDoctorStationAdviceResponse createDischargeInpatientDoctorStationAdvice(
        CreateDischargeInpatientDoctorStationAdviceRequest request
    ) {
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        if (Boolean.TRUE.equals(serviceRequestQueryService.checkDuplicateTransferOrDischargeRequest(request.getEncounterId()))) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_DUPLICATE_TRANSFER_OR_DISCHARGE_REQUEST);
        }
        Long serviceRequestId = serviceRequestDomainService.createDischargeRequest(
            inpatientDoctorStationAdviceMapper.toCreateDischargeRequestCommand(request, operatorId));
        Long encounterHistoryId = encounterHistoryDomainService.recordDischargeMilestone(
            inpatientDoctorStationAdviceMapper.toRecordDischargeMilestoneCommand(serviceRequestId, request, operatorId));
        return inpatientDoctorStationAdviceMapper.toCreateDischargeResponse(serviceRequestId, encounterHistoryId);
    }

    /**
     * 住院医生站统一门控：加载快照并校验患者、IMP、可选可写态。
     *
     * @param encounterId 就诊主键
     * @param patientId   患者主键（与卡片一致）
     * @param writable    为 {@code true} 时额外校验 IN_PROGRESS/ON_HOLD
     * @return 住院信息读模型快照
     */
    private InpatientEncounterContextQueryModel requireInpatientSnapshot(Long encounterId, Long patientId, boolean writable) {
        InpatientEncounterContextQueryModel snapshot =
            encounterContextFacade.mustGetInpatientContextByEncounterId(encounterId);
        inpatientDoctorStationAdviceMapper.validateInpatientEncounterPatientGate(snapshot, patientId);
        inpatientDoctorStationAdviceMapper.validateInpatientEncounterClassImp(snapshot);
        if (writable) {
            inpatientDoctorStationAdviceMapper.validateInpatientEncounterWritableGate(snapshot);
        }
        return snapshot;
    }

    /**
     * 暂存批次中的 CREATE 行：按产品表名分轨创建药/诊草稿（长临均不挂费用草稿）。
     */
    private void handleCreateLine(
        SaveInpatientDoctorStationAdviceDraftBatchRequest request,
        InpatientDoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId,
        List<Long> newMedIds
    ) {
        String pt = normalizeProductTableName(line.getProductTableName());
        if (isMedicationProductTable(pt)) {
            Long newId = medicationRequestDomainService.createMedicationOrderDraft(
                inpatientDoctorStationAdviceMapper.toCreateMedicationOrderDraftCommand(request, line, operatorId, orgId));
            if (line.getMedicationPayload() != null && line.getMedicationPayload().getGroupId() != null) {
                medicationRequestDomainService.adjustMedicationOrderGroup(
                    inpatientDoctorStationAdviceMapper.toAdjustMedicationGroupCommand(
                        List.of(newId), line.getMedicationPayload().getGroupId(), operatorId));
            }
            newMedIds.add(newId);
        } else if (isActivityProductTable(pt)) {
            if (line.getServicePayload() == null || line.getServicePayload().getActivityDefinitionId() == null) {
                throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_SERVICE_PAYLOAD_OR_ACTIVITY_ID_REQUIRED);
            }
            ProductDefinitionDisplayDto product = loadSingleProduct(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId());
            serviceRequestDomainService.createServiceRequestDraft(
                inpatientDoctorStationAdviceMapper.toCreateServiceRequestDraftCommand(request, line, product, operatorId));
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + line.getProductTableName());
        }
    }

    /**
     * 暂存批次中的 UPDATE 行：更新草稿（长临均不挂费用草稿）。
     */
    private void handleUpdateLine(
        SaveInpatientDoctorStationAdviceDraftBatchRequest request,
        InpatientDoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId
    ) {
        String pt = normalizeProductTableName(line.getProductTableName());
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UPDATE_LINE_REQUEST_ID_REQUIRED);
        }
        if (isMedicationProductTable(pt)) {
            medicationRequestDomainService.changeMedicationOrderDraft(
                inpatientDoctorStationAdviceMapper.toChangeMedicationOrderDraftCommand(line, operatorId));
        } else if (isActivityProductTable(pt)) {
            serviceRequestDomainService.modifyServiceRequestDraft(
                inpatientDoctorStationAdviceMapper.toModifyServiceRequestDraftCommand(line, operatorId));
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + line.getProductTableName());
        }
    }

    /**
     * 暂存批次中的 DELETE 行：软删请求草稿（长临均不在暂存挂费）。
     */
    private void handleDeleteLine(
        SaveInpatientDoctorStationAdviceDraftBatchRequest request,
        InpatientDoctorStationAdviceDraftLineRequest line,
        Long operatorId
    ) {
        String pt = normalizeProductTableName(line.getProductTableName());
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_DELETE_LINE_REQUEST_ID_REQUIRED);
        }
        if (isMedicationProductTable(pt)) {
            medicationRequestDomainService.removeMedicationOrderDraft(
                inpatientDoctorStationAdviceMapper.toRemoveMedicationOrderDraftCommand(line.getRequestId(), operatorId));
        } else if (isActivityProductTable(pt)) {
            serviceRequestDomainService.removeServiceRequestDraft(
                inpatientDoctorStationAdviceMapper.toRemoveServiceRequestDraftCommand(line.getRequestId(), operatorId));
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + line.getProductTableName());
        }
    }

    /**
     * 签发单个药品目标：长临均仅 sign，不建发放与费用（BR-IP-ORD-005/006）。
     */
    private void signMedicationTarget(
        SignInpatientDoctorStationAdviceBatchRequest request,
        InpatientDoctorStationAdviceResourceKeyDto target,
        Long operatorId,
        Map<Long, String> rxMap
    ) {
        String prescriptionNo = businessSequenceService.getSeqByDay(
            AggregateTypeConstants.COMPOSITE_PRESCRIPTION, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
        medicationRequestDomainService.signMedicationOrder(
            inpatientDoctorStationAdviceMapper.toSignMedicationOrderCommand(
                target.getRequestId(), prescriptionNo, operatorId));
        rxMap.put(target.getRequestId(), prescriptionNo);
    }

    /**
     * 签发单个诊疗目标：域服务 sign 后触发 LIS/PACS 占位（不升位费用）。
     */
    private void signServiceTarget(
        SignInpatientDoctorStationAdviceBatchRequest request,
        InpatientDoctorStationAdviceResourceKeyDto target,
        Long operatorId
    ) {
        serviceRequestDomainService.signServiceRequest(
            inpatientDoctorStationAdviceMapper.toSignServiceRequestCommand(target.getRequestId(), operatorId));
        LisPacsAdviceApplyCommand stub = buildLisPacsApplyCommand(
            request.getEncounterId(), request.getPatientId(), target.getRequestId());
        lisPacsApplyGateway.submitIfLabOrImaging(stub);
    }

    /**
     * 在就诊下按医生开立来源检索诊疗申请，解析指定申请的长临属性 code。
     *
     * @param encounterId       就诊主键
     * @param serviceRequestId  诊疗申请主键
     * @return {@link TherapyType} 域 code 字符串
     * @throws BusinessException 未在医生开立来源列表中命中该申请时抛出
     */
    private String resolveServiceTherapyEnum(Long encounterId, Long serviceRequestId) {
        List<ServiceRequestListModel> rows = serviceRequestQueryService.listServiceRequestsByGenerateSource(
            encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        for (ServiceRequestListModel row : rows) {
            if (serviceRequestId.equals(row.getId())) {
                return row.getTherapyEnum();
            }
        }
        throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND, DoctorStationAdviceConstants.MSG_SERVICE_REQUEST_NOT_FOUND);
    }

    /**
     *
     * @param requestId 药品申请主键
     * @throws BusinessException 非长期医嘱时抛出
     */
    private void assertLongTermMedication(Long requestId) {
        MedicationRequestDetailQueryModel detail = medicationRequestQueryService.loadMedicationRequestById(requestId);
        if (!TherapyType.LONG_TERM.getCode().equals(detail.getTherapyEnum())) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_STOP_LONG_TERM_MEDICATION_ONLY);
        }
    }

    /**
     *
     * @param encounterId 就诊主键（用于列举医生开立来源申请）
     * @param requestId   诊疗申请主键
     * @throws BusinessException 非长期医嘱时抛出
     */
    private void assertLongTermService(Long encounterId, Long requestId) {
        String therapy = resolveServiceTherapyEnum(encounterId, requestId);
        if (!TherapyType.LONG_TERM.getCode().equals(therapy)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_STOP_LONG_TERM_SERVICE_ONLY);
        }
    }

    /**
     * 按产品表与主键加载单条产品定义读模型，供诊疗开立前校验与 Mapper 装配。
     *
     * @param table 产品来源表名
     * @param id    定义主键
     * @return 读模型
     * @throws BusinessException 未命中
     */
    private ProductDefinitionDisplayDto loadSingleProduct(String table, Long id) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(table);
        lookup.setProductDefinitionId(id);
        Map<String, ProductDefinitionDisplayDto> map = productDefinitionBatchLoader.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(table, id);
        ProductDefinitionDisplayDto row = map.get(key);
        if (row == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_ACTIVITY_PRODUCT_DEFINITION_NOT_FOUND);
        }
        return row;
    }

    /**
     * 规范化并校验长临医嘱类型 code（仅 LONG_TERM / TEMPORARY）。
     *
     * @param code 前端传入的长临 code
     * @return 去空白后的域 code
     * @throws BusinessException 空或非法 code 时抛出
     */
    private static String normalizeTherapyTypeCode(String code) {
        String trimmed = StringUtils.trimToEmpty(code);
        if (StringUtils.isBlank(trimmed)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_THERAPY_TYPE_REQUIRED);
        }
        if (TherapyType.LONG_TERM.getCode().equals(trimmed) || TherapyType.TEMPORARY.getCode().equals(trimmed)) {
            return trimmed;
        }
        throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
            DoctorStationAdviceConstants.MSG_INVALID_THERAPY_TYPE_PREFIX + trimmed);
    }

    /**
     * 规范化产品定义表名（去首尾空白，大小写比较由调用方 {@code equalsIgnoreCase} 处理）。
     *
     * @param productTableName 前端传入的物理表名
     * @return 去空白后的表名，可能为空串
     */
    private static String normalizeProductTableName(String productTableName) {
        return StringUtils.trimToEmpty(productTableName);
    }

    /**
     * 是否为药品定义表分支（住院医生站仅支持药+诊疗，无耗材开立）。
     *
     * @param productTableName 已规范化或原始表名
     * @return 药品定义为 {@code true}
     */
    private static boolean isMedicationProductTable(String productTableName) {
        return TableNameConstants.MED_MEDICATION_DEFINITION.equalsIgnoreCase(normalizeProductTableName(productTableName));
    }

    /**
     * 是否为诊疗活动定义表分支。
     *
     * @param productTableName 已规范化或原始表名
     * @return 诊疗定义为 {@code true}
     */
    private static boolean isActivityProductTable(String productTableName) {
        return TableNameConstants.WKF_ACTIVITY_DEFINITION.equalsIgnoreCase(normalizeProductTableName(productTableName));
    }

    private List<OptionDto> loadBodyStructureOptions() {
        List<BodyStructureListItemQueryModel> rows =
            bodyStructureQueryService.getList(null, true, Pageable.unpaged()).getContent();
        return OptionDtoAssembler.sortBySortAsc(
            OptionDtoAssembler.toOptionsWithSortFallback(
                rows,
                BodyStructureListItemQueryModel::getId,
                BodyStructureListItemQueryModel::getName,
                BodyStructureListItemQueryModel::getId,
                0L));
    }

    private List<OptionDto> loadSpecimenDefinitionOptions() {
        SearchSpecimenDefinitionQuery query = new SearchSpecimenDefinitionQuery();
        query.setLimit(500);
        List<SpecimenDefinitionSearchItemQueryModel> rows = specimenDefinitionQueryService.searchByKeyWord(query);
        return OptionDtoAssembler.sortBySortAsc(
            OptionDtoAssembler.toOptionsWithSortFallback(
                rows,
                SpecimenDefinitionSearchItemQueryModel::getId,
                SpecimenDefinitionSearchItemQueryModel::getName,
                SpecimenDefinitionSearchItemQueryModel::getId,
                0L));
    }

    private LisPacsAdviceApplyCommand buildLisPacsApplyCommand(Long encounterId, Long patientId, Long requestId) {
        LisPacsAdviceApplyCommand cmd = new LisPacsAdviceApplyCommand();
        cmd.setEncounterId(encounterId);
        cmd.setPatientId(patientId);
        cmd.setRequestId(requestId);
        Map<Long, List<ServiceRequestDetailListModel>> detailMap =
            serviceRequestQueryService.mapDetailListModelsByServiceRequestIds(List.of(requestId));
        cmd.setServiceRequestDetails(serviceApplyFormAssembler.toDetailSummaryDtos(
            ServiceApplyFormOrchestrationSupport.toDetailSummaryModels(detailMap.get(requestId))));
        return cmd;
    }
}
