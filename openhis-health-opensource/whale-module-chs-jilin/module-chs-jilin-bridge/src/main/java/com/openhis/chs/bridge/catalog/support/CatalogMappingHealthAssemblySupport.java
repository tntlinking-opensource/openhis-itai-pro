/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.support;

import com.openhis.chs.domain.settlement.support.SettlementTraceCountSupport;
import com.openhis.chs.domain.share.enums.ListType;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.share.constants.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.command.RevokeCatalog3302RowCommand;
import com.openhis.chs.domain.catalog.command.UploadCatalog3301RowCommand;
import com.openhis.health.domain.administration.devicedefinition.command.SyncDeviceDefinitionYbMappingCommand;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionDomainService;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.command.SyncInsuranceCatalogMappingCommand;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionDetailQueryModel;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionDomainService;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.activitydefinition.command.SyncActivityYbMappingCommand;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionBaseQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionDomainService;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对码页：从 health 主数据加载院内品项，并回写 yb_match_flag。
 * <p>负责 3301/3302 行组装与 health domain 对码状态同步，不直接调用 gateway。</p>
 */
@Component
@RequiredArgsConstructor
public class CatalogMappingHealthAssemblySupport {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final MedicationDefinitionDomainService medicationDefinitionDomainService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ActivityDefinitionDomainService activityDefinitionDomainService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    private final DeviceDefinitionDomainService deviceDefinitionDomainService;

    /** 加载药品定义详情，不存在时由 query 层抛错。 */
    public MedicationDefinitionDetailQueryModel requireMedication(Long medicationDefId) {
        return medicationDefinitionQueryService.getMedicationCatalog(medicationDefId);
    }

    /** 加载诊疗项目基础信息。 */
    public ActivityDefinitionBaseQueryModel requireActivityBase(Long activityDefId) {
        ActivityDefinitionDetailQueryModel detail = activityDefinitionQueryService.getServiceCatalog(activityDefId);
        if (detail.getBaseInfo() == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND, "诊疗项目定义不存在");
        }
        return detail.getBaseInfo();
    }

    /** 加载耗材定义。 */
    public DeviceDefinition requireDevice(Long deviceDefId) {
        DeviceDefinition device = deviceDefinitionQueryService.getDeviceDefinition(deviceDefId);
        if (device == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_NOT_FOUND, "耗材定义不存在");
        }
        return device;
    }

    /** 组装药品 3301 上行行（fixmedins_hilist_id 优先取 bus_no）。 */
    public UploadCatalog3301RowCommand toMedicationUploadRow(
            MedicationDefinitionDetailQueryModel med,
            String listType
    ) {
        requireYbNo(med.getYbNo());
        return new UploadCatalog3301RowCommand()
            .setProductTable(TableNameConstants.MED_MEDICATION_DEFINITION)
            .setProductId(med.getId())
            .setFixmedinsHilistId(CatalogMappingRowSupport.fixmedinsHilistId(med.getBusNo(), med.getId()))
            .setFixmedinsHilistName(med.getName())
            .setListType(CatalogMappingRowSupport.listType(listType, ListType.WESTERN_DRUG.getCode()))
            .setMedListCodg(med.getYbNo().trim())
            .setAprvno(med.getApprovalNumber())
            .setSpec(med.getTotalVolume());
    }

    /** 组装诊疗项目 3301 上行行。 */
    public UploadCatalog3301RowCommand toActivityUploadRow(
            ActivityDefinitionBaseQueryModel activity,
            String listType
    ) {
        requireYbNo(activity.getYbNo());
        return new UploadCatalog3301RowCommand()
            .setProductTable(TableNameConstants.WKF_ACTIVITY_DEFINITION)
            .setProductId(activity.getId())
            .setFixmedinsHilistId(CatalogMappingRowSupport.fixmedinsHilistId(activity.getBusNo(), activity.getId()))
            .setFixmedinsHilistName(activity.getName())
            .setListType(CatalogMappingRowSupport.listType(listType, ListType.MEDICAL_SERVICE.getCode()))
            .setMedListCodg(activity.getYbNo().trim());
    }

    /** 组装耗材 3301 上行行（med_list_codg 截前 15 位）。 */
    public UploadCatalog3301RowCommand toDeviceUploadRow(DeviceDefinition device, String listType) {
        requireYbNo(device.getYbNo());
        return new UploadCatalog3301RowCommand()
            .setProductTable(TableNameConstants.ADM_DEVICE_DEFINITION)
            .setProductId(device.getId())
            .setFixmedinsHilistId(CatalogMappingRowSupport.fixmedinsHilistId(device.getBusNo(), device.getId()))
            .setFixmedinsHilistName(device.getName())
            .setListType(CatalogMappingRowSupport.listType(listType, ListType.MEDICAL_CONSUMABLE.getCode()))
            .setMedListCodg(SettlementTraceCountSupport.toPlatformMedListCodg(
                TableNameConstants.ADM_DEVICE_DEFINITION, device.getYbNo().trim()))
            .setAprvno(device.getApprovalNumber())
            .setSpec(device.getTotalVolume());
    }

    /** 组装药品 3302 撤销上行行。 */
    public RevokeCatalog3302RowCommand toMedicationRevokeRow(
            MedicationDefinitionDetailQueryModel med,
            String listType
    ) {
        requireYbNo(med.getYbNo());
        return new RevokeCatalog3302RowCommand()
            .setProductTable(TableNameConstants.MED_MEDICATION_DEFINITION)
            .setProductId(med.getId())
            .setFixmedinsHilistId(CatalogMappingRowSupport.fixmedinsHilistId(med.getBusNo(), med.getId()))
            .setListType(CatalogMappingRowSupport.listType(listType, ListType.WESTERN_DRUG.getCode()))
            .setMedListCodg(med.getYbNo().trim());
    }

    /** 组装诊疗项目 3302 撤销上行行。 */
    public RevokeCatalog3302RowCommand toActivityRevokeRow(
            ActivityDefinitionBaseQueryModel activity,
            String listType
    ) {
        requireYbNo(activity.getYbNo());
        return new RevokeCatalog3302RowCommand()
            .setProductTable(TableNameConstants.WKF_ACTIVITY_DEFINITION)
            .setProductId(activity.getId())
            .setFixmedinsHilistId(CatalogMappingRowSupport.fixmedinsHilistId(activity.getBusNo(), activity.getId()))
            .setListType(CatalogMappingRowSupport.listType(listType, ListType.MEDICAL_SERVICE.getCode()))
            .setMedListCodg(activity.getYbNo().trim());
    }

    /** 组装耗材 3302 撤销上行行（med_list_codg 截前 15 位）。 */
    public RevokeCatalog3302RowCommand toDeviceRevokeRow(DeviceDefinition device, String listType) {
        requireYbNo(device.getYbNo());
        return new RevokeCatalog3302RowCommand()
            .setProductTable(TableNameConstants.ADM_DEVICE_DEFINITION)
            .setProductId(device.getId())
            .setFixmedinsHilistId(CatalogMappingRowSupport.fixmedinsHilistId(device.getBusNo(), device.getId()))
            .setListType(CatalogMappingRowSupport.listType(listType, ListType.MEDICAL_CONSUMABLE.getCode()))
            .setMedListCodg(SettlementTraceCountSupport.toPlatformMedListCodg(
                TableNameConstants.ADM_DEVICE_DEFINITION, device.getYbNo().trim()));
    }

    /** 3301 成功后回写药品 yb_match_flag / chrgitm_lv。 */
    public void syncMedicationMatchFlag(
            Long medId,
            String ybNo,
            Integer chrgitmLv,
            boolean matched,
            Long operatorId
    ) {
        SyncInsuranceCatalogMappingCommand sync = new SyncInsuranceCatalogMappingCommand();
        sync.setOperatorId(operatorId);
        SyncInsuranceCatalogMappingCommand.SyncInsuranceCatalogMappingItemCommand item =
            new SyncInsuranceCatalogMappingCommand.SyncInsuranceCatalogMappingItemCommand();
        item.setId(medId);
        item.setYbNo(ybNo);
        item.setYbMatchFlag(matched);
        item.setChrgitmLv(chrgitmLv);
        item.setSyncTime(LocalDateTime.now());
        sync.setItems(List.of(item));
        medicationDefinitionDomainService.syncInsuranceCatalogMapping(sync);
    }

    /** 3301/3302 后回写诊疗项目 yb_match_flag / chrgitm_lv。 */
    public void syncActivityMatchFlag(
            Long activityId,
            String ybNo,
            Integer chrgitmLv,
            boolean matched,
            Long operatorId
    ) {
        SyncActivityYbMappingCommand sync = new SyncActivityYbMappingCommand();
        sync.setOperatorId(operatorId);
        SyncActivityYbMappingCommand.SyncActivityYbMappingItemCommand item =
            new SyncActivityYbMappingCommand.SyncActivityYbMappingItemCommand();
        item.setActivityId(activityId);
        item.setYbNo(ybNo);
        item.setYbMatchFlag(matched);
        item.setChrgitmLv(chrgitmLv);
        sync.setItems(List.of(item));
        activityDefinitionDomainService.syncActivityYbMapping(sync);
    }

    /** 3301/3302 后回写耗材 yb_match_flag / chrgitm_lv（不再写 yb_org_no）。 */
    public void syncDeviceMatchFlag(DeviceDefinition device, boolean matched, Long operatorId) {
        syncDeviceYbMapping(
            device.getId(),
            device.getYbFlag(),
            device.getYbNo(),
            matched,
            device.getChrgitmLv(),
            operatorId);
    }

    /**
     * 1317 等场景：按主键回写耗材医保编码与对码标志。
     *
     * @param deviceId   耗材定义主键
     * @param ybFlag     是否医保
     * @param ybNo       医保目录编码
     * @param matched    是否已对照
     * @param chrgitmLv  收费项目等级
     * @param operatorId 操作人
     */
    public void syncDeviceYbMapping(
            Long deviceId,
            Boolean ybFlag,
            String ybNo,
            boolean matched,
            Integer chrgitmLv,
            Long operatorId) {
        SyncDeviceDefinitionYbMappingCommand sync = new SyncDeviceDefinitionYbMappingCommand();
        sync.setOperatorId(operatorId);
        SyncDeviceDefinitionYbMappingCommand.Item item = new SyncDeviceDefinitionYbMappingCommand.Item();
        item.setId(deviceId);
        item.setYbFlag(ybFlag);
        // 回写保留完整医保编码；上传 med_list_codg 时再截前 15
        if (isNotEmpty(ybNo)) {
            item.setYbNo(ybNo.trim());
        } else {
            item.setYbNo(ybNo);
        }
        item.setYbMatchFlag(matched);
        item.setChrgitmLv(chrgitmLv);
        sync.setItems(List.of(item));
        deviceDefinitionDomainService.syncDeviceDefinitionYbMapping(sync);
    }

    private static void requireYbNo(String ybNo) {
        if (isEmpty(ybNo)) {
            throw new BusinessException("YB_NO_REQUIRED", "请先选择国家医保目录编码（yb_no）后再上传 3301");
        }
    }
}
