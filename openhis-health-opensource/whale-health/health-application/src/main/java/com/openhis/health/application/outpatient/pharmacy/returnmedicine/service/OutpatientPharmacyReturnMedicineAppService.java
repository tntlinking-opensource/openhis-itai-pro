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

package com.openhis.health.application.outpatient.pharmacy.returnmedicine.service;

import com.openhis.health.module.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentOperatorResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.assembler.OutpatientReturnMedicinePatientQueueAssembler;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.mapper.OutpatientPharmacyReturnMedicineMapper;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.application.insurance.config.InsuranceCoordinationProperties;
import com.openhis.health.application.insurance.inventory.MedicareInventoryUploadCoordinator;
import com.openhis.health.domain.share.constants.InsuranceCoordinationConstants;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.ConfirmMedicineReturnRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.GetReturnMedicineDetailRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.LoadReturnMedicineInitRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.PageReturnPatientRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.request.ReturnMedicineItemRequest;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.response.ConfirmMedicineReturnResponse;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.response.LoadReturnMedicineInitResponse;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.response.PageReturnPatientResponse;
import com.openhis.health.application.outpatient.pharmacy.returnmedicine.response.ReturnMedicineDetailResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.custom.tracenumberrecord.command.AppendTraceRecordsCommand;
import com.openhis.health.domain.custom.tracenumberrecord.service.TraceNumberRecordDomainService;
import com.openhis.health.domain.medication.medicationdispense.command.DeclinePendingRefundMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.RefundDispensedMedicationCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.query.PageReturnPatientQuery;
import com.openhis.health.domain.medication.medicationdispense.query.ReturnMedicineDetailQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.pharmacy.PharmacyEncounterQueueLoader;
import com.openhis.health.module.querysupport.pharmacy.ReturnMedicineDetailLoader;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicineDetailReadRow;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicinePatientQueueRow;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.chs.jilin.port.InventoryInsurancePort;
import com.openhis.whale.chs.jilin.port.model.UploadAfterReturnCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 门诊药房退药工作站应用服务（AS-OP-PHA-002）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：分页/登录、退药行状态 PENDING_REFUND、同就诊约束。</li>
 *   <li>Pre：患者队列、退药明细与应退金额回填。</li>
 *   <li>Cmd：确认/拒绝退药、库存回补、追溯码、3506 进销存上传。</li>
 *   <li>Asm：经 {@link OutpatientPharmacyReturnMedicineMapper} 装配响应。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientPharmacyReturnMedicineAppService {

    @Autowired(required = false)
    private InventoryInsurancePort inventoryInsurancePort;

    @Autowired(required = false)
    private MedicareInventoryUploadCoordinator medicareInventoryUploadCoordinator;

    private final InsuranceCoordinationProperties insuranceCoordinationProperties;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final OrganizationQueryService organizationQueryService;
    private final LocationQueryService locationQueryService;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final ReturnMedicineDetailLoader returnMedicineDetailLoader;
    private final PharmacyEncounterQueueLoader pharmacyEncounterQueueLoader;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final TraceNumberRecordDomainService traceNumberRecordDomainService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final OutpatientPharmacyReturnMedicineMapper returnMedicineMapper;
    private final OutpatientReturnMedicinePatientQueueAssembler outpatientReturnMedicinePatientQueueAssembler;

    /**
     * 加载退药初始化元数据（科室与退药状态筛选项）。
     *
     * @param request 请求体（可空壳）
     * @return 初始化数据
     * @throws BusinessException {@code EX-PHA-002-101}：加载失败
     */
    public LoadReturnMedicineInitResponse loadReturnMedicineInit(LoadReturnMedicineInitRequest request) {
        try {
            if (request == null) {
                request = new LoadReturnMedicineInitRequest();
            }
            // Pre：加载科室下拉与退药状态筛选项。
            List<Organization> departments = this.organizationQueryService.listOrganizationsForOutpatientRegistration();
            LoadReturnMedicineInitResponse resp = new LoadReturnMedicineInitResponse();
            resp.setDepartmentOptions(this.returnMedicineMapper.mapToDepartmentOptions(departments));
            resp.setRefundStatusOptions(this.returnMedicineMapper.mapToRefundStatusOptions());
            // Asm：返回初始化元数据。
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "退药元数据加载失败：" + ex.getMessage());
        }
    }

    /**
     * 分页查询退药患者队列（仅含药品发放事实的就诊）。
     *
     * @param request 分页与过滤条件
     * @return 分页结果
     * @throws BusinessException {@code EX-PHA-002-101}：查询失败；分页参数非法
     */
    public PageReturnPatientResponse pageReturnPatientQueue(PageReturnPatientRequest request) {
        try {
            if (request == null) {
                request = new PageReturnPatientRequest();
            }
            // Gate：分页参数须合法。
            this.validatePageReturnPatientRequest(request);
            // Pre：按管辖药房与筛选条件分页就诊 ID。
            PageReturnPatientQuery query = this.returnMedicineMapper.toPageReturnPatientQuery(request);
            List<Long> pharmacyLocationIds = this.managedLocationIdsResolver.listManagedLocationIdsOrEmpty();
            if (!pharmacyLocationIds.isEmpty()) {
                query.setLocationIds(pharmacyLocationIds);
            }
            Page<Long> encounterIdPage =
                this.pharmacyEncounterQueueLoader.pageEncounterIdsForOutpatientPharmacyReturn(query);
            List<Long> encounterIds = encounterIdPage.getContent();
            List<OutpatientEncounterContextQueryModel> infos =
                this.encounterContextFacade.listOutpatientContextsByEncounterIdsOrdered(encounterIds);
            Map<Long, String> refundCodeByEncounter =
                this.medicationDispenseQueryService.mapAggregatedRefundStatusCodeByEncounterIds(encounterIds);
            List<ReturnMedicinePatientQueueRow> rows =
                this.outpatientReturnMedicinePatientQueueAssembler.buildRows(infos, refundCodeByEncounter);
            PageReturnPatientResponse response = new PageReturnPatientResponse();
            return PagedResultBuilder.build(
                response,
                this.returnMedicineMapper.mapToReturnPatientRows(rows),
                encounterIdPage);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "退药队列查询失败：" + ex.getMessage());
        }
    }

    /**
     * 查询指定就诊的药品退药明细。
     *
     * @param request 就诊与过滤条件
     * @return 明细列表
     * @throws BusinessException {@code EX-PHA-002-101}：就诊不存在或查询失败
     */
    public ReturnMedicineDetailResponse getReturnMedicineDetail(GetReturnMedicineDetailRequest request) {
        try {
            // Gate：就诊主键须非空且就诊须存在。
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "encounterId 不能为空");
            }
            this.encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
            // Pre：加载退药明细读模型行。
            ReturnMedicineDetailQuery query = this.returnMedicineMapper.toReturnMedicineDetailQuery(request);
            List<ReturnMedicineDetailReadRow> rows = this.returnMedicineDetailLoader.listReturnMedicineDetails(query);
            // Pre：按原药嘱 service_id 汇总应退金额。
            this.applyChargeTotalPriceByOriginRequestId(request.getEncounterId(), rows);
            // Asm：映射明细列表并返回。
            ReturnMedicineDetailResponse resp = new ReturnMedicineDetailResponse();
            resp.setItems(this.returnMedicineMapper.mapToReturnMedicineDetailRows(rows));
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "退药明细查询失败：" + ex.getMessage());
        }
    }

    /**
     * 确认退药或拒绝退药（仅药品发放单）。
     *
     * @param request 操作类型、原因与退药项列表
     * @return 成功响应
     * @throws BusinessException 参数非法、状态不允许、库存定位失败等
     */
    public ConfirmMedicineReturnResponse confirmMedicineReturn(ConfirmMedicineReturnRequest request) {
        // Gate：请求体、confirmType 与退药行须合法。
        this.validateConfirmMedicineReturnRequest(request);
        Long operatorId = this.requireOperatorUserId();
        Long practitionerId = this.resolvePractitionerIdForAudit(operatorId);
        boolean confirm =
            MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_TYPE_CONFIRM.equalsIgnoreCase(
                request.getConfirmType().trim());
        boolean decline =
            MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_TYPE_DECLINE.equalsIgnoreCase(
                request.getConfirmType().trim());
        // Gate：confirmType 只能是 CONFIRM 或 DECLINE。
        if (!confirm && !decline) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_201_STATUS_NOT_ALLOWED, "confirmType 只能是 CONFIRM 或 DECLINE");
        }
        List<RefundDispensedMedicationCommand.Item> medRefundItems = new ArrayList<>();
        List<DeclinePendingRefundMedicationDispenseCommand.Item> medDeclineItems = new ArrayList<>();
        LinkedHashSet<Long> medRequestIds = new LinkedHashSet<>();
        List<AppendTraceRecordsCommand.AppendTraceRecordItemCommand> traceItems = new ArrayList<>();
        Map<Long, BigDecimal> inventoryIncreaseMerged = new HashMap<>();
        List<Long> confirmedDispenseIds = new ArrayList<>();
        Long encounterIdForUpload = null;

        for (ReturnMedicineItemRequest item : request.getItems()) {
            if (item == null) {
                continue;
            }
            String table = StringUtils.defaultString(item.getTableName()).trim();
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equalsIgnoreCase(table)) {
                throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "本页仅支持药品退药");
            }
            MedicationDispense md = this.medicationDispenseQueryService.mustGetMedicationDispense(item.getDispenseId());
            this.assertPendingRefundDispense(md.getStatusEnum(), item.getDispenseId());
            if (encounterIdForUpload == null) {
                encounterIdForUpload = md.getEncounterId();
            } else if (!encounterIdForUpload.equals(md.getEncounterId())) {
                throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "一次退药确认仅支持同一就诊");
            }
            if (confirm) {
                RefundDispensedMedicationCommand.Item it = new RefundDispensedMedicationCommand.Item();
                it.setId(md.getId());
                it.setRefundQuantity(this.resolveRefundQuantity(md));
                it.setOperatorId(operatorId);
                it.setPractitionerId(practitionerId);
                it.setReasonText(request.getReason());
                medRefundItems.add(it);
                confirmedDispenseIds.add(md.getId());
                this.collectMedicationTraceAndInventory(md, traceItems, inventoryIncreaseMerged);
            } else {
                DeclinePendingRefundMedicationDispenseCommand.Item it = new DeclinePendingRefundMedicationDispenseCommand.Item();
                it.setId(md.getId());
                it.setDeclineReasonText(request.getReason());
                it.setPractitionerId(practitionerId);
                medDeclineItems.add(it);
            }
            medRequestIds.add(item.getRequestId());
        }

        if (confirm) {
            if (!medRefundItems.isEmpty()) {
                RefundDispensedMedicationCommand cmd = new RefundDispensedMedicationCommand();
                cmd.setOperatorId(operatorId);
                cmd.setItems(medRefundItems);
                // Cmd：仅推进待退 REFUND 行；退药与原正向发药单无关联（BR-PHA-038）
                this.medicationDispenseDomainService.refundDispensedMedication(cmd);
            }
        } else {
            if (!medDeclineItems.isEmpty()) {
                DeclinePendingRefundMedicationDispenseCommand cmd = new DeclinePendingRefundMedicationDispenseCommand();
                cmd.setOperatorId(operatorId);
                cmd.setItems(medDeclineItems);
                this.medicationDispenseDomainService.declinePendingRefundMedicationDispense(cmd);
            }
        }

        for (Long requestId : medRequestIds) {
            if (requestId == null) {
                continue;
            }
            this.medicationRequestDomainService.completeMedicationRequestAfterRefund(requestId, operatorId);
        }

        if (confirm) {
            if (!traceItems.isEmpty()) {
                AppendTraceRecordsCommand traceCmd = new AppendTraceRecordsCommand();
                traceCmd.setOperatorId(operatorId);
                traceCmd.setItems(traceItems);
                this.traceNumberRecordDomainService.appendTraceRecords(traceCmd);
            }
            if (!inventoryIncreaseMerged.isEmpty()) {
                IncreaseQuantityCommand inc = new IncreaseQuantityCommand();
                inc.setOperatorId(operatorId);
                List<IncreaseQuantityCommand.IncreaseQuantityItemCommand> incItems = new ArrayList<>();
                for (Map.Entry<Long, BigDecimal> e : inventoryIncreaseMerged.entrySet()) {
                    IncreaseQuantityCommand.IncreaseQuantityItemCommand it = new IncreaseQuantityCommand.IncreaseQuantityItemCommand();
                    it.setId(e.getKey());
                    it.setIncreaseQty(e.getValue());
                    incItems.add(it);
                }
                inc.setItems(incItems);
                this.inventoryItemDomainService.increaseQuantities(inc);
            }
        }

        ConfirmMedicineReturnResponse resp = new ConfirmMedicineReturnResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setFailedItems(new ArrayList<>());
        if (confirm) {
            resp.setMessage(MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_SUCCESS_MESSAGE);
            mergeMedicareUploadWarning(
                resp,
                tryUploadMedicareInventoryAfterReturn(request, encounterIdForUpload, confirmedDispenseIds));
        } else {
            resp.setMessage(MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_DECLINE_SUCCESS_MESSAGE);
        }
        return resp;
    }

    /**
     * 退药本地事实提交后触发 3506；失败仅告警，不回滚退药。
     *
     * @return 需合并进响应的告警文案；无告警时 {@code null}
     */
    private String tryUploadMedicareInventoryAfterReturn(
        ConfirmMedicineReturnRequest request,
        Long encounterId,
        List<Long> dispenseIds
    ) {
        if (encounterId == null || dispenseIds == null || dispenseIds.isEmpty()) {
            return null;
        }
        try {
            if (insuranceCoordinationProperties.isDispenseUploadAsyncEnabled()
                && medicareInventoryUploadCoordinator != null) {
                medicareInventoryUploadCoordinator.scheduleUploadIfEnabled(
                    encounterId,
                    dispenseIds,
                    requireOperatorUserId(),
                    InsuranceCoordinationConstants.DISPENSE_UPLOAD_KIND_RETURN);
            } else if (inventoryInsurancePort != null) {
                UploadAfterReturnCommand uploadCommand = new UploadAfterReturnCommand()
                    .setEncounterId(encounterId)
                    .setDispenseIds(dispenseIds);
                inventoryInsurancePort.uploadAfterOutpatientReturn(uploadCommand);
            }
        } catch (RuntimeException ex) {
            log.warn("医保进销存3506上传失败（本地退药已成功）", ex);
            return MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_SUCCESS_MESSAGE
                + "；"
                + ErrorCodeConstants.EX_OPP_207_MEDICARE_PARTIAL_FAILURE
                + "："
                + ex.getMessage();
        }
        return null;
    }

    private static void mergeMedicareUploadWarning(ConfirmMedicineReturnResponse response, String warningMessage) {
        if (response == null || StringUtils.isBlank(warningMessage)) {
            return;
        }
        response.setMessage(warningMessage);
    }

    private void validatePageReturnPatientRequest(PageReturnPatientRequest request) {
        if (request.getPageNo() == null || request.getPageNo() < 1) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "pageNo 必须大于等于 1");
        }
        if (request.getPageSize() == null) {
            request.setPageSize(10);
        }
        if (request.getPageSize() < 1) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED, "pageSize 非法");
        }
        if (request.getPageSize() > 100) {
            request.setPageSize(100);
        }
    }

    private void validateConfirmMedicineReturnRequest(ConfirmMedicineReturnRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "请求不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "请选择要退药的药品");
        }
        if (StringUtils.isBlank(request.getConfirmType())) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_301_ITEMS_EMPTY, "confirmType 不能为空");
        }
        boolean decline =
            MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_CONFIRM_TYPE_DECLINE.equalsIgnoreCase(
                request.getConfirmType().trim());
        if (decline && StringUtils.isBlank(request.getReason())) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_302_DECLINE_REASON_REQUIRED, "拒绝退药须填写原因");
        }
    }

    private Long requireOperatorUserId() {
        return currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.EX_PHA_002_101_LOAD_FAILED,
            "用户未登录");
    }

    private Long resolvePractitionerIdForAudit(Long operatorId) {
        Long pid = currentPractitionerResolver.findPractitionerIdOrNull();
        if (pid != null) {
            return pid;
        }
        return operatorId;
    }

    private void assertPendingRefundDispense(DispenseStatus status, Long dispenseId) {
        if (status == null) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_201_STATUS_NOT_ALLOWED, "发放单状态缺失");
        }
        if (DispenseStatus.REFUNDED.equals(status) || DispenseStatus.DECLINED.equals(status)) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_202_DUPLICATE_OR_CLOSED, "该药品已退药或已拒绝，请勿重复操作");
        }
        if (!DispenseStatus.PENDING_REFUND.equals(status)) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_201_STATUS_NOT_ALLOWED,
                "当前状态不允许退药，请刷新后重试：" + dispenseId);
        }
    }

    private BigDecimal resolveRefundQuantity(MedicationDispense md) {
        BigDecimal q = md.getDispenseQuantity();
        if (q == null) {
            q = md.getQuantity();
        }
        if (q == null) {
            return BigDecimal.ZERO;
        }
        return q.abs();
    }

    private void collectMedicationTraceAndInventory(
        MedicationDispense md,
        List<AppendTraceRecordsCommand.AppendTraceRecordItemCommand> traceItems,
        Map<Long, BigDecimal> inventoryIncreaseMerged
    ) {
        if (StringUtils.isNotBlank(md.getTraceNo())) {
            for (String part : md.getTraceNo().split(GeneralSharedConstants.COMMA)) {
                String traceNo = part.trim();
                if (traceNo.isEmpty()) {
                    continue;
                }
                AppendTraceRecordsCommand.AppendTraceRecordItemCommand it =
                    new AppendTraceRecordsCommand.AppendTraceRecordItemCommand();
                it.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
                it.setItemId(md.getMedicationId());
                it.setLocationTypeEnum(LocationForm.PHARMACY.getCode());
                it.setLocationId(md.getLocationId());
                it.setLocationStoreId(null);
                it.setLotNumber(md.getLotNumber());
                it.setTraceNo(traceNo);
                it.setStatusEnum(TraceDirection.IN.getCode());
                it.setUnitCode(md.getUnitCode());
                it.setOperationType(TraceNumberRecordOperationType.MEDICATION_RETURN_IN.getCode());
                it.setBusNo(md.getBusNo());
                traceItems.add(it);
            }
        }
        if (StringUtils.isBlank(md.getLotNumber())) {
            return;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        lookup.setProductDefinitionId(md.getMedicationId());
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        lookups.add(lookup);
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionBatchLoader.loadAsMap(lookups);
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, md.getMedicationId());
        ProductDefinitionDisplayDto product = null;
        if (key != null) {
            product = productByKey.get(key);
        }
        if (product == null) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_203_INVENTORY_MISSING, "药品定义缺失，无法换算退库数量");
        }
        BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            ProductDefinitionDisplaySupport.toUnitConversionSource(product), md.getUnitCode(), this.resolveRefundQuantity(md));
        if (splitQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.EX_PHA_002_203_INVENTORY_MISSING, "退库数量非法，无法回补库存");
        }
        Long inventoryItemId = this.inventoryItemQueryService.requireInventoryItemIdForMedicationDispenseBatch(
            TableNameConstants.MED_MEDICATION_DEFINITION,
            md.getMedicationId(),
            md.getLocationId(),
            md.getLotNumber());
        BigDecimal existed = inventoryIncreaseMerged.get(inventoryItemId);
        if (existed == null) {
            inventoryIncreaseMerged.put(inventoryItemId, splitQty);
        } else {
            inventoryIncreaseMerged.put(inventoryItemId, existed.add(splitQty));
        }
    }

    /**
     * 为退药明细行回填总价：按就诊 + 原药嘱 {@code service_id} 批量查收费明细并汇总。
     *
     * @param encounterId 就诊主键
     * @param rows        读侧明细行；无匹配收费时保持原值（默认 0）
     */
    private void applyChargeTotalPriceByOriginRequestId(Long encounterId, List<ReturnMedicineDetailReadRow> rows) {
        if (encounterId == null || rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> originRequestIds = new LinkedHashSet<>();
        for (ReturnMedicineDetailReadRow row : rows) {
            if (row == null || row.getRefundOriginRequestId() == null) {
                continue;
            }
            originRequestIds.add(row.getRefundOriginRequestId());
        }
        if (originRequestIds.isEmpty()) {
            return;
        }
        ChargeItemQueryCommand chargeQuery = new ChargeItemQueryCommand();
        chargeQuery.setServiceIds(originRequestIds);
        List<ChargeItem> chargeRows = this.chargeItemQueryService.listChargeItemsByServiceIds(chargeQuery);
        Map<Long, BigDecimal> totalPriceByServiceId = new HashMap<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getServiceId() == null) {
                continue;
            }
            if (!encounterId.equals(chargeRow.getEncounterId())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(chargeRow.getServiceTable())) {
                continue;
            }
            if (!this.isReturnMedicinePricingChargeStatus(chargeRow.getStatusEnum())) {
                continue;
            }
            Long serviceKey = chargeRow.getServiceId();
            if (chargeRow.getTotalPrice() != null) {
                BigDecimal existed = totalPriceByServiceId.get(serviceKey);
                if (existed == null) {
                    totalPriceByServiceId.put(serviceKey, chargeRow.getTotalPrice());
                } else {
                    totalPriceByServiceId.put(serviceKey, existed.add(chargeRow.getTotalPrice()));
                }
            }
        }
        for (ReturnMedicineDetailReadRow row : rows) {
            if (row == null || row.getRefundOriginRequestId() == null) {
                continue;
            }
            BigDecimal total = totalPriceByServiceId.get(row.getRefundOriginRequestId());
            if (total != null) {
                // 退药页展示应退金额绝对值（与应退数量口径一致）
                row.setTotalPrice(total.abs());
            }
        }
    }

    /**
     * 退药明细应退金额所采纳的收费状态（与门诊发药定价 Query 子集一致）。
     *
     * @param statusEnum 收费明细 status_enum code
     * @return true 表示可纳入应退金额汇总
     */
    private boolean isReturnMedicinePricingChargeStatus(String statusEnum) {
        if (statusEnum == null) {
            return false;
        }
        if (ChargeItemStatus.DRAFT.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.PLANNED.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.SETTLED.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.REFUNDING.getCode().equals(statusEnum)) {
            return true;
        }
        if (ChargeItemStatus.REFUNDED.getCode().equals(statusEnum)) {
            return true;
        }
        return false;
    }
}
