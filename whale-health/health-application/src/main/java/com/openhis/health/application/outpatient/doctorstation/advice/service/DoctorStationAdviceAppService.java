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

package com.openhis.health.application.outpatient.doctorstation.advice.service;

import com.openhis.health.application.common.assembler.advice.ServiceAdviceDraftCommandSupport;
import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.DoctorStationAdviceCatalogAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.DoctorStationAdviceFifoAllocationAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.DoctorStationAdviceListAssembler;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceCatalogRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftLineRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceResourceKeyDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.mapper.DoctorStationAdviceMapper;
import com.openhis.health.application.outpatient.doctorstation.advice.port.LisPacsAdviceApplyCommand;
import com.openhis.health.application.outpatient.doctorstation.advice.port.LisPacsApplyGateway;
import com.openhis.health.application.outpatient.doctorstation.advice.query.DoctorStationAdviceOrderBindQueryService;
import com.openhis.health.application.outpatient.doctorstation.advice.request.GetDoctorStationAdviceOrderBindInfoRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.InitDoctorStationAdviceRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.ListActivityExecutionLocationsRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.ListDoctorStationAdviceRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.PageDoctorStationAdviceCatalogRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.SaveDoctorStationAdviceDraftBatchRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.SignDoctorStationAdviceBatchRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.UpdateDoctorStationMedicationAdviceGroupsRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.request.WithdrawDoctorStationAdviceBatchRequest;
import com.openhis.health.application.outpatient.doctorstation.advice.response.GetDoctorStationAdviceOrderBindInfoResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.InitDoctorStationAdviceResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.ActivityExecutionLocationRuleRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.response.ListActivityExecutionLocationsResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.ListDoctorStationAdviceResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.PageDoctorStationAdviceCatalogResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.SaveDoctorStationAdviceDraftBatchResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.SignDoctorStationAdviceBatchResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.UpdateDoctorStationMedicationAdviceGroupsResponse;
import com.openhis.health.application.outpatient.doctorstation.advice.response.WithdrawDoctorStationAdviceBatchResponse;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.medication.medicationdispense.command.ReleaseDispenseReserveCommand;
import com.openhis.health.domain.medication.medicationdispense.command.ReplaceFifoMedicationDispenseDraftsCommand;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestFifoSignContext;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleListQueryModel;
import com.openhis.health.domain.custom.activityexecutionlocation.service.ActivityExecutionLocationQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.application.querysupport.product.ProductDefinitionCatalogPageLoader;
import com.openhis.health.application.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.devicedispense.command.ReleaseDeviceDispenseReserveCommand;
import com.openhis.health.domain.workflow.devicedispense.command.ReplaceFifoDeviceDispenseDraftsCommand;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestFifoSignContext;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
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
 * 门诊医生站医嘱开立编排：统一列表、目录检索、开立/停嘱/作废与绑定占位。
 */
@Service
@RequiredArgsConstructor
public class DoctorStationAdviceAppService {

    private final CurrentOperatorResolver currentOperatorResolver;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final DoctorStationAdviceMapper doctorStationAdviceMapper;
    private final DoctorStationAdviceListAssembler doctorStationAdviceListAssembler;
    private final DoctorStationAdviceCatalogAssembler doctorStationAdviceCatalogAssembler;
    private final DoctorStationAdviceOrderBindQueryService doctorStationAdviceOrderBindQueryService;
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final AccountQueryService accountQueryService;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final DeviceRequestDomainService deviceRequestDomainService;
    private final BusinessSequenceService businessSequenceService;
    private final LisPacsApplyGateway lisPacsApplyGateway;
    private final CodeSystemQueryService codeSystemQueryService;
    private final DoctorStationAdviceFifoAllocationAssembler doctorStationAdviceFifoAllocationAssembler;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final ActivityExecutionLocationQueryService activityExecutionLocationQueryService;
    private final LocationQueryService locationQueryService;

    /**
     * 医嘱开立页初始化：用药频次、用法（给药途径）术语下拉（费用性质选项由就诊管理
     * {@code pageOutpatientInfoForDoctorStation} 响应字段 {@code feeNatureOptions} 一并提供）。
     *
     * @param request 就诊与患者门控
     * @return 术语类初始化数据
     * @throws BusinessException 就诊不存在或患者不一致
     */
    public InitDoctorStationAdviceResponse initDoctorStationAdvice(InitDoctorStationAdviceRequest request) {
        // 门控：与列表/目录一致，先加载门诊快照并校验患者卡片与就诊绑定一致。
        OutpatientEncounterContextQueryModel snapshot = encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        // 前置：频次与给药途径走术语缓存全量下拉，体量受字典种子约束。
        InitDoctorStationAdviceResponse resp = new InitDoctorStationAdviceResponse();
        resp.setMedicationRateOptions(codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.RATE_CODE));
        resp.setMedicationRouteOptions(codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.METHOD_CODE));
        return resp;
    }

    /**
     * 按诊疗定义查询可执行科室规则（执行科室/地点展示名批量解析）。
     *
     * @param request 就诊、患者与诊疗定义门控
     * @return 规则列表（含展示名）
     * @throws BusinessException 就诊不存在或患者与就诊不一致
     */
    public ListActivityExecutionLocationsResponse listActivityExecutionLocations(ListActivityExecutionLocationsRequest request) {
        OutpatientEncounterContextQueryModel snapshot =
            encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
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
            } else {
                line.setExecutionOrgName(null);
            }
            line.setExecutionLocationId(src.getExecutionLocationId());
            if (src.getExecutionLocationId() != null) {
                line.setExecutionLocationName(locNameById.get(src.getExecutionLocationId()));
            } else {
                line.setExecutionLocationName(null);
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
     * 加载当前就诊医嘱统一列表。
     *
     * @param request 就诊、患者、仅本人过滤
     * @return 合并药品/诊疗/耗材行与收费态汇总
     * @throws BusinessException 就诊不存在或患者与就诊不一致
     */
    public ListDoctorStationAdviceResponse listDoctorStationAdvice(ListDoctorStationAdviceRequest request) {
        // 门控：必须先有有效门诊读模型行，再比对请求中的患者，防止跨患者卡片拉医嘱。
        OutpatientEncounterContextQueryModel snapshot = encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        Long uid = null;
        if (Boolean.TRUE.equals(request.getOnlyMine())) {
            uid = currentOperatorResolver.findOperatorIdOrNull();
        }
        // 装配：三类申请与收费态归并由装配器一次性完成，本方法不写库、不签发。
        List<DoctorStationAdviceRowDto> rows =
            doctorStationAdviceListAssembler.assembleRows(
                request.getEncounterId(), request.getPatientId(), request.getOnlyMine(), uid);
        // —— B.10.11：使用 PagedResultBuilder 构建响应，保持 Response 类纯粹性。——
        return PagedResultBuilder.buildList(new ListDoctorStationAdviceResponse(), rows);
    }

    /**
     * 分页检索可开立医嘱项目目录。
     *
     * @param request 分页条件、定价来源、药房库位等
     * @return 当前页目录行（含库存预减、定价 JSON）
     * @throws BusinessException 就诊不存在或患者与就诊不一致
     */
    public PageDoctorStationAdviceCatalogResponse pageDoctorStationAdviceCatalog(PageDoctorStationAdviceCatalogRequest request) {
        OutpatientEncounterContextQueryModel snapshot = encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        // 前置：将前端分页/筛选参数转为读模型分页命令（定价来源、关键字等下推至 RM 查询）。
        ProductDefinitionPageQuery pageQuery = doctorStationAdviceMapper.toProductDefinitionPageQuery(request);
        Page<ProductDefinitionDisplayDto> page = productDefinitionCatalogPageLoader.pageProductDefinition(pageQuery);
        // 装配：目录行（读模型已分页，装配器不再发起分页查询）
        List<DoctorStationAdviceCatalogRowDto> rows =
            doctorStationAdviceCatalogAssembler.assemblePageContent(request, page, snapshot);
        // —— B.10.11：行类型与 Page 元素类型不同，用 build(response, dtoRows, page) 取 Page 元数据 + 已映射 content。——
        return PagedResultBuilder.build(new PageDoctorStationAdviceCatalogResponse(), rows, page);
    }

    /**
     * 查询医嘱绑定信息（当前为占位查询服务，返回空列表亦可）。
     *
     * @param request 绑定类型、项目主键/业务号、组织单元
     * @return 绑定行集合
     */
    public GetDoctorStationAdviceOrderBindInfoResponse getDoctorStationAdviceOrderBindInfo(GetDoctorStationAdviceOrderBindInfoRequest request) {
        // 前置：业务号与数字主键二选一解析为绑定查询用的 itemId（具体优先级见 Mapper）。
        Long itemId = doctorStationAdviceMapper.parseOrderBindItemId(request.getItemNo(), request.getItemId());
        GetDoctorStationAdviceOrderBindInfoResponse resp = new GetDoctorStationAdviceOrderBindInfoResponse();
        // 装配：绑定行当前可为占位空列表，接口形状保持稳定供前端联调。
        resp.setBindRows(doctorStationAdviceOrderBindQueryService.listBindings(request.getBindType(), itemId, request.getOrganizationUnitId()));
        return resp;
    }

    /**
     * 暂存医嘱变更批次（新建/更新/删除行混合）。
     *
     * @param request 就诊、患者、行集合
     * @return 成功标记与新建药品医嘱主键列表
     * @throws BusinessException 门控失败、自费账户缺失、行操作或资源类型非法、域校验失败
     */
    public SaveDoctorStationAdviceDraftBatchResponse saveDoctorStationAdviceDraftBatch(SaveDoctorStationAdviceDraftBatchRequest request) {
        // 门控：加载门诊快照并校验患者上下文一致
        OutpatientEncounterContextQueryModel snapshot = encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        // 解析当前操作人主键
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        Long orgId = snapshot.getOrganizationId();
        // 前置：费用草稿挂账锚点为就诊默认个人现金账户（AccountTypeCode.PERSONAL_CASH_ACCOUNT，与挂号开户一致），缺失才拒绝整批暂存。
        Long accountId = accountQueryService.getSelfPayAccount(request.getEncounterId());
        // 自费账户缺失时阻断整批暂存
        if (accountId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊未解析到自费账户，无法生成费用草稿");
        }
        // 收集新建药品医嘱主键，供前端局部刷新
        List<Long> newMedIds = new ArrayList<>();
        // 命令：按行操作码分发 CREATE/UPDATE/DELETE；单行失败则整批随 @Transactional 回滚。
        for (DoctorStationAdviceDraftLineRequest line : request.getLines()) {
            String op = StringUtils.upperCase(StringUtils.trimToEmpty(line.getLineOp()));
            if (DoctorStationAdviceConstants.LINE_OP_CREATE.equals(op)) {
                handleCreateLine(request, line, operatorId, orgId, accountId, newMedIds);
            } else if (DoctorStationAdviceConstants.LINE_OP_UPDATE.equals(op)) {
                handleUpdateLine(request, line, operatorId, orgId, accountId);
            } else if (DoctorStationAdviceConstants.LINE_OP_DELETE.equals(op)) {
                handleDeleteLine(request, line, operatorId);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的行操作: " + line.getLineOp());
            }
        }
        // 装配：批次成功应答；新建药品 id 列表供前端局部刷新。
        SaveDoctorStationAdviceDraftBatchResponse resp = new SaveDoctorStationAdviceDraftBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setNewMedicationRequestIds(newMedIds);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * 处理暂存批次中的 CREATE 行（新建草稿医嘱并按定价规则挂费用草稿；定价不满足开立口径时由 {@link #createChargeIfPossible} 阻断）。
     *
     * @param request    批次上下文（就诊、患者），用于组装域命令
     * @param line       单行请求（资源类型 + 各资源载荷）
     * @param operatorId 当前操作人，写入域命令
     * @param orgId      开立机构，药品草稿落库需要
     * @param accountId  自费账户，费用草稿锚点
     * @param newMedIds  输出：本轮新建的药品申请主键，供前端刷新列表或跳转
     * @throws BusinessException 资源类型非法、载荷缺失或域不变量失败（由下层抛出）
     */
    private void handleCreateLine(
        SaveDoctorStationAdviceDraftBatchRequest request,
        DoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId,
        Long accountId,
        List<Long> newMedIds
    ) {
        String pt = normalizeProductTableName(line.getProductTableName());
        if (isMedicationProductTable(pt)) {
            // 药品：先落药品申请草稿（含生成来源），再按需调整组号，最后按批号售价定价行生成费用草稿（缺失则整批保存失败）
            Long newMedicationRequestId = medicationRequestDomainService.createMedicationOrderDraft(
                doctorStationAdviceMapper.toCreateMedicationOrderDraftCommand(request, line, operatorId, orgId));
            if (line.getMedicationPayload() != null && line.getMedicationPayload().getGroupId() != null) {
                // 成组：与另开成组接口一致，此处允许单行创建后立即并入组
                medicationRequestDomainService.adjustMedicationOrderGroup(
                    doctorStationAdviceMapper.toAdjustMedicationGroupCommand(
                        List.of(newMedicationRequestId), line.getMedicationPayload().getGroupId(), operatorId));
            }
            createChargeIfPossible(request, newMedicationRequestId, TableNameConstants.MED_MEDICATION_REQUEST,
                TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId(),
                line.getMedicationPayload().getQuantity(), line.getMedicationPayload().getUnitCode(),
                loadSingleProduct(TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId()),
                accountId, orgId, operatorId);
            newMedIds.add(newMedicationRequestId);
        } else if (isDeviceProductTable(pt)) {
            // 耗材：单条草稿 + 费用草稿（缺批号售价定价则保存失败，与目录查询展示解耦）
            Long newDeviceRequestId = deviceRequestDomainService.createDeviceOrderDraft(
                doctorStationAdviceMapper.toCreateDeviceOrderDraftCommand(request, line, operatorId));
            createChargeIfPossible(request, newDeviceRequestId, TableNameConstants.WKF_DEVICE_REQUEST,
                TableNameConstants.ADM_DEVICE_DEFINITION, line.getDevicePayload().getDeviceDefinitionId(),
                line.getDevicePayload().getQuantity(), line.getDevicePayload().getUnitCode(),
                loadSingleProduct(TableNameConstants.ADM_DEVICE_DEFINITION, line.getDevicePayload().getDeviceDefinitionId()),
                accountId, orgId, operatorId);
        } else if (isActivityProductTable(pt)) {
            // 诊疗：必须先解析活动定义对应的产品读模型行，再落服务申请草稿（携带展示/计价元数据）
            ProductDefinitionDisplayDto activityProductRow =
                loadSingleProduct(TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId());
            Long newServiceRequestId = serviceRequestDomainService.createServiceRequestDraft(
                doctorStationAdviceMapper.toCreateServiceRequestDraftCommand(request, line, activityProductRow, operatorId));
            createChargeIfPossible(request, newServiceRequestId, TableNameConstants.WKF_SERVICE_REQUEST,
                TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId(),
                ServiceAdviceDraftCommandSupport.requireQuantity(
                    line.getServicePayload().getQuantity(), line.getServicePayload().getChildOverridesJson()),
                ServiceAdviceDraftCommandSupport.requireUnitCode(
                    line.getServicePayload().getUnitCode(), line.getServicePayload().getChildOverridesJson(), activityProductRow),
                activityProductRow, accountId, orgId, operatorId);
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + line.getProductTableName());
        }
    }

    /**
     * 处理 UPDATE 行——先作废「未计费且仍为草稿」的收费项，避免改医嘱后费用仍指向旧定义，再改医嘱并重挂费用。
     *
     * @param request    批次上下文
     * @param line       含 {@code requestId} 与各资源载荷
     * @param operatorId 操作人
     * @param orgId      机构上下文（费用草稿）
     * @param accountId  自费账户
     * @throws BusinessException 资源类型非法、requestId 缺失（由 Mapper/域抛出）或域校验失败
     */
    private void handleUpdateLine(
        SaveDoctorStationAdviceDraftBatchRequest request,
        DoctorStationAdviceDraftLineRequest line,
        Long operatorId,
        Long orgId,
        Long accountId
    ) {
        String pt = normalizeProductTableName(line.getProductTableName());
        if (isMedicationProductTable(pt)) {
            // 药品：作废旧草稿费用 → 改药品草稿 → 再按新定义/数量生成费用草稿
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, line.getRequestId(), operatorId));
            medicationRequestDomainService.changeMedicationOrderDraft(
                doctorStationAdviceMapper.toChangeMedicationOrderDraftCommand(line, operatorId));
            createChargeIfPossible(request, line.getRequestId(), TableNameConstants.MED_MEDICATION_REQUEST,
                TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId(),
                line.getMedicationPayload().getQuantity(), line.getMedicationPayload().getUnitCode(),
                loadSingleProduct(TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationPayload().getMedicationDefinitionId()),
                accountId, orgId, operatorId);
        } else if (isDeviceProductTable(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, line.getRequestId(), operatorId));
            deviceRequestDomainService.changeDeviceOrderDraft(doctorStationAdviceMapper.toChangeDeviceOrderDraftCommand(line, operatorId));
            createChargeIfPossible(request, line.getRequestId(), TableNameConstants.WKF_DEVICE_REQUEST,
                TableNameConstants.ADM_DEVICE_DEFINITION, line.getDevicePayload().getDeviceDefinitionId(),
                line.getDevicePayload().getQuantity(), line.getDevicePayload().getUnitCode(),
                loadSingleProduct(TableNameConstants.ADM_DEVICE_DEFINITION, line.getDevicePayload().getDeviceDefinitionId()),
                accountId, orgId, operatorId);
        } else if (isActivityProductTable(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, line.getRequestId(), operatorId));
            ProductDefinitionDisplayDto activityProductRow =
                loadSingleProduct(TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId());
            serviceRequestDomainService.modifyServiceRequestDraft(doctorStationAdviceMapper.toModifyServiceRequestDraftCommand(line, operatorId));
            createChargeIfPossible(request, line.getRequestId(), TableNameConstants.WKF_SERVICE_REQUEST,
                TableNameConstants.WKF_ACTIVITY_DEFINITION, line.getServicePayload().getActivityDefinitionId(),
                ServiceAdviceDraftCommandSupport.requireQuantity(
                    line.getServicePayload().getQuantity(), line.getServicePayload().getChildOverridesJson()),
                ServiceAdviceDraftCommandSupport.requireUnitCode(
                    line.getServicePayload().getUnitCode(), line.getServicePayload().getChildOverridesJson(), activityProductRow),
                activityProductRow, accountId, orgId, operatorId);
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + line.getProductTableName());
        }
    }

    /**
     * 处理 DELETE 行——先作废未计费草稿费用，再物理/逻辑删除医嘱草稿（语义由域服务定义）。
     *
     * @param request    批次上下文
     * @param line       必须带 {@code requestId}，否则无法定位待删聚合
     * @param operatorId 操作人
     * @throws BusinessException {@code requestId} 为空、资源类型非法或域拒绝删除
     */
    private void handleDeleteLine(SaveDoctorStationAdviceDraftBatchRequest request, DoctorStationAdviceDraftLineRequest line, Long operatorId) {
        String pt = normalizeProductTableName(line.getProductTableName());
        if (line.getRequestId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "DELETE 操作 requestId 不能为空");
        }
        if (isMedicationProductTable(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, line.getRequestId(), operatorId));
            medicationRequestDomainService.removeMedicationOrderDraft(
                doctorStationAdviceMapper.toRemoveMedicationOrderDraftCommand(line.getRequestId(), operatorId));
        } else if (isDeviceProductTable(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, line.getRequestId(), operatorId));
            deviceRequestDomainService.removeDeviceOrderDraft(
                doctorStationAdviceMapper.toRemoveDeviceOrderDraftCommand(line.getRequestId(), operatorId));
        } else if (isActivityProductTable(pt)) {
            chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchor(
                doctorStationAdviceMapper.toVoidDraftChargeAnchor(
                    request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, line.getRequestId(), operatorId));
            serviceRequestDomainService.removeServiceRequestDraft(
                doctorStationAdviceMapper.toRemoveServiceRequestDraftCommand(line.getRequestId(), operatorId));
        } else {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + line.getProductTableName());
        }
    }

    /**
     * 在存在收费项定义「定价行」的前提下，为当前医嘱申请生成一笔就诊费用草稿。
     *
     * <p>定义主键为空时不生成费用（历史兼容）；定义主键非空时必须在 {@link DoctorStationAdviceCatalogAssembler#selectFirstPriceLineForChargeDraft}
     * 分流下命中定价行（诊疗 {@code SALE}，药耗 {@code LOT_NUMBER_PRICE}），否则抛出 {@link BusinessException}，避免无锚点费用默默落库。</p>
     *
     * @param request        就诊与患者，写入费用项
     * @param requestId      医嘱申请主键，作为收费 service_id
     * @param requestTable   医嘱申请物理表名，与收费锚点一致
     * @param definitionTable 定价查询用的定义表（药品定义/活动定义/耗材定义）
     * @param definitionId   定义主键；为 {@code null} 时不查询定价、不生成费用
     * @param quantity       计价数量（与医嘱请求 quantity 同语义）
     * @param quantityUnit   计价单位 code
     * @param productRow     产品定义读模型
     * @param accountId      自费账户
     * @param orgId          机构
     * @param operatorId     操作人
     * @throws BusinessException 定义主键已给出但无可用开立口径定价行时抛出（错误码 {@link ErrorCodeConstants#CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT}）
     */
    private void createChargeIfPossible(
        SaveDoctorStationAdviceDraftBatchRequest request,
        Long requestId,
        String requestTable,
        String definitionTable,
        Long definitionId,
        java.math.BigDecimal quantity,
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
                "未配置符合开立规则的收费定价（诊疗须零售价 SALE，药品/耗材须批号售价 LOT_NUMBER_PRICE）。请先维护收费项定价后再保存医嘱。");
        }
        chargeItemDomainService.createDraftChargeItemFromOrder(
            doctorStationAdviceMapper.toCreateChargeItemDraftFromOrder(
                request.getEncounterId(), request.getPatientId(), accountId, orgId, operatorId,
                requestId, requestTable, definitionTable, definitionId, priceLine, quantity, quantityUnit, productRow));
    }

    /**
     * 按定价实例表与主键加载该实例下全部定价行，再交给 {@link DoctorStationAdviceCatalogAssembler#selectFirstPriceLineForChargeDraft}
     * 选出费用草稿单价行（与目录计价对齐）。
     * <p>诊疗活动定义（{@link TableNameConstants#WKF_ACTIVITY_DEFINITION}）仅采纳零售价行（价型
     * {@link com.openhis.health.domain.share.enums.PriceType#SALE}）；其它定义表维持仓储返回顺序首条的历史兼容。</p>
     *
     * @param instanceTable 定价实例表（与 {@link ChargeItemDefinitionQueryCommand#setInstanceTable} 一致）
     * @param instanceId    定价实例主键；{@code null} 时不查询，直接返回 {@code null}
     * @return 用于草稿金额的定价行；无记录或诊疗无零售价行时 {@code null}
     */
    private ChargeItemDefinition firstPriceLine(String instanceTable, Long instanceId) {
        if (instanceId == null) {
            return null;
        }
        ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
        priceQuery.setInstanceTable(instanceTable);
        priceQuery.setInstanceIds(List.of(instanceId));
        List<ChargeItemDefinition> priceLines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
        // 诊疗与目录共用 SALE-only 择优，避免「首条」落在采购价行导致计费单价为空或口径错误
        return DoctorStationAdviceCatalogAssembler.selectFirstPriceLineForChargeDraft(instanceTable, priceLines);
    }

    /**
     * 按 FIFO 发放分段批量生成费用草稿：单次解析定价、单次调用 {@link ChargeItemDomainService#createDraftChargeItemFromOrder}，
     * 域内 {@code saveAll} 落库与批量 busNo（通用规范 A.9.1 / B.10.9）；无符合分流规则的定价行时抛出 {@link BusinessException}。
     *
     * @param encounterId         就诊主键
     * @param patientId           患者主键
     * @param accountId           自费账户
     * @param orgId               开立机构
     * @param operatorId          操作人
     * @param requestId           医嘱申请主键（写入 {@code service_id}）
     * @param requestServiceTable 医嘱请求物理表名（写入 {@code service_table}）
     * @param productDefinitionTable 药品/耗材定义表（写入 {@code product_table}）
     * @param productDefinitionId 定义主键（写入 {@code product_id}）
     * @param dispenseTable       发放物理表名（写入 {@code dispense_table}）
     * @param dispenseIds         与各分段顺序一致的 {@code dispense_id} 列表
     * @param segmentQuantities   与各分段顺序一致的计价数量列表
     * @param quantityUnit        计价单位 code（与医嘱开立单位一致）
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
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "发放分段与费用草稿数量不一致");
        }
        if (dispenseIds.isEmpty()) {
            return;
        }
        // 签发批量费用草稿：必须与目录/暂存同一「批号售价」口径命中定价行，缺失则阻断签发以便回到主数据补价。
        ChargeItemDefinition priceLine = firstPriceLine(productDefinitionTable, productDefinitionId);
        if (priceLine == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                "未配置符合开立规则的收费定价（药品/耗材须批号售价 LOT_NUMBER_PRICE）。请先维护收费项定价后再签发医嘱。");
        }
        ProductDefinitionDisplayDto productRow = loadSingleProduct(productDefinitionTable, productDefinitionId);
        List<CreateChargeItemCommand.Item> draftItems = new ArrayList<>();
        // 每一发放分段一行 ChargeItem：service 指向医嘱请求，product 指向定义，dispense 指向本条分段（DM-ADM-011）。
        for (int i = 0; i < dispenseIds.size(); i++) {
            draftItems.add(doctorStationAdviceMapper.toCreateChargeItemDraftItemForFifoDispenseSegment(
                encounterId,
                patientId,
                accountId,
                orgId,
                requestId,
                requestServiceTable,
                productDefinitionTable,
                productDefinitionId,
                dispenseTable,
                dispenseIds.get(i),
                priceLine,
                segmentQuantities.get(i),
                quantityUnit,
                productRow));
        }
        CreateChargeItemCommand batchCmd = new CreateChargeItemCommand();
        batchCmd.setOperatorId(operatorId);
        batchCmd.setItems(draftItems);
        // 单次 saveAll 批量落库草稿费用（A.9.1），busNo 由域内按批量条数取号。
        chargeItemDomainService.createDraftChargeItemFromOrder(batchCmd);
    }

    /**
     * 按产品表与主键加载单条产品定义读模型，供诊疗开立前校验与展示。
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
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "未找到诊疗活动产品定义行");
        }
        return row;
    }

    /**
     * 签发医嘱批次（药品批次号、处方号、诊疗外呼占位）。
     *
     * <p>签发成功后，将本批次内各医嘱锚点下仍处于 DRAFT 的收费明细升为 PLANNED，以便门诊收费预结算通过
     *
     * @param request 就诊、患者、签发目标集合；批次号可空则按日序列生成
     * @return 批次号、药品处方号映射
     * @throws BusinessException 门控失败、产品表名不支持，或域签发/费用状态迁移失败
     */
    
    public SignDoctorStationAdviceBatchResponse signDoctorStationAdviceBatch(SignDoctorStationAdviceBatchRequest request) {
        // 门控：加载门诊快照并校验患者上下文一致
        OutpatientEncounterContextQueryModel snapshot = encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        // 解析当前操作人主键与机构上下文
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        Long orgId = snapshot.getOrganizationId();
        // 签发需要自费账户作为费用锚点
        Long accountId = accountQueryService.getSelfPayAccount(request.getEncounterId());
        if (accountId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊未解析到自费账户，无法完成签发费用编排");
        }
        // 生成批次号：请求传入时直接使用，否则按日序列生成
        String signBatch;
        if (StringUtils.isNotBlank(request.getSignBatchCode())) {
            signBatch = request.getSignBatchCode();
        } else {
            signBatch = businessSequenceService.getSeqByDay(AggregateTypeConstants.MEDICATION_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
        }
        // 收集药品医嘱与处方号映射，供前端展示
        Map<Long, String> rxMap = new HashMap<>();
        // 命令：按目标类型签发；诊疗分支触发外呼网关占位
        for (DoctorStationAdviceResourceKeyDto t : request.getSignTargets()) {
            String pt = normalizeProductTableName(t.getProductTableName());
            if (isMedicationProductTable(pt)) {
                String prescriptionNo =
                    businessSequenceService.getSeqByDay(AggregateTypeConstants.MEDICATION_REQUEST, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
                medicationRequestDomainService.signMedicationOrder(
                    doctorStationAdviceMapper.toSignMedicationOrderCommand(t.getRequestId(), signBatch, prescriptionNo, operatorId));
                rxMap.put(t.getRequestId(), prescriptionNo);
                // 签发后：作废原「医嘱申请锚点」暂存费用草稿 → FIFO 分段发放 → 按请求+定义+发放段挂费 → 按发放锚点批量升为 PLANNED。
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, List.of(t.getRequestId()));
                MedicationRequestFifoSignContext fifoMedCtx =
                    medicationRequestQueryService.loadFifoSignContextOrThrow(t.getRequestId());
                List<ReplaceFifoMedicationDispenseDraftsCommand.Segment> medSegments = doctorStationAdviceFifoAllocationAssembler.allocateMedicationFifo(
                    orgId, fifoMedCtx.getEncounterId(), fifoMedCtx.getMedicationId(), fifoMedCtx.getCatalogCategoryCode(),
                    fifoMedCtx.getUnitCode(), fifoMedCtx.getQuantity());
                ReplaceFifoMedicationDispenseDraftsCommand fifoMedCmd = new ReplaceFifoMedicationDispenseDraftsCommand();
                fifoMedCmd.setOperatorId(operatorId);
                fifoMedCmd.setMedReqId(fifoMedCtx.getMedReqId());
                fifoMedCmd.setMedicationId(fifoMedCtx.getMedicationId());
                fifoMedCmd.setEncounterId(fifoMedCtx.getEncounterId());
                fifoMedCmd.setPatientId(fifoMedCtx.getPatientId());
                fifoMedCmd.setUnitCode(fifoMedCtx.getUnitCode());
                fifoMedCmd.setDispenseEnum(MedicationDispenseType.OUTPATIENT);
                fifoMedCmd.setSegments(medSegments);
                List<Long> medDispenseIds = medicationDispenseDomainService.replaceFifoDraftDispensesAfterDoctorStationSign(fifoMedCmd);
                List<BigDecimal> medQtys = new ArrayList<>();
                for (ReplaceFifoMedicationDispenseDraftsCommand.Segment seg : medSegments) {
                    medQtys.add(seg.getQuantity());
                }
                this.createDispenseSegmentChargeDrafts(
                    request.getEncounterId(), request.getPatientId(), accountId, orgId, operatorId,
                    fifoMedCtx.getMedReqId(),
                    TableNameConstants.MED_MEDICATION_REQUEST,
                    TableNameConstants.MED_MEDICATION_DEFINITION,
                    fifoMedCtx.getMedicationId(),
                    TableNameConstants.MED_MEDICATION_DISPENSE,
                    medDispenseIds,
                    medQtys,
                    fifoMedCtx.getUnitCode());
                chargeItemDomainService.planDraftChargeItemsAfterAdviceSignedByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_DISPENSE, medDispenseIds);
            } else if (isActivityProductTable(pt)) {
                // 诊疗：批次号写入服务申请；外呼仅占位，真实 LIS/PACS 路由待检查申请类型后替换
                serviceRequestDomainService.signServiceRequest(
                    doctorStationAdviceMapper.toSignServiceRequestCommand(t.getRequestId(), signBatch, operatorId));
                LisPacsAdviceApplyCommand lisPacsStub = new LisPacsAdviceApplyCommand();
                lisPacsStub.setEncounterId(request.getEncounterId());
                lisPacsStub.setPatientId(request.getPatientId());
                lisPacsApplyGateway.submitIfLabOrImaging(lisPacsStub);
                // 诊疗申请签发完成：检查/治疗类费用行进入可预结算态。
                chargeItemDomainService.planDraftChargeItemsAfterAdviceSigned(
                    request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, t.getRequestId());
            } else if (isDeviceProductTable(pt)) {
                // 耗材：当前域命令不携带批次号，与药品/诊疗批次展示策略区分
                deviceRequestDomainService.signDeviceOrder(doctorStationAdviceMapper.toSignDeviceOrderCommand(t.getRequestId(), operatorId));
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, List.of(t.getRequestId()));
                DeviceRequestFifoSignContext fifoDevCtx =
                    deviceRequestQueryService.loadFifoSignContextOrThrow(t.getRequestId());
                List<ReplaceFifoDeviceDispenseDraftsCommand.Segment> devSegments = doctorStationAdviceFifoAllocationAssembler.allocateDeviceFifo(
                    orgId, fifoDevCtx.getEncounterId(), fifoDevCtx.getDeviceDefId(), fifoDevCtx.getCatalogCategoryCode(),
                    fifoDevCtx.getUnitCode(), fifoDevCtx.getQuantity());
                ReplaceFifoDeviceDispenseDraftsCommand fifoDevCmd = new ReplaceFifoDeviceDispenseDraftsCommand();
                fifoDevCmd.setOperatorId(operatorId);
                fifoDevCmd.setDeviceReqId(fifoDevCtx.getDeviceReqId());
                fifoDevCmd.setDeviceDefId(fifoDevCtx.getDeviceDefId());
                fifoDevCmd.setEncounterId(fifoDevCtx.getEncounterId());
                fifoDevCmd.setPatientId(fifoDevCtx.getPatientId());
                fifoDevCmd.setUnitCode(fifoDevCtx.getUnitCode());
                fifoDevCmd.setSegments(devSegments);
                List<Long> devDispenseIds = deviceDispenseDomainService.replaceFifoDraftDispensesAfterDoctorStationSign(fifoDevCmd);
                List<BigDecimal> devQtys = new ArrayList<>();
                for (ReplaceFifoDeviceDispenseDraftsCommand.Segment seg : devSegments) {
                    devQtys.add(seg.getQuantity());
                }
                this.createDispenseSegmentChargeDrafts(
                    request.getEncounterId(), request.getPatientId(), accountId, orgId, operatorId,
                    fifoDevCtx.getDeviceReqId(),
                    TableNameConstants.WKF_DEVICE_REQUEST,
                    TableNameConstants.ADM_DEVICE_DEFINITION,
                    fifoDevCtx.getDeviceDefId(),
                    TableNameConstants.WKF_DEVICE_DISPENSE,
                    devDispenseIds,
                    devQtys,
                    fifoDevCtx.getUnitCode());
                chargeItemDomainService.planDraftChargeItemsAfterAdviceSignedByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_DISPENSE, devDispenseIds);
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + t.getProductTableName());
            }
        }
        SignDoctorStationAdviceBatchResponse resp = new SignDoctorStationAdviceBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setSignBatchCode(signBatch);
        resp.setPrescriptionNoByMedicationRequestId(rxMap);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * 签退医嘱批次。
     *
     * <p>签退成功后，将各目标医嘱锚点下仍为 PLANNED 且未结算的收费明细回退为 DRAFT，与「医嘱未对外生效」语义一致，
     * 避免收费端继续对已签退医嘱发起预结算。</p>
     *
     * @param request 就诊、患者、签退目标
     * @return 成功应答
     * @throws BusinessException 门控失败、产品表名不支持，或域签退/费用状态回退失败
     */
    
    public WithdrawDoctorStationAdviceBatchResponse withdrawDoctorStationAdviceBatch(WithdrawDoctorStationAdviceBatchRequest request) {
        OutpatientEncounterContextQueryModel snapshot = encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        // 命令：按目标类型签退（与签发不对称处以保持域服务既有命令为准）
        for (DoctorStationAdviceResourceKeyDto t : request.getTargets()) {
            String pt = normalizeProductTableName(t.getProductTableName());
            if (isMedicationProductTable(pt)) {
                List<Long> medDispenseIds = medicationDispenseQueryService.listMedicationDispenseIdsByMedicationRequest(t.getRequestId());
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_DISPENSE, medDispenseIds);
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, t.getRequestId());
                medicationRequestDomainService.signOffMedicationOrder(
                    doctorStationAdviceMapper.toSignOffMedicationOrderCommand(t.getRequestId(), operatorId));
                chargeItemDomainService.voidUnbilledDraftChargeItemsByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_DISPENSE, medDispenseIds);
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                    request.getEncounterId(), TableNameConstants.MED_MEDICATION_REQUEST, List.of(t.getRequestId()));
                ReleaseDispenseReserveCommand releaseMed = new ReleaseDispenseReserveCommand();
                releaseMed.setMedReqId(t.getRequestId());
                releaseMed.setOperatorId(operatorId);
                medicationDispenseDomainService.releaseDispenseReserve(releaseMed);
            } else if (isActivityProductTable(pt)) {
                serviceRequestDomainService.withdrawSignedServiceRequest(
                    doctorStationAdviceMapper.toWithdrawServiceRequestCommand(t.getRequestId(), operatorId));
                // 诊疗签退：检查申请对应的可计费行收回为草稿。
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    request.getEncounterId(), TableNameConstants.WKF_SERVICE_REQUEST, t.getRequestId());
            } else if (isDeviceProductTable(pt)) {
                List<Long> devDispenseIds = deviceDispenseQueryService.listDeviceDispenseIdsByDeviceRequest(t.getRequestId());
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_DISPENSE, devDispenseIds);
                chargeItemDomainService.rollbackPlannedChargeItemsAfterAdviceSignedOff(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, t.getRequestId());
                deviceRequestDomainService.signOffDeviceOrder(
                    doctorStationAdviceMapper.toSignOffDeviceOrderCommand(t.getRequestId(), operatorId));
                chargeItemDomainService.voidUnbilledDraftChargeItemsByDispenseAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_DISPENSE, devDispenseIds);
                chargeItemDomainService.voidUnbilledDraftChargeItemsByServiceAnchors(
                    request.getEncounterId(), TableNameConstants.WKF_DEVICE_REQUEST, List.of(t.getRequestId()));
                if (!devDispenseIds.isEmpty()) {
                    ReleaseDeviceDispenseReserveCommand releaseDev = new ReleaseDeviceDispenseReserveCommand();
                    releaseDev.setIdListOrRequestKey(String.valueOf(t.getRequestId()));
                    releaseDev.setOperatorId(operatorId);
                    deviceDispenseDomainService.releaseDeviceDispenseReserve(releaseDev);
                }
            } else {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "不支持的产品定义表名: " + t.getProductTableName());
            }
        }
        WithdrawDoctorStationAdviceBatchResponse resp = new WithdrawDoctorStationAdviceBatchResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * 更新药品医嘱组号（清空与成组两段命令）。
     *
     * @param request 就诊、患者、清空列表与成组指令
     * @return 成功应答
     * @throws BusinessException 门控失败
     */
    
    public UpdateDoctorStationMedicationAdviceGroupsResponse updateDoctorStationMedicationAdviceGroups(UpdateDoctorStationMedicationAdviceGroupsRequest request) {
        OutpatientEncounterContextQueryModel snapshot = encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "未获取到当前操作人");
        // 命令：先清空组号再按请求成组（两段独立域调用）
        if (request.getClearGroupIds() != null && !request.getClearGroupIds().isEmpty()) {
            medicationRequestDomainService.adjustMedicationOrderGroup(
                doctorStationAdviceMapper.toAdjustMedicationGroupCommand(request.getClearGroupIds(), null, operatorId));
        }
        if (request.getAssignGroup() != null && request.getAssignGroup().getMedicationRequestIds() != null) {
            medicationRequestDomainService.adjustMedicationOrderGroup(
                doctorStationAdviceMapper.toAdjustMedicationGroupCommand(
                    request.getAssignGroup().getMedicationRequestIds(), request.getAssignGroup().getGroupId(), operatorId));
        }
        UpdateDoctorStationMedicationAdviceGroupsResponse resp = new UpdateDoctorStationMedicationAdviceGroupsResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setMessage(DoctorStationAdviceConstants.RESPONSE_MESSAGE_OK);
        return resp;
    }

    /**
     * 规范化产品定义表名字符串：去首尾空白，{@code null} 视为空串，便于与 {@link TableNameConstants} 常量做忽略大小写比较。
     *
     * @param productTableName 客户端或草稿行传入的物理表名，允许 {@code null}
     * @return 去空白后的表名；输入为 {@code null} 时返回空串
     */
    private static String normalizeProductTableName(String productTableName) {
        return StringUtils.trimToEmpty(productTableName);
    }

    /**
     * 判断是否为药品定义表（与目录锚点一致）。
     *
     * @param productTableName 物理表名，允许空白；与 {@link TableNameConstants#MED_MEDICATION_DEFINITION} 忽略大小写相等时视为药品
     * @return 为药品定义表时 {@code true}
     */
    private static boolean isMedicationProductTable(String productTableName) {
        return TableNameConstants.MED_MEDICATION_DEFINITION.equalsIgnoreCase(normalizeProductTableName(productTableName));
    }

    /**
     * 判断是否为耗材/设备定义表。
     *
     * @param productTableName 物理表名；与 {@link TableNameConstants#ADM_DEVICE_DEFINITION} 忽略大小写相等时视为耗材
     * @return 为设备定义表时 {@code true}
     */
    private static boolean isDeviceProductTable(String productTableName) {
        return TableNameConstants.ADM_DEVICE_DEFINITION.equalsIgnoreCase(normalizeProductTableName(productTableName));
    }

    /**
     * 判断是否为诊疗活动定义表。
     *
     * @param productTableName 物理表名；与 {@link TableNameConstants#WKF_ACTIVITY_DEFINITION} 忽略大小写相等时视为诊疗
     * @return 为活动定义表时 {@code true}
     */
    private static boolean isActivityProductTable(String productTableName) {
        return TableNameConstants.WKF_ACTIVITY_DEFINITION.equalsIgnoreCase(normalizeProductTableName(productTableName));
    }

}
