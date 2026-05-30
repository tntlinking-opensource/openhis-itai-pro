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

import com.openhis.health.application.common.assembler.advice.ServiceAdviceDraftCommandSupport;
import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.inpatient.doctorstation.advice.assembler.InpatientDoctorStationAdviceCatalogAssembler;
import com.openhis.health.application.inpatient.doctorstation.advice.assembler.InpatientDoctorStationAdviceFifoAllocationAssembler;
import com.openhis.health.application.inpatient.doctorstation.advice.assembler.InpatientDoctorStationAdviceListAssembler;
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
import com.openhis.health.application.inpatient.doctorstation.advice.request.ListInpatientDoctorStationAdviceRequest;
import com.openhis.health.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceCatalogRequest;
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
import com.openhis.health.application.inpatient.doctorstation.advice.response.ListInpatientDoctorStationAdviceResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.PageInpatientDoctorStationAdviceCatalogResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.SaveInpatientDoctorStationAdviceDraftBatchResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.SignInpatientDoctorStationAdviceBatchResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.StopLongTermInpatientDoctorStationAdviceResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.UpdateInpatientDoctorStationMedicationAdviceGroupsResponse;
import com.openhis.health.application.inpatient.doctorstation.advice.response.WithdrawInpatientDoctorStationAdviceBatchResponse;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleListQueryModel;
import com.openhis.health.domain.custom.activityexecutionlocation.service.ActivityExecutionLocationQueryService;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryDomainService;
import com.openhis.health.domain.medication.medicationdispense.command.ReleaseDispenseReserveCommand;
import com.openhis.health.domain.medication.medicationdispense.command.ReplaceFifoMedicationDispenseDraftsCommand;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestDetailQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestFifoSignContext;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.application.common.assembler.order.InpatientOrderListMergeAssembler;
import com.openhis.health.application.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.application.querysupport.product.ProductDefinitionCatalogPageLoader;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.application.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院医生站医嘱开立应用编排。
 *
 * <p>草稿/签发：长期药品仅开立请求；临时药品签发走 FIFO 建发放与费用草稿。</p>
 * <p>停嘱/撤回：长临分轨更新请求与费用；出院医嘱一次性开立。</p>
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
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final AccountQueryService accountQueryService;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final BusinessSequenceService businessSequenceService;
    private final LisPacsApplyGateway lisPacsApplyGateway;
    private final CodeSystemQueryService codeSystemQueryService;
    private final InpatientDoctorStationAdviceFifoAllocationAssembler inpatientDoctorStationAdviceFifoAllocationAssembler;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ActivityExecutionLocationQueryService activityExecutionLocationQueryService;
    private final LocationQueryService locationQueryService;
    private final EncounterHistoryDomainService encounterHistoryDomainService;

    /**
     *
     * <p>只读接口：校验住院快照与患者卡片一致后，加载频次、给药途径等术语下拉，
     * 供前端渲染开立表单控件；不涉及写库。</p>
     *
     * @param request 就诊 id、患者 id（Gate 用）
     * @return 频次与给药途径 {@code OptionDto} 列表
     * @throws BusinessException 快照缺失或患者与读模型不一致
     */
    public InitInpatientDoctorStationAdviceResponse initInpatientDoctorStationAdvice(InitInpatientDoctorStationAdviceRequest request) {
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), false);
        InitInpatientDoctorStationAdviceResponse resp = new InitInpatientDoctorStationAdviceResponse();
        resp.setMedicationRateOptions(codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.RATE_CODE));
        resp.setMedicationRouteOptions(codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.METHOD_CODE));
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
        List<ExecutionRuleListQueryModel> ruleModels =
            activityExecutionLocationQueryService.listExecutionRulesByActivityDefinition(request.getActivityDefinitionId());
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
        Map<Long, String> orgNameById = locationQueryService.mapNamesByIds(orgIds);
        Map<Long, String> locNameById = locationQueryService.mapNamesByIds(locIds);
        List<ActivityExecutionLocationRuleRowDto> rows = new ArrayList<>();
        for (ExecutionRuleListQueryModel src : ruleModels) {
            ActivityExecutionLocationRuleRowDto line = new ActivityExecutionLocationRuleRowDto();
            line.setId(src.getId());
            line.setActivityDefinitionId(src.getActivityDefinitionId());
            line.setChargeItemDefinitionId(src.getChargeItemDefinitionId());
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
     *
     * <p>只读接口：以 OrderInfo 读模型（Q-ORD-007）为主数据源，叠加收费归并、术语与 FIFO 库位展示；
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
        Long itemId = inpatientDoctorStationAdviceMapper.parseOrderBindItemId(request.getItemNo(), request.getItemId());
        GetInpatientDoctorStationAdviceOrderBindInfoResponse resp = new GetInpatientDoctorStationAdviceOrderBindInfoResponse();
        resp.setBindRows(inpatientDoctorStationAdviceOrderBindQueryService.listBindings(
            request.getBindType(), itemId, request.getOrganizationUnitId()));
        return resp;
    }

    /**
     *
     * <p>写接口：按行操作码 CREATE/UPDATE/DELETE 分轨处理药品与诊疗草稿。
     * 临时医嘱与门诊一致——暂存挂费用草稿；长期药品仅维护 Request，不建发放（BR-IP-ORD-005）。
     * 单行失败则整批随事务回滚。</p>
     *
     * @param request 批次行列表与就诊上下文
     * @return 成功标记与本批新建药品请求 id
     * @throws BusinessException 可写态/操作人/自费账户/行操作码/定价等门控失败
     */
    public SaveInpatientDoctorStationAdviceDraftBatchResponse saveInpatientDoctorStationAdviceDraftBatch(
        SaveInpatientDoctorStationAdviceDraftBatchRequest request
    ) {
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        Long orgId = snapshot.getOrganizationId();
        Long accountId = accountQueryService.getSelfPayAccount(request.getEncounterId());
        if (accountId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_SELF_PAY_ACCOUNT_REQUIRED_FOR_DRAFT_CHARGE);
        }
        List<Long> newMedIds = new ArrayList<>();
        for (InpatientDoctorStationAdviceDraftLineRequest line : request.getLines()) {
            String op = StringUtils.upperCase(StringUtils.trimToEmpty(line.getLineOp()));
            if (DoctorStationAdviceConstants.LINE_OP_CREATE.equals(op)) {
                handleCreateLine(request, line, operatorId, orgId, accountId, newMedIds);
            } else if (DoctorStationAdviceConstants.LINE_OP_UPDATE.equals(op)) {
                handleUpdateLine(request, line, operatorId, orgId, accountId);
            } else if (DoctorStationAdviceConstants.LINE_OP_DELETE.equals(op)) {
                handleDeleteLine(request, line, operatorId);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_LINE_OP_PREFIX + line.getLineOp());
            }
        }
        SaveInpatientDoctorStationAdviceDraftBatchResponse resp = new SaveInpatientDoctorStationAdviceDraftBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setNewMedicationRequestIds(newMedIds);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     *
     * <p>写接口：按签发目标逐条 sign 药品或诊疗请求。
     * 临时药品含 FIFO 分段发放与费用升位；长期药品/诊疗仅签发请求，不建发放。
     * 未传批次号时按日取 {@link BusinessSequenceService} 序号。</p>
     *
     * @param request 签发目标列表、可选 {@code signBatchCode} 与就诊上下文
     * @return 签发批次号与药品处方号映射
     * @throws BusinessException 门控失败、FIFO/定价异常或产品表不支持
     */
    public SignInpatientDoctorStationAdviceBatchResponse signInpatientDoctorStationAdviceBatch(
        SignInpatientDoctorStationAdviceBatchRequest request
    ) {
        InpatientEncounterContextQueryModel snapshot = requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        Long orgId = snapshot.getOrganizationId();
        Long accountId = accountQueryService.getSelfPayAccount(request.getEncounterId());
        if (accountId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_SELF_PAY_ACCOUNT_REQUIRED_FOR_SIGN_CHARGE);
        }
        String signBatch;
        if (StringUtils.isNotBlank(request.getSignBatchCode())) {
            signBatch = request.getSignBatchCode();
        } else {
            signBatch = businessSequenceService.getSeqByDay(
                AggregateTypeConstants.MEDICATION_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
        }
        Map<Long, String> rxMap = new HashMap<>();
        for (InpatientDoctorStationAdviceResourceKeyDto target : request.getSignTargets()) {
            String pt = normalizeProductTableName(target.getProductTableName());
            if (isMedicationProductTable(pt)) {
                signMedicationTarget(request, target, signBatch, operatorId, orgId, accountId, rxMap);
            } else if (isActivityProductTable(pt)) {
                signServiceTarget(request, target, signBatch, operatorId);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + target.getProductTableName());
            }
        }
        SignInpatientDoctorStationAdviceBatchResponse resp = new SignInpatientDoctorStationAdviceBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setSignBatchCode(signBatch);
        resp.setPrescriptionNoByMedicationRequestId(rxMap);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     *
     * 长期药品仅 signOff 请求。长期诊疗签退同步回滚 PLANNED 费用。</p>
     *
     * @param request 签退目标列表与就诊上下文
     * @return 成功标记
     * @throws BusinessException 可写态/操作人门控失败或产品表不支持
     */
    public WithdrawInpatientDoctorStationAdviceBatchResponse withdrawInpatientDoctorStationAdviceBatch(
        WithdrawInpatientDoctorStationAdviceBatchRequest request
    ) {
        requireInpatientSnapshot(request.getEncounterId(), request.getPatientId(), true);
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_OPERATOR_NOT_RESOLVED);
        for (InpatientDoctorStationAdviceResourceKeyDto target : request.getTargets()) {
            String pt = normalizeProductTableName(target.getProductTableName());
            if (isMedicationProductTable(pt)) {
                MedicationRequestDetailQueryModel medDetail =
                    medicationRequestQueryService.loadMedicationRequestById(target.getRequestId());
                if (isTemporaryTherapy(medDetail.getTherapyEnum())) {
                    // 临时药品签退：与门诊药品分支同序（回滚 PLANNED 费 → signOff → 作废草稿费 → 释放发放）。
                    List<Long> medDispenseIds =
                        medicationDispenseQueryService.listMedicationDispenseIdsByMedicationRequest(target.getRequestId());
                    chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors(
                        request.getEncounterId(), TableNameConstants.MED_MEDICATION_DISPENSE, medDispenseIds);
                    chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                        request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, target.getRequestId());
                    medicationRequestDomainService.signOffMedicationOrder(
                        inpatientDoctorStationAdviceMapper.toSignOffMedicationOrderCommand(target.getRequestId(), operatorId));
                    chargeItemDomainService.voidUnbilledDraftChargeItemsByDispenseAnchors(
                        request.getEncounterId(), TableNameConstants.MED_MEDICATION_DISPENSE, medDispenseIds);
                    chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                        request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, List.of(target.getRequestId()));
                    ReleaseDispenseReserveCommand releaseMed = new ReleaseDispenseReserveCommand();
                    releaseMed.setMedReqId(target.getRequestId());
                    releaseMed.setOperatorId(operatorId);
                    medicationDispenseDomainService.releaseDispenseReserve(releaseMed);
                } else {
                    // 长期药品签退：仅 signOff 请求（住院特有，门诊无长临分轨）。
                    medicationRequestDomainService.signOffMedicationOrder(
                        inpatientDoctorStationAdviceMapper.toSignOffMedicationOrderCommand(target.getRequestId(), operatorId));
                }
            } else if (isActivityProductTable(pt)) {
                serviceRequestDomainService.withdrawSignedServiceRequest(
                    inpatientDoctorStationAdviceMapper.toWithdrawServiceRequestCommand(target.getRequestId(), operatorId));
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, target.getRequestId());
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
     * 暂存批次中的 CREATE 行：按产品表名分轨创建药/诊草稿；临时药品/诊疗挂费用草稿（与门诊一致，不发放在签发）。
     *
     * @param request    批次请求（就诊/患者上下文）
     * @param line       单行草稿载荷
     * @param operatorId 当前操作人
     * @param orgId      机构主键（来自住院快照）
     * @param accountId  自费账户（费用草稿锚点）
     * @param newMedIds  本批新建药品请求 id 收集器（响应回传前端）
     */
    private void handleCreateLine(
        SaveInpatientDoctorStationAdviceDraftBatchRequest request,
        InpatientDoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId,
        Long accountId,
        List<Long> newMedIds
    ) {
        String pt = normalizeProductTableName(line.getProductTableName());
        String therapyCode = normalizeTherapyTypeCode(line.getTherapyTypeCode());
        if (isMedicationProductTable(pt)) {
            // 药品轨：落草稿 → 可选并组；临时医嘱挂费用草稿（与门诊一致，暂存不建发放）。
            Long newId = medicationRequestDomainService.createMedicationOrderDraft(
                inpatientDoctorStationAdviceMapper.toCreateMedicationOrderDraftCommand(request, line, operatorId, orgId));
            if (line.getMedicationPayload() != null && line.getMedicationPayload().getGroupId() != null) {
                medicationRequestDomainService.adjustMedicationOrderGroup(
                    inpatientDoctorStationAdviceMapper.toAdjustMedicationGroupCommand(
                        List.of(newId), line.getMedicationPayload().getGroupId(), operatorId));
            }
            if (isTemporaryTherapy(therapyCode) && line.getMedicationPayload() != null) {
                createChargeIfPossible(request, newId, TableNameConstants.MED_MEDICATION_REQUEST,
                    TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId(),
                    line.getMedicationPayload().getQuantity(), line.getMedicationPayload().getUnitCode(),
                    loadSingleProduct(TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId()),
                    accountId, orgId, operatorId);
            }
            newMedIds.add(newId);
        } else if (isActivityProductTable(pt)) {
            // 诊疗轨：锚定活动定义；临时医嘱同步挂单笔费用草稿。
            if (line.getServicePayload() == null || line.getServicePayload().getActivityDefinitionId() == null) {
                throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_SERVICE_PAYLOAD_OR_ACTIVITY_ID_REQUIRED);
            }
            ProductDefinitionDisplayDto product = loadSingleProduct(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId());
            Long newId = serviceRequestDomainService.createServiceRequestDraft(
                inpatientDoctorStationAdviceMapper.toCreateServiceRequestDraftCommand(request, line, product, operatorId));
            if (isTemporaryTherapy(therapyCode)) {
                createChargeIfPossible(request, newId, TableNameConstants.WKF_SERVICE_REQUEST,
                    TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId(),
                    ServiceAdviceDraftCommandSupport.requireQuantity(
                        line.getServicePayload().getQuantity(), line.getServicePayload().getChildOverridesJson()),
                    ServiceAdviceDraftCommandSupport.requireUnitCode(
                        line.getServicePayload().getUnitCode(), line.getServicePayload().getChildOverridesJson(), product),
                    product, accountId, orgId, operatorId);
            }
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + line.getProductTableName());
        }
    }

    /**
     * 暂存批次中的 UPDATE 行：更新草稿；临时药品/诊疗与门诊一致作废旧费后重挂费用草稿。
     *
     * @param request    批次请求
     * @param line       单行 UPDATE 载荷
     * @param operatorId 操作人
     * @param orgId      机构
     * @param accountId  自费账户
     * @throws BusinessException 缺少 requestId、定价缺失或产品表不支持时抛出
     */
    private void handleUpdateLine(
        SaveInpatientDoctorStationAdviceDraftBatchRequest request,
        InpatientDoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId,
        Long accountId
    ) {
        String pt = normalizeProductTableName(line.getProductTableName());
        String therapyCode = normalizeTherapyTypeCode(line.getTherapyTypeCode());
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UPDATE_LINE_REQUEST_ID_REQUIRED);
        }
        if (isMedicationProductTable(pt)) {
            // 临时药品 UPDATE：作废旧草稿费用 → 改草稿 → 重挂费用（与门诊一致，不涉及发放）。
            if (isTemporaryTherapy(therapyCode)) {
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                    inpatientDoctorStationAdviceMapper.toVoidDraftChargeAnchor(
                        request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, line.getRequestId(), operatorId));
            }
            medicationRequestDomainService.changeMedicationOrderDraft(
                inpatientDoctorStationAdviceMapper.toChangeMedicationOrderDraftCommand(line, operatorId));
            if (isTemporaryTherapy(therapyCode) && line.getMedicationPayload() != null) {
                createChargeIfPossible(request, line.getRequestId(), TableNameConstants.MED_MEDICATION_REQUEST,
                    TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId(),
                    line.getMedicationPayload().getQuantity(), line.getMedicationPayload().getUnitCode(),
                    loadSingleProduct(TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId()),
                    accountId, orgId, operatorId);
            }
        } else if (isActivityProductTable(pt)) {
            serviceRequestDomainService.modifyServiceRequestDraft(
                inpatientDoctorStationAdviceMapper.toModifyServiceRequestDraftCommand(line, operatorId));
            if (isTemporaryTherapy(therapyCode)) {
                if (line.getServicePayload() == null || line.getServicePayload().getActivityDefinitionId() == null) {
                    throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_SERVICE_PAYLOAD_OR_ACTIVITY_ID_REQUIRED);
                }
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                    inpatientDoctorStationAdviceMapper.toVoidDraftChargeAnchor(
                        request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, line.getRequestId(), operatorId));
                ProductDefinitionDisplayDto product = loadSingleProduct(
                    TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId());
                createChargeIfPossible(request, line.getRequestId(), TableNameConstants.WKF_SERVICE_REQUEST,
                    TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId(),
                    ServiceAdviceDraftCommandSupport.requireQuantity(
                        line.getServicePayload().getQuantity(), line.getServicePayload().getChildOverridesJson()),
                    ServiceAdviceDraftCommandSupport.requireUnitCode(
                        line.getServicePayload().getUnitCode(), line.getServicePayload().getChildOverridesJson(), product),
                    product, accountId, orgId, operatorId);
            }
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + line.getProductTableName());
        }
    }

    /**
     * 暂存批次中的 DELETE 行：临时医嘱作废未记账费用后软删请求；长期医嘱仅软删请求（暂存未挂费）。
     *
     * @param request    批次请求
     * @param line       单行 DELETE 载荷
     * @param operatorId 操作人
     * @throws BusinessException 缺少 requestId 或产品表不支持时抛出
     */
    private void handleDeleteLine(
        SaveInpatientDoctorStationAdviceDraftBatchRequest request,
        InpatientDoctorStationAdviceDraftLineRequest line,
        Long operatorId
    ) {
        String pt = normalizeProductTableName(line.getProductTableName());
        String therapyCode = normalizeTherapyTypeCode(line.getTherapyTypeCode());
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_DELETE_LINE_REQUEST_ID_REQUIRED);
        }
        if (isMedicationProductTable(pt)) {
            // 临时药品 DELETE：作废草稿费用 → 软删请求（与门诊一致）；长期仅删请求。
            if (isTemporaryTherapy(therapyCode)) {
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                    inpatientDoctorStationAdviceMapper.toVoidDraftChargeAnchor(
                        request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, line.getRequestId(), operatorId));
            }
            medicationRequestDomainService.removeMedicationOrderDraft(
                inpatientDoctorStationAdviceMapper.toRemoveMedicationOrderDraftCommand(line.getRequestId(), operatorId));
        } else if (isActivityProductTable(pt)) {
            if (isTemporaryTherapy(therapyCode)) {
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                    inpatientDoctorStationAdviceMapper.toVoidDraftChargeAnchor(
                        request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, line.getRequestId(), operatorId));
            }
            serviceRequestDomainService.removeServiceRequestDraft(
                inpatientDoctorStationAdviceMapper.toRemoveServiceRequestDraftCommand(line.getRequestId(), operatorId));
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, DoctorStationAdviceConstants.MSG_UNSUPPORTED_PRODUCT_TABLE_PREFIX + line.getProductTableName());
        }
    }

    /**
     * 签发单个药品目标：长期仅 sign；临时含费用作废、FIFO 分段、按发放锚点升 PLANNED。
     */
    private void signMedicationTarget(
        SignInpatientDoctorStationAdviceBatchRequest request,
        InpatientDoctorStationAdviceResourceKeyDto target,
        String signBatch,
        Long operatorId,
        Long orgId,
        Long accountId,
        Map<Long, String> rxMap
    ) {
        MedicationRequestDetailQueryModel detail = medicationRequestQueryService.loadMedicationRequestById(target.getRequestId());
        String prescriptionNo = businessSequenceService.getSeqByDay(
            AggregateTypeConstants.MEDICATION_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
        medicationRequestDomainService.signMedicationOrder(
            inpatientDoctorStationAdviceMapper.toSignMedicationOrderCommand(
                target.getRequestId(), signBatch, prescriptionNo, operatorId));
        rxMap.put(target.getRequestId(), prescriptionNo);
        // 长期医嘱签发后不建发放、不升位费用（BR-IP-ORD-005）。
        if (!isTemporaryTherapy(detail.getTherapyEnum())) {
            return;
        }
        // 临时：作废请求锚点暂存费 → FIFO 分段发放 → 按发放锚点升 PLANNED。
        chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
            request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, List.of(target.getRequestId()));
        MedicationRequestFifoSignContext fifoCtx = medicationRequestQueryService.loadFifoSignContextOrThrow(target.getRequestId());
        List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> segments =
            inpatientDoctorStationAdviceFifoAllocationAssembler.allocateMedicationFifo(
                orgId, fifoCtx.getEncounterId(), fifoCtx.getMedicationId(), fifoCtx.getCatalogCategoryCode(),
                fifoCtx.getUnitCode(), fifoCtx.getQuantity());
        ReplaceFifoMedicationDispenseDraftsCommand fifoCmd = new ReplaceFifoMedicationDispenseDraftsCommand();
        fifoCmd.setOperatorId(operatorId);
        fifoCmd.setMedReqId(fifoCtx.getMedReqId());
        fifoCmd.setMedicationId(fifoCtx.getMedicationId());
        fifoCmd.setEncounterId(fifoCtx.getEncounterId());
        fifoCmd.setPatientId(fifoCtx.getPatientId());
        fifoCmd.setUnitCode(fifoCtx.getUnitCode());
        fifoCmd.setDispenseEnum(MedicationDispenseType.INPATIENT);
        fifoCmd.setSegments(segments);
        List<Long> dispenseIds = medicationDispenseDomainService.replaceFifoDraftDispensesAfterDoctorStationSign(fifoCmd);
        List<BigDecimal> qtys = new ArrayList<>();
        for (ReplaceFifoMedicationDispenseDraftsCommand.Segment seg : segments) {
            qtys.add(seg.getQuantity());
        }
        createDispenseSegmentChargeDrafts(
            request.getEncounterId(), request.getPatientId(), accountId, orgId, operatorId,
            fifoCtx.getMedReqId(), TableNameConstants.MED_MEDICATION_REQUEST,
            TableNameConstants.MED_MEDICATION_DEFINITION, fifoCtx.getMedicationId(),
            TableNameConstants.MED_MEDICATION_DISPENSE, dispenseIds, qtys, fifoCtx.getUnitCode());
        chargeItemDomainService.planDraftChargeItemsAfterAdviceSignedByDispenseAnchors(
            request.getEncounterId(), TableNameConstants.MED_MEDICATION_DISPENSE, dispenseIds);
    }

    /**
     * 签发单个诊疗目标：域服务 sign 后触发 LIS/PACS 占位；临时诊疗升位费用草稿。
     */
    private void signServiceTarget(
        SignInpatientDoctorStationAdviceBatchRequest request,
        InpatientDoctorStationAdviceResourceKeyDto target,
        String signBatch,
        Long operatorId
    ) {
        // 诊疗签发：域 sign → LIS/PACS 占位外呼 → 临时医嘱升位费用。
        serviceRequestDomainService.signServiceRequest(
            inpatientDoctorStationAdviceMapper.toSignServiceRequestCommand(target.getRequestId(), signBatch, operatorId));
        LisPacsAdviceApplyCommand stub = new LisPacsAdviceApplyCommand();
        stub.setEncounterId(request.getEncounterId());
        stub.setPatientId(request.getPatientId());
        lisPacsApplyGateway.submitIfLabOrImaging(stub);
        String therapy = resolveServiceTherapyEnum(request.getEncounterId(), target.getRequestId());
        if (isTemporaryTherapy(therapy)) {
            chargeItemDomainService.planDraftChargeItemsAfterAdviceSigned(
                request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, target.getRequestId());
        }
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
     * 在存在定义主键时尝试挂一笔未计费费用草稿（临时医嘱/诊疗暂存路径，与门诊一致）。
     *
     * @param request          批次父上下文（就诊/患者）
     * @param requestId        医嘱申请主键
     * @param requestTable     医嘱请求物理表名
     * @param definitionTable  定价实例表（药品/诊疗定义表）
     * @param definitionId     定义主键；为 {@code null} 时不查询定价、不生成费用
     * @param quantity         计价数量
     * @param quantityUnit       计价单位 code
     * @param productRow         产品定义读模型
     * @param accountId        自费账户
     * @param orgId            机构
     * @param operatorId       操作人
     * @throws BusinessException 定义主键已给出但无可用开立口径定价行时抛出
     */
    private void createChargeIfPossible(
        SaveInpatientDoctorStationAdviceDraftBatchRequest request,
        Long requestId,
        String requestTable,
        String definitionTable,
        Long definitionId,
        BigDecimal quantity,
        String quantityUnit,
        ProductDefinitionDisplayDto productRow,
        Long accountId,
        Long orgId,
        Long operatorId
    ) {
        if (definitionId == null) {
            return;
        }
        ChargeItemDefinition priceLine = firstPriceLine(definitionTable, definitionId);
        if (priceLine == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHARGE_DEFINITION_MISSING_FOR_SAVE);
        }
        chargeItemDomainService.createDraftChargeItemFromOrder(
            inpatientDoctorStationAdviceMapper.toCreateChargeItemDraftFromOrder(
                request.getEncounterId(), request.getPatientId(), accountId, orgId, operatorId,
                requestId, requestTable, definitionTable, definitionId, priceLine, quantity, quantityUnit, productRow));
    }

    /**
     * 按 FIFO 发放分段批量生成费用草稿：单次解析定价、单次调用域批量落库。
     *
     * @param encounterId            就诊主键
     * @param patientId              患者主键
     * @param accountId              自费账户
     * @param orgId                  开立机构
     * @param operatorId             操作人
     * @param requestId              医嘱申请主键（写入 {@code service_id}）
     * @param requestServiceTable    医嘱请求物理表名
     * @param productDefinitionTable 药品定义表（写入 {@code product_table}）
     * @param productDefinitionId    定义主键
     * @param dispenseTable          发放物理表名
     * @param dispenseIds            与各分段顺序一致的 {@code dispense_id} 列表
     * @param segmentQuantities      与各分段顺序一致的计价数量列表
     * @param quantityUnit           计价单位 code（与医嘱开立单位一致）
     * @throws BusinessException 分段条数与数量列表长度不一致，或缺少批号售价定价行时抛出
     */
    private void createDispenseSegmentChargeDrafts(
        Long encounterId,
        Long patientId,
        Long accountId,
        Long orgId,
        Long operatorId,
        Long requestId,
        String requestServiceTable,
        String productDefinitionTable,
        Long productDefinitionId,
        String dispenseTable,
        List<Long> dispenseIds,
        List<BigDecimal> segmentQuantities,
        String quantityUnit
    ) {
        if (dispenseIds == null || segmentQuantities == null || dispenseIds.size() != segmentQuantities.size()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_DISPENSE_SEGMENT_CHARGE_COUNT_MISMATCH);
        }
        if (dispenseIds.isEmpty()) {
            return;
        }
        // 签发批量费用草稿：须与目录/暂存同一「批号售价」口径命中定价行。
        ChargeItemDefinition priceLine = firstPriceLine(productDefinitionTable, productDefinitionId);
        if (priceLine == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                DoctorStationAdviceConstants.MSG_CHARGE_DEFINITION_MISSING_LOT_NUMBER_PRICE);
        }
        ProductDefinitionDisplayDto productRow = loadSingleProduct(productDefinitionTable, productDefinitionId);
        List<CreateChargeItemCommand.Item> draftItems = new ArrayList<>();
        // 每一发放分段一行 ChargeItem：service→医嘱请求，product→定义，dispense→本条分段（DM-ADM-011）。
        for (int i = 0; i < dispenseIds.size(); i++) {
            draftItems.add(inpatientDoctorStationAdviceMapper.toCreateChargeItemDraftItemForFifoDispenseSegment(
                encounterId, patientId, accountId, orgId, requestId, requestServiceTable,
                productDefinitionTable, productDefinitionId, dispenseTable, dispenseIds.get(i), priceLine,
                segmentQuantities.get(i), quantityUnit, productRow));
        }
        CreateChargeItemCommand batchCmd = new CreateChargeItemCommand();
        batchCmd.setOperatorId(operatorId);
        batchCmd.setItems(draftItems);
        chargeItemDomainService.createDraftChargeItemFromOrder(batchCmd);
    }

    /**
     *
     * @param instanceTable 定价实例表
     * @param instanceId    定价实例主键；{@code null} 时不查询
     * @return 用于草稿金额的定价行；无记录时 {@code null}
     */
    private ChargeItemDefinition firstPriceLine(String instanceTable, Long instanceId) {
        if (instanceId == null) {
            return null;
        }
        ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
        priceQuery.setInstanceTable(instanceTable);
        priceQuery.setInstanceIds(List.of(instanceId));
        List<ChargeItemDefinition> priceLines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
        return InpatientDoctorStationAdviceCatalogAssembler.selectFirstPriceLineForChargeDraft(instanceTable, priceLines);
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
     * 判断是否为临时医嘱（与门诊开立等价：暂存挂费、签发 FIFO 建发放与费用升位）。
     *
     * @param therapyCode 长临 code
     * @return 临时医嘱为 {@code true}
     */
    private static boolean isTemporaryTherapy(String therapyCode) {
        return TherapyType.TEMPORARY.getCode().equals(therapyCode);
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
}
