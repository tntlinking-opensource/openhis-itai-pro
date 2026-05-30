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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.service;

import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.common.validator.PageWindowGate;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.assembler.NurseStationInpatientBillingCatalogAssembler;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.assembler.NurseStationInpatientBillingListAssembler;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.assembler.NurseStationInpatientBillingWriteAssembler;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingParentRequestAnchorDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingPatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationSupplementParentOrderRowDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.mapper.NurseStationInpatientBillingMapper;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.ConfirmDirectNurseStationPricingRequest;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.ConfirmSupplementNurseStationPricingRequest;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.ListNurseStationPricedOrdersRequest;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.ListNurseStationSupplementParentOrdersRequest;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.PageNurseStationPricingCatalogRequest;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.response.ConfirmDirectNurseStationPricingResponse;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.response.ConfirmSupplementNurseStationPricingResponse;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.response.ListNurseStationPricedOrdersResponse;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.response.ListNurseStationSupplementParentOrdersResponse;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.response.PageNurseStationPricingCatalogResponse;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.application.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.application.common.assembler.order.InpatientOrderListMergeAssembler;
import com.openhis.health.application.querysupport.order.InpatientOrderLookupLoader;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.application.querysupport.product.ProductDefinitionCatalogPageLoader;
import com.openhis.health.domain.custom.servicelocationtemplate.service.ServiceLocationTemplateQueryService;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 住院护士站划价应用编排。
 *
 * <p>选品目录：诊疗与耗材定价目录分页（排除药品）。</p>
 * <p>直接划价/医嘱补费：不经校对签发，同事务落请求、收费与耗材发放草稿。</p>
 * <p>列表：父医嘱候选与已划价记录，均收窄至护士管理病区可见就诊。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationInpatientBillingAppService {

    private final CurrentOperatorResolver currentOperatorResolver;
    private final PageWindowGate pageWindowGate;
    private final ICurrentUser currentUser;
    private final LocationQueryService locationQueryService;
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ServiceLocationTemplateQueryService serviceLocationTemplateQueryService;
    private final InpatientOrderListMergeAssembler inpatientOrderListMergeAssembler;
    private final InpatientOrderLookupLoader inpatientOrderLookupLoader;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final DeviceRequestDomainService deviceRequestDomainService;
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final NurseStationInpatientBillingMapper nurseStationInpatientBillingMapper;
    private final NurseStationInpatientBillingCatalogAssembler nurseStationInpatientBillingCatalogAssembler;
    private final NurseStationInpatientBillingListAssembler nurseStationInpatientBillingListAssembler;
    private final NurseStationInpatientBillingWriteAssembler nurseStationInpatientBillingWriteAssembler;

    /**
     *
     * @param request 含定价来源、分页与关键字
     * @return 带定价行、单位槽、套餐子项、吸氧/心电 Gate 标记的目录页
     * @throws BusinessException EX-IP-PRC-101 分页/定价来源非法
     */
    public PageNurseStationPricingCatalogResponse pageNurseStationPricingCatalog(
        PageNurseStationPricingCatalogRequest request) {
        try {
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientNurseStationConstants.EX_IP_PRC_101,
                "分页参数无效");
            String pricingSource = StringUtils.defaultIfBlank(
                request.getPricingSourceCode(), DoctorStationAdviceConstants.PRICING_SOURCE_RETAIL_PRICE);
            if (!DoctorStationAdviceConstants.PRICING_SOURCE_RETAIL_PRICE.equals(pricingSource)
                && !DoctorStationAdviceConstants.PRICING_SOURCE_BATCH_SELLING_PRICE.equals(pricingSource)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_101,
                    "医嘱定价来源未配置，请联系管理员");
            }
            var pageQuery = nurseStationInpatientBillingMapper.toProductDefinitionPageQuery(request);
            Page<ProductDefinitionDisplayDto> page = productDefinitionCatalogPageLoader.pageProductDefinition(pageQuery);
            var rows = nurseStationInpatientBillingCatalogAssembler.assembleCatalogRows(request, page);
            return PagedResultBuilder.build(new PageNurseStationPricingCatalogResponse(), rows, page);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_101,
                "划价选品目录加载失败");
        }
    }

    /**
     *
     * @param request 1～50 行写明细，每行须 accountId 与完整定价字段
     * @return 新建诊疗/耗材请求 id 汇总
     * @throws BusinessException EX-IP-PRC-201 夹带父锚点；其余同写路径
     */
    @Transactional
    public ConfirmDirectNurseStationPricingResponse confirmDirectNurseStationPricing(
        ConfirmDirectNurseStationPricingRequest request) {
        try {
            nurseStationInpatientBillingMapper.validateDirectPricingItems(request.getItems());
            return this.confirmPricingWrite(request.getItems());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_205,
                "划价失败，请稍后重试");
        }
    }

    /**
     *
     * @param request 写明细，携带 parentRequestAnchor
     * @return 与 IF-02 同构的写结果
     * @throws BusinessException EX-IP-PRC-301 父医嘱非法
     */
    @Transactional
    public ConfirmSupplementNurseStationPricingResponse confirmSupplementNurseStationPricing(
        ConfirmSupplementNurseStationPricingRequest request) {
        try {
            nurseStationInpatientBillingMapper.validateSupplementPricingItems(request.getItems());
            for (NurseStationPricingWriteItemDto row : request.getItems()) {
                this.assertParentOrderAllowed(row.getParentRequestAnchor(), row.getEncounterId());
            }
            ConfirmDirectNurseStationPricingResponse core = this.confirmPricingWrite(request.getItems());
            ConfirmSupplementNurseStationPricingResponse resp = new ConfirmSupplementNurseStationPricingResponse();
            resp.setSuccess(core.getSuccess());
            resp.setProcessedCount(core.getProcessedCount());
            resp.setCreatedServiceRequestIds(core.getCreatedServiceRequestIds());
            resp.setCreatedDeviceRequestIds(core.getCreatedDeviceRequestIds());
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_205,
                "划价失败，请稍后重试");
        }
    }

    /**
     *
     * @param request 多选 encounter、长临筛选、关键字、科室
     * @return 按就诊分组的父医嘱展示行
     * @throws BusinessException EX-IP-PRC-101/102 列表加载失败或就诊不可见
     */
    public ListNurseStationSupplementParentOrdersResponse listNurseStationSupplementParentOrders(
        ListNurseStationSupplementParentOrdersRequest request) {
        try {
            List<Long> visibleEncounterIds = this.resolveVisibleEncounterIds(request.getEncounterIds());
            String therapyEnum = nurseStationInpatientBillingMapper.resolveTherapyEnum(request.getTherapyType());
            List<InpatientOrderListSkeleton> skeleton =
                inpatientOrderListMergeAssembler.mergeNurseStationSupplementOrders(visibleEncounterIds, therapyEnum);
            String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
            Map<Long, List<MedicationRequestListQueryModel>> medByEncounter = new HashMap<>();
            Map<Long, List<ServiceRequestListModel>> svcByEncounter = new HashMap<>();
            for (Long encounterId : visibleEncounterIds) {
                medByEncounter.put(encounterId,
                    medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                        encounterId, doctorSource));
                svcByEncounter.put(encounterId,
                    serviceRequestQueryService.listServiceRequestsByGenerateSource(encounterId, doctorSource));
            }
            List<NurseStationPricingPatientGroupDto> groups =
                nurseStationInpatientBillingListAssembler.assembleSupplementParentGroups(
                    skeleton, medByEncounter, svcByEncounter);
            List<NurseStationSupplementParentOrderRowDto> flat = new ArrayList<>();
            for (NurseStationPricingPatientGroupDto group : groups) {
                if (group.getOrders() != null) {
                    flat.addAll(group.getOrders());
                }
            }
            flat = nurseStationInpatientBillingMapper.filterByKeywordAndOrg(
                flat, request.getKeyword(), request.getOrgId());
            List<NurseStationPricingPatientGroupDto> regrouped = this.regroupSupplementRows(flat);
            ListNurseStationSupplementParentOrdersResponse resp = new ListNurseStationSupplementParentOrdersResponse();
            resp.setGroups(regrouped);
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_101,
                "医嘱列表加载失败");
        }
    }

    /**
     *
     * @param request 多选 encounter、划价时间窗、可选父医嘱 id 过滤补费子行
     * @return 按就诊分组的已划价行（含费用态、耗材发放态）
     * @throws BusinessException EX-IP-PRC-101/102
     */
    public ListNurseStationPricedOrdersResponse listNurseStationPricedOrders(
        ListNurseStationPricedOrdersRequest request) {
        try {
            List<Long> visibleEncounterIds = this.resolveVisibleEncounterIds(request.getEncounterIds());
            List<InpatientOrderListSkeleton> skeleton = inpatientOrderListMergeAssembler.mergeNurseStationPricedOrders(
                visibleEncounterIds, request.getPricedTimeFrom(), request.getPricedTimeTo(), request.getParentRequestId());
            Set<Long> serviceIds = new LinkedHashSet<>();
            Set<Long> deviceReqIds = new LinkedHashSet<>();
            for (InpatientOrderListSkeleton row : skeleton) {
                if (row == null || row.getOrderId() == null) {
                    continue;
                }
                if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(row.getOrderType())) {
                    deviceReqIds.add(row.getOrderId());
                } else {
                    serviceIds.add(row.getOrderId());
                }
            }
            // ChargeItem.service_id 与诊疗/耗材请求主键同 id 关联，须合并两轨 id 再批量加载
            Set<Long> chargeAnchorIds = new LinkedHashSet<>(serviceIds);
            chargeAnchorIds.addAll(deviceReqIds);
            ChargeItemQueryCommand chargeCmd = new ChargeItemQueryCommand();
            chargeCmd.setServiceIds(new ArrayList<>(chargeAnchorIds));
            List<ChargeItem> chargeRows = chargeItemQueryService.listChargeItemsByServiceIds(chargeCmd);
            Map<Long, List<ChargeItem>> chargeByServiceId = new HashMap<>();
            for (ChargeItem charge : chargeRows) {
                if (charge == null || charge.getServiceId() == null) {
                    continue;
                }
                chargeByServiceId.computeIfAbsent(charge.getServiceId(), k -> new ArrayList<>()).add(charge);
            }
            Map<Long, List<Long>> dispenseIdsByDeviceReqId =
                deviceDispenseQueryService.mapDeviceDispenseIdsByDeviceRequestIds(deviceReqIds);
            Map<Long, NurseStationInpatientBillingListAssembler.PricedOrderRowFacts> pricedRowFactsByOrderId =
                nurseStationInpatientBillingListAssembler.buildPricedRowFactsByOrderId(skeleton);
            List<NurseStationPricingPatientGroupDto> groups =
                nurseStationInpatientBillingListAssembler.assemblePricedOrderGroups(
                    skeleton, chargeByServiceId, dispenseIdsByDeviceReqId, pricedRowFactsByOrderId);
            ListNurseStationPricedOrdersResponse resp = new ListNurseStationPricedOrdersResponse();
            resp.setGroups(groups);
            return resp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_101,
                "已划价列表加载失败");
        }
    }

    /**
     *
     * @param items 1～50 行；直接划价无 based_on，补费由 Mapper 写入 based_on
     * @return 写结果与新建 id 列表
     */
    private ConfirmDirectNurseStationPricingResponse confirmPricingWrite(
        List<NurseStationPricingWriteItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_204,
                "划价项目无效或价格异常，请重新选品");
        }
        if (items.size() > InpatientNurseStationConstants.PRICING_WRITE_BATCH_MAX) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_204,
                "划价项目无效或价格异常，请重新选品");
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_PRC_205,
            "划价失败，请稍后重试");
        Long requesterId = currentUser.getCurrentPractitionerId();
        Set<Long> encounterIds = new HashSet<>();
        for (NurseStationPricingWriteItemDto row : items) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        this.assertEncountersVisible(new ArrayList<>(encounterIds));
        // 耗材发放库房：前端不传时按就诊住院科室 + 取药/发材科室模板自动解析（BR-IP-PRC-009）
        this.fillDevicePerformLocationWhenAbsent(items, new ArrayList<>(encounterIds));
        List<NurseStationPricingWriteItemDto> serviceRows = new ArrayList<>();
        List<NurseStationPricingWriteItemDto> deviceRows = new ArrayList<>();
        for (NurseStationPricingWriteItemDto row : items) {
            if (row == null) {
                continue;
            }
            this.validateWriteRow(row);
            // 吸氧/心电：当日上限 + 在院余量 Gate（BR-IP-PRC-010）
            if (Boolean.TRUE.equals(row.getOxygenOrEcgFlag())
                || StringUtils.isNotBlank(row.getDurationGateKind())) {
                nurseStationInpatientBillingWriteAssembler.assertOxygenOrEcgDuration(row);
            }
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getProductTableName())) {
                serviceRows.add(row);
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(row.getProductTableName())) {
                // 耗材划价必须指定发放库房（BR-IP-PRC-009），不做库存校验
                if (row.getPerformLocation() == null) {
                    throw new BusinessException(
                        InpatientNurseStationConstants.EX_IP_PRC_202,
                        "耗材划价失败：请选择发放库房");
                }
                deviceRows.add(row);
            } else {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_204,
                    "划价项目无效或价格异常，请重新选品");
            }
        }
        InpatientEncounterContextQueryModel orgSnapshot = this.requireVisibleSnapshot(
            new ArrayList<>(encounterIds), items.get(0).getPatientId());
        Long orgId = orgSnapshot.getOrganizationId();
        List<Long> createdServiceIds = new ArrayList<>();
        List<Long> createdDeviceIds = new ArrayList<>();
        // 耗材轨：请求 COMPLETED + 待发发放 + 费用待结算（BR-IP-PRC-008）
        if (!deviceRows.isEmpty()) {
            var deviceCmd = nurseStationInpatientBillingMapper.toCreateExecutedDeviceOrderCommand(deviceRows, operatorId);
            List<Long> deviceReqIds = deviceRequestDomainService.createExecutedDeviceOrder(deviceCmd);
            createdDeviceIds.addAll(deviceReqIds);
            for (int i = 0; i < deviceRows.size(); i++) {
                Long deviceReqId = deviceReqIds.get(i);
                NurseStationPricingWriteItemDto row = deviceRows.get(i);
                var genCmd = nurseStationInpatientBillingMapper.toGenerateDeviceDispenseFromTemporaryPathCommand(
                    deviceReqId, row, operatorId);
                Long dispenseId = deviceDispenseDomainService.generateDeviceDispenseFromTemporaryPath(genCmd);
                deviceDispenseDomainService.promoteDraftDispensesForDeviceRequests(List.of(deviceReqId), operatorId);
                var chargeCmd = nurseStationInpatientBillingMapper.toCreateChargeItemCommand(
                    row,
                    TableNameConstants.WKF_DEVICE_REQUEST,
                    deviceReqId,
                    TableNameConstants.WKF_DEVICE_DISPENSE,
                    dispenseId,
                    operatorId,
                    orgId);
                chargeItemDomainService.priceByNurseStation(chargeCmd);
            }
        }
        // 诊疗轨：请求 VERIFIED + 费用待结算；套餐子项同事务展开（BR-IP-PRC-007/011）
        if (!serviceRows.isEmpty()) {
            var serviceCmd = nurseStationInpatientBillingMapper.toCreateFromNursePricingCommand(
                serviceRows, operatorId, requesterId);
            List<Long> serviceRequestIds = serviceRequestDomainService.createFromNursePricing(serviceCmd);
            createdServiceIds.addAll(serviceRequestIds);
            for (int i = 0; i < serviceRows.size(); i++) {
                NurseStationPricingWriteItemDto row = serviceRows.get(i);
                Long serviceRequestId = serviceRequestIds.get(i);
                var chargeCmd = nurseStationInpatientBillingMapper.toCreateChargeItemCommand(
                    row,
                    TableNameConstants.WKF_SERVICE_REQUEST,
                    serviceRequestId,
                    null,
                    null,
                    operatorId,
                    orgId);
                chargeItemDomainService.priceByNurseStation(chargeCmd);
                nurseStationInpatientBillingWriteAssembler.expandPackageChildrenIfPresent(
                    row, operatorId, requesterId, orgId, createdServiceIds);
            }
        }
        ConfirmDirectNurseStationPricingResponse resp = new ConfirmDirectNurseStationPricingResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setProcessedCount(items.size());
        resp.setCreatedServiceRequestIds(createdServiceIds);
        resp.setCreatedDeviceRequestIds(createdDeviceIds);
        return resp;
    }

    /**
     * 为未传 {@code performLocation} 的耗材划价行，按就诊住院科室与 {@link ServiceLocationTemplateQueryService} 解析默认发放库房。
     *
     * @param items        写路径行
     * @param encounterIds 本批就诊（已通过可见性 Gate）
     */
    private void fillDevicePerformLocationWhenAbsent(
        List<NurseStationPricingWriteItemDto> items,
        List<Long> encounterIds) {
        if (items == null || items.isEmpty()) {
            return;
        }
        boolean needsResolve = false;
        for (NurseStationPricingWriteItemDto row : items) {
            if (row != null
                && TableNameConstants.ADM_DEVICE_DEFINITION.equals(row.getProductTableName())
                && row.getPerformLocation() == null) {
                needsResolve = true;
                break;
            }
        }
        if (!needsResolve) {
            return;
        }
        var encounterQuery = nurseStationInpatientBillingMapper.toInpatientEncounterPageQuery(
            encounterIds, this.resolveManagedWardIds(currentUser.getCurrentPractitionerId()));
        List<InpatientEncounterContextQueryModel> encounterRows =
            encounterContextFacade.listInpatientEncounterContexts(encounterQuery);
        Map<Long, InpatientEncounterContextQueryModel> encounterById = new HashMap<>();
        for (InpatientEncounterContextQueryModel encounterRow : encounterRows) {
            if (encounterRow != null && encounterRow.getEncounterId() != null) {
                encounterById.put(encounterRow.getEncounterId(), encounterRow);
            }
        }
        List<ProductDefinitionBatchLookup> productLookups = new ArrayList<>();
        for (NurseStationPricingWriteItemDto row : items) {
            if (row == null || row.getPerformLocation() != null) {
                continue;
            }
            if (!TableNameConstants.ADM_DEVICE_DEFINITION.equals(row.getProductTableName())) {
                continue;
            }
            if (row.getProductDefinitionId() == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.ADM_DEVICE_DEFINITION);
            lookup.setProductDefinitionId(row.getProductDefinitionId());
            productLookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            productDefinitionBatchLoader.loadAsMap(productLookups);
        for (NurseStationPricingWriteItemDto row : items) {
            if (row == null || row.getPerformLocation() != null) {
                continue;
            }
            if (!TableNameConstants.ADM_DEVICE_DEFINITION.equals(row.getProductTableName())) {
                continue;
            }
            InpatientEncounterContextQueryModel encounterRow = encounterById.get(row.getEncounterId());
            Long organizationId = null;
            if (encounterRow != null) {
                organizationId = encounterRow.getOrganizationId();
            }
            if (organizationId == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_202,
                    "耗材划价失败：未配置该住院科室的发放库房");
            }
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.ADM_DEVICE_DEFINITION, row.getProductDefinitionId());
            ProductDefinitionDisplayDto productRow = productByKey.get(productKey);
            String distributionCategory = MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE;
            if (productRow != null && StringUtils.isNotBlank(productRow.getCategoryCode())) {
                distributionCategory = productRow.getCategoryCode();
            }
            Long deviceDefinitionId = row.getDeviceDefId();
            if (deviceDefinitionId == null) {
                deviceDefinitionId = row.getProductDefinitionId();
            }
            Long performLocation = serviceLocationTemplateQueryService.matchDefaultDistributionLocation(
                organizationId,
                TableNameConstants.ADM_DEVICE_DEFINITION,
                distributionCategory,
                deviceDefinitionId,
                null,
                null);
            if (performLocation == null) {
                List<Long> candidateLocations = serviceLocationTemplateQueryService.listCandidateDistributionLocationIds(
                    organizationId,
                    TableNameConstants.ADM_DEVICE_DEFINITION,
                    distributionCategory,
                    null);
                if (!candidateLocations.isEmpty()) {
                    performLocation = candidateLocations.get(0);
                }
            }
            if (performLocation == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_202,
                    "耗材划价失败：未配置该住院科室的发放库房");
            }
            row.setPerformLocation(performLocation);
        }
    }

    /**
     * 写行定价字段完整性（BR-IP-PRC-013/204）。
     *
     * @param row 待提交行
     */
    private void validateWriteRow(NurseStationPricingWriteItemDto row) {
        if (row.getAccountId() == null || row.getChargeItemDefinitionId() == null
            || row.getQuantity() == null || row.getUnitPrice() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_204,
                "划价项目无效或价格异常，请重新选品");
        }
    }

    /**
     * 补费父医嘱须为医生开立且状态可补（BR-IP-PRC-005/301）。
     *
     * @param anchor      父锚点
     * @param encounterId 就诊
     */
    private void assertParentOrderAllowed(NurseStationPricingParentRequestAnchorDto anchor, Long encounterId) {
        if (anchor == null || anchor.getRequestId() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_301,
                "关联医嘱无效，请刷新医嘱列表后重试");
        }
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(anchor.getOrderType())) {
            // 药品父医嘱：须在就诊下医生开立列表中且状态可补
            List<MedicationRequestListQueryModel> meds =
                medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                    encounterId, doctorSource);
            for (MedicationRequestListQueryModel med : meds) {
                if (med != null && anchor.getRequestId().equals(med.getId())) {
                    if (!doctorSource.equals(med.getGenerateSourceEnum())) {
                        throw new BusinessException(
                            InpatientNurseStationConstants.EX_IP_PRC_301,
                            "关联医嘱无效，请刷新医嘱列表后重试");
                    }
                    if (this.isAllowedParentStatus(med.getStatusEnum())) {
                        return;
                    }
                }
            }
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(anchor.getOrderType())) {
            // 诊疗父医嘱：经 OrderInfo 校验来源与状态（与列表骨架一致）
            Optional<InpatientOrderListSkeleton> orderOpt = inpatientOrderLookupLoader.findByOrderTypeAndId(
                ReadModelOrderTypeConstants.SERVICE_REQUEST, anchor.getRequestId());
            if (orderOpt.isPresent()) {
                InpatientOrderListSkeleton order = orderOpt.get();
                if (doctorSource.equals(order.getGenerateSourceEnum())
                    && this.isAllowedParentStatus(order.getStatusEnum())) {
                    return;
                }
            }
        }
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_PRC_301,
            "关联医嘱无效，请刷新医嘱列表后重试");
    }

    /**
     * 父医嘱是否允许补费：已校对或已停嘱（BR-IP-PRC-014）。
     *
     * @param statusEnum 请求状态 code
     * @return 允许为 true
     */
    private boolean isAllowedParentStatus(String statusEnum) {
        if (RequestStatus.VERIFIED.getCode().equals(statusEnum)) {
            return true;
        }
        if (RequestStatus.STOPPED.getCode().equals(statusEnum)) {
            return true;
        }
        return false;
    }

    /**
     * 将请求就诊列表收窄为当前护士管理病区内的可见就诊（BR-IP-NUR-001 同口径）。
     *
     * @param requestedEncounterIds 左侧多选 encounter
     * @return 可见 encounterId 列表（顺序稳定）
     * @throws BusinessException EX-IP-PRC-102 无可见就诊
     */
    private List<Long> resolveVisibleEncounterIds(List<Long> requestedEncounterIds) {
        List<Long> distinct = this.distinctEncounterIds(requestedEncounterIds);
        if (distinct.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_102,
                "当前患者不可用，请刷新左侧列表");
        }
        Long practitionerId = currentUser.getCurrentPractitionerId();
        List<Long> wardIds = this.resolveManagedWardIds(practitionerId);
        // 住院上下文：护士站队列 + 管理病区 + 请求 encounter 交集
        var query = nurseStationInpatientBillingMapper.toInpatientEncounterPageQuery(distinct, wardIds);
        List<InpatientEncounterContextQueryModel> visible =
            encounterContextFacade.listInpatientEncounterContexts(query);
        List<Long> visibleIds = new ArrayList<>();
        Set<Long> requestedSet = new HashSet<>(distinct);
        // 保持请求顺序，仅保留上下文返回的 encounter
        for (InpatientEncounterContextQueryModel row : visible) {
            if (row != null && row.getEncounterId() != null && requestedSet.contains(row.getEncounterId())) {
                visibleIds.add(row.getEncounterId());
            }
        }
        if (visibleIds.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_102,
                "当前患者不可用，请刷新左侧列表");
        }
        return visibleIds;
    }

    /**
     * 单就诊卡片门控：可见且 patientId 与读模型一致。
     *
     * @param encounterIds 目标就诊（通常单元素）
     * @param patientId    左侧卡片患者 id
     * @return 住院信息快照（含 organizationId 供 Charge）
     */
    private InpatientEncounterContextQueryModel requireVisibleSnapshot(List<Long> encounterIds, Long patientId) {
        this.assertEncountersVisible(encounterIds);
        var query = nurseStationInpatientBillingMapper.toInpatientEncounterPageQuery(
            encounterIds, this.resolveManagedWardIds(currentUser.getCurrentPractitionerId()));
        List<InpatientEncounterContextQueryModel> rows =
            encounterContextFacade.listInpatientEncounterContexts(query);
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row != null && patientId.equals(row.getPatientId())) {
                return row;
            }
        }
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_PRC_102,
            "当前患者不可用，请刷新左侧列表");
    }

    /**
     * 断言请求列表中的每个 encounter 均在可见集合内（防伪造 encounterId）。
     *
     * @param encounterIds 待校验就诊列表
     */
    private void assertEncountersVisible(List<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_102,
                "当前患者不可用，请刷新左侧列表");
        }
        // 任一 encounter 不可见则拒绝写/读（防横向越权）
        List<Long> visible = this.resolveVisibleEncounterIds(encounterIds);
        if (visible.size() != new HashSet<>(encounterIds).size()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_102,
                "当前患者不可用，请刷新左侧列表");
        }
    }

    /**
     * 当前护士管辖病区 id 列表（无管辖时返回空列表，由可见性 Gate 拒绝）。
     *
     * @param practitionerId 当前医务人员 id
     * @return 病区 location id 列表
     */
    private List<Long> resolveManagedWardIds(Long practitionerId) {
        List<Location> wards = locationQueryService.listManagedWardsForPractitioner(practitionerId);
        List<Long> ids = new ArrayList<>();
        for (Location ward : wards) {
            if (ward != null && ward.getId() != null) {
                ids.add(ward.getId());
            }
        }
        return ids;
    }

    /**
     * 就诊 id 去重并保持请求顺序。
     *
     * @param encounterIds 原始列表
     * @return 去重后的就诊 id
     */
    private List<Long> distinctEncounterIds(List<Long> encounterIds) {
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        if (encounterIds != null) {
            for (Long id : encounterIds) {
                if (id != null) {
                    set.add(id);
                }
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 关键字过滤后按 encounter 重新分组父医嘱行。
     *
     * @param rows 扁平父医嘱行
     * @return 分组结果
     */
    private List<NurseStationPricingPatientGroupDto> regroupSupplementRows(
        List<NurseStationSupplementParentOrderRowDto> rows) {
        // 关键字过滤后扁平列表 → 按 encounter 重新分组（IF-03 响应结构）
        Map<Long, List<NurseStationSupplementParentOrderRowDto>> byEncounter = new HashMap<>();
        for (NurseStationSupplementParentOrderRowDto row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            byEncounter.computeIfAbsent(row.getEncounterId(), k -> new ArrayList<>()).add(row);
        }
        List<NurseStationPricingPatientGroupDto> groups = new ArrayList<>();
        for (Map.Entry<Long, List<NurseStationSupplementParentOrderRowDto>> entry : byEncounter.entrySet()) {
            NurseStationPricingPatientGroupDto group = new NurseStationPricingPatientGroupDto();
            group.setEncounterId(entry.getKey());
            group.setOrders(entry.getValue());
            groups.add(group);
        }
        return groups;
    }

}
