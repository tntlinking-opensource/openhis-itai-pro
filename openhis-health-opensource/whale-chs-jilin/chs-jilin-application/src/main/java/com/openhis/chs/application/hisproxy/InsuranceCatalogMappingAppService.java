/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.hisproxy;

import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.dto.request.RevokeActivityCatalog3302Request;
import com.openhis.chs.domain.catalog.dto.request.RevokeCatalog3302Request;
import com.openhis.chs.domain.catalog.dto.request.RevokeCatalog3302RowRequest;
import com.openhis.chs.domain.catalog.dto.request.RevokeDeviceCatalog3302Request;
import com.openhis.chs.domain.catalog.dto.request.RevokeMedicationCatalog3302Request;
import com.openhis.chs.domain.catalog.dto.request.UploadActivityCatalog3301Request;
import com.openhis.chs.domain.catalog.dto.request.UploadCatalog3301Request;
import com.openhis.chs.domain.catalog.dto.request.UploadCatalog3301RowRequest;
import com.openhis.chs.domain.catalog.dto.request.UploadDeviceCatalog3301Request;
import com.openhis.chs.domain.catalog.dto.request.UploadMedicationCatalog3301Request;
import com.openhis.chs.domain.catalog.dto.response.Catalog3301UploadResultDto;
import com.openhis.chs.domain.catalog.dto.response.Catalog3301UploadRowResultDto;
import com.openhis.chs.domain.catalog.dto.response.Catalog3302RevokeResultDto;
import com.openhis.chs.domain.catalog.dto.response.Catalog3302RevokeRowResultDto;
import com.openhis.chs.domain.catalog.facade.ChsCatalogMappingOperations;
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
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 院内产品 ↔ 国家目录对照编排（3301 上传 + 回写 yb_match_flag）。
 *
 * <p>HTTP：{@code POST /api/app/insuranceCatalogMapping/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceCatalogMappingAppService {

    private final ChsCatalogMappingOperations catalogMappingOperations;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final MedicationDefinitionDomainService medicationDefinitionDomainService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ActivityDefinitionDomainService activityDefinitionDomainService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    private final DeviceDefinitionDomainService deviceDefinitionDomainService;
    private final CurrentOperatorResolver currentOperatorResolver;

    /**
     * 单条药品【3301】上传；成功则 yb_match_flag=true，失败抛出业务异常并保留 false。
     *
     * @param request 药品定义 ID 与契约 bus_no
     * @return 3301 上传结果
     */
    public Catalog3301UploadResultDto uploadMedicationCatalog3301(UploadMedicationCatalog3301Request request) {
        // Gate：校验登录态与 yb_no
        Long operatorId = currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录");
        MedicationDefinitionDetailQueryModel med =
            medicationDefinitionQueryService.getMedicationCatalog(request.getMedicationDefId());
        if (StringUtils.isBlank(med.getYbNo())) {
            throw new BusinessException("YB_NO_REQUIRED", "请先选择国家医保目录编码（yb_no）后再上传 3301");
        }
        // Pre：组装 3301 行请求
        String listType = resolveListType(request.getListType(), ChsCatalogConstants.LIST_TYPE_WESTERN_DRUG);
        String fixmedinsId = resolveFixmedinsHilistId(med.getBusNo(), med.getId());
        UploadCatalog3301RowRequest row = new UploadCatalog3301RowRequest()
            .setProductTable(TableNameConstants.MED_MEDICATION_DEFINITION)
            .setProductId(med.getId())
            .setFixmedinsHilistId(fixmedinsId)
            .setFixmedinsHilistName(med.getName())
            .setListType(listType)
            .setMedListCodg(med.getYbNo().trim())
            .setAprvno(med.getApprovalNumber())
            .setSpec(med.getTotalVolume());
        // Cmd：调用 3301 上传
        Catalog3301UploadResultDto result = invokeUpload3301(request.getContractBusNo(), List.of(row));
        Catalog3301UploadRowResultDto rowResult = firstRowResult(result);
        // Asm：回写 yb_match_flag 并返回或抛出
        if (rowResult != null && rowResult.isSuccess()) {
            syncMedicationMatchFlag(med.getId(), med.getYbNo(), med.getChrgitmLv(), true, operatorId);
            return result;
        }
        syncMedicationMatchFlag(med.getId(), med.getYbNo(), med.getChrgitmLv(), false, operatorId);
        throw uploadFailed(rowResult, result);
    }

    /**
     * 单条诊疗【3301】上传；成功则 yb_match_flag=true。
     *
     * @param request 诊疗定义 ID 与契约 bus_no
     * @return 3301 上传结果
     */
    public Catalog3301UploadResultDto uploadActivityCatalog3301(UploadActivityCatalog3301Request request) {
        // Gate：校验登录态与 yb_no
        Long operatorId = currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录");
        ActivityDefinitionBaseQueryModel activity = requireActivityBase(request.getActivityDefId());
        if (StringUtils.isBlank(activity.getYbNo())) {
            throw new BusinessException("YB_NO_REQUIRED", "请先选择国家医保目录编码（yb_no）后再上传 3301");
        }
        // Pre：组装 3301 行请求
        String listType = resolveListType(request.getListType(), ChsCatalogConstants.LIST_TYPE_MEDICAL_SERVICE);
        String fixmedinsId = resolveFixmedinsHilistId(activity.getBusNo(), activity.getId());
        UploadCatalog3301RowRequest row = new UploadCatalog3301RowRequest()
            .setProductTable(TableNameConstants.WKF_ACTIVITY_DEFINITION)
            .setProductId(activity.getId())
            .setFixmedinsHilistId(fixmedinsId)
            .setFixmedinsHilistName(activity.getName())
            .setListType(listType)
            .setMedListCodg(activity.getYbNo().trim());
        // Cmd：调用 3301 上传
        Catalog3301UploadResultDto result = invokeUpload3301(request.getContractBusNo(), List.of(row));
        Catalog3301UploadRowResultDto rowResult = firstRowResult(result);
        // Asm：回写 yb_match_flag 并返回或抛出
        if (rowResult != null && rowResult.isSuccess()) {
            syncActivityMatchFlag(activity.getId(), activity.getYbNo(), activity.getChrgitmLv(), true, operatorId);
            return result;
        }
        syncActivityMatchFlag(activity.getId(), activity.getYbNo(), activity.getChrgitmLv(), false, operatorId);
        throw uploadFailed(rowResult, result);
    }

    /**
     * 单条耗材【3301】上传；成功则 yb_match_flag=true。
     *
     * @param request 耗材定义 ID 与契约 bus_no
     * @return 3301 上传结果
     */
    public Catalog3301UploadResultDto uploadDeviceCatalog3301(UploadDeviceCatalog3301Request request) {
        // Gate：校验登录态与 yb_no
        Long operatorId = currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录");
        DeviceDefinition device = requireDeviceDefinition(request.getDeviceDefId());
        if (StringUtils.isBlank(device.getYbNo())) {
            throw new BusinessException("YB_NO_REQUIRED", "请先选择国家医保目录编码（yb_no）后再上传 3301");
        }
        // Pre：组装 3301 行请求
        String listType = resolveListType(request.getListType(), ChsCatalogConstants.LIST_TYPE_MEDICAL_CONSUMABLE);
        String fixmedinsId = resolveFixmedinsHilistId(device.getBusNo(), device.getId());
        UploadCatalog3301RowRequest row = new UploadCatalog3301RowRequest()
            .setProductTable(TableNameConstants.ADM_DEVICE_DEFINITION)
            .setProductId(device.getId())
            .setFixmedinsHilistId(fixmedinsId)
            .setFixmedinsHilistName(device.getName())
            .setListType(listType)
            .setMedListCodg(device.getYbNo().trim())
            .setAprvno(device.getApprovalNumber())
            .setSpec(device.getTotalVolume());
        // Cmd：调用 3301 上传
        Catalog3301UploadResultDto result = invokeUpload3301(request.getContractBusNo(), List.of(row));
        Catalog3301UploadRowResultDto rowResult = firstRowResult(result);
        // Asm：回写 yb_match_flag 并返回或抛出
        if (rowResult != null && rowResult.isSuccess()) {
            syncDeviceMatchFlag(device, true, operatorId);
            return result;
        }
        syncDeviceMatchFlag(device, false, operatorId);
        throw uploadFailed(rowResult, result);
    }

    /**
     * 单条药品【3302】撤销；成功则 yb_match_flag=false。
     *
     * @param request 药品定义 ID 与契约 bus_no
     * @return 3302 撤销结果
     */
    public Catalog3302RevokeResultDto revokeMedicationCatalog3302(RevokeMedicationCatalog3302Request request) {
        // Gate：校验登录态与 yb_no
        Long operatorId = currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录");
        MedicationDefinitionDetailQueryModel med =
            medicationDefinitionQueryService.getMedicationCatalog(request.getMedicationDefId());
        if (StringUtils.isBlank(med.getYbNo())) {
            throw new BusinessException("YB_NO_REQUIRED", "无医保编码，无法撤销 3302 对照");
        }
        // Pre：组装 3302 行请求
        String listType = resolveListType(request.getListType(), ChsCatalogConstants.LIST_TYPE_WESTERN_DRUG);
        String fixmedinsId = resolveFixmedinsHilistId(med.getBusNo(), med.getId());
        RevokeCatalog3302RowRequest row = new RevokeCatalog3302RowRequest()
            .setProductTable(TableNameConstants.MED_MEDICATION_DEFINITION)
            .setProductId(med.getId())
            .setFixmedinsHilistId(fixmedinsId)
            .setListType(listType)
            .setMedListCodg(med.getYbNo().trim());
        // Cmd：调用 3302 撤销
        Catalog3302RevokeResultDto result = invokeRevoke3302(request.getContractBusNo(), List.of(row));
        Catalog3302RevokeRowResultDto rowResult = firstRevokeRowResult(result);
        // Asm：回写 yb_match_flag 并返回或抛出
        if (rowResult != null && rowResult.isSuccess()) {
            syncMedicationMatchFlag(med.getId(), med.getYbNo(), med.getChrgitmLv(), false, operatorId);
            return result;
        }
        throw revokeFailed(rowResult, result);
    }

    /**
     * 单条诊疗【3302】撤销；成功则 yb_match_flag=false。
     *
     * @param request 诊疗定义 ID 与契约 bus_no
     * @return 3302 撤销结果
     */
    public Catalog3302RevokeResultDto revokeActivityCatalog3302(RevokeActivityCatalog3302Request request) {
        // Gate：校验登录态与 yb_no
        Long operatorId = currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录");
        ActivityDefinitionBaseQueryModel activity = requireActivityBase(request.getActivityDefId());
        if (StringUtils.isBlank(activity.getYbNo())) {
            throw new BusinessException("YB_NO_REQUIRED", "无医保编码，无法撤销 3302 对照");
        }
        // Pre：组装 3302 行请求
        String listType = resolveListType(request.getListType(), ChsCatalogConstants.LIST_TYPE_MEDICAL_SERVICE);
        String fixmedinsId = resolveFixmedinsHilistId(activity.getBusNo(), activity.getId());
        RevokeCatalog3302RowRequest row = new RevokeCatalog3302RowRequest()
            .setProductTable(TableNameConstants.WKF_ACTIVITY_DEFINITION)
            .setProductId(activity.getId())
            .setFixmedinsHilistId(fixmedinsId)
            .setListType(listType)
            .setMedListCodg(activity.getYbNo().trim());
        // Cmd：调用 3302 撤销
        Catalog3302RevokeResultDto result = invokeRevoke3302(request.getContractBusNo(), List.of(row));
        Catalog3302RevokeRowResultDto rowResult = firstRevokeRowResult(result);
        // Asm：回写 yb_match_flag 并返回或抛出
        if (rowResult != null && rowResult.isSuccess()) {
            syncActivityMatchFlag(activity.getId(), activity.getYbNo(), activity.getChrgitmLv(), false, operatorId);
            return result;
        }
        throw revokeFailed(rowResult, result);
    }

    /**
     * 单条耗材【3302】撤销；成功则 yb_match_flag=false。
     *
     * @param request 耗材定义 ID 与契约 bus_no
     * @return 3302 撤销结果
     */
    public Catalog3302RevokeResultDto revokeDeviceCatalog3302(RevokeDeviceCatalog3302Request request) {
        // Gate：校验登录态与 yb_no
        Long operatorId = currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录");
        DeviceDefinition device = requireDeviceDefinition(request.getDeviceDefId());
        if (StringUtils.isBlank(device.getYbNo())) {
            throw new BusinessException("YB_NO_REQUIRED", "无医保编码，无法撤销 3302 对照");
        }
        // Pre：组装 3302 行请求
        String listType = resolveListType(request.getListType(), ChsCatalogConstants.LIST_TYPE_MEDICAL_CONSUMABLE);
        String fixmedinsId = resolveFixmedinsHilistId(device.getBusNo(), device.getId());
        RevokeCatalog3302RowRequest row = new RevokeCatalog3302RowRequest()
            .setProductTable(TableNameConstants.ADM_DEVICE_DEFINITION)
            .setProductId(device.getId())
            .setFixmedinsHilistId(fixmedinsId)
            .setListType(listType)
            .setMedListCodg(device.getYbNo().trim());
        // Cmd：调用 3302 撤销
        Catalog3302RevokeResultDto result = invokeRevoke3302(request.getContractBusNo(), List.of(row));
        Catalog3302RevokeRowResultDto rowResult = firstRevokeRowResult(result);
        // Asm：回写 yb_match_flag 并返回或抛出
        if (rowResult != null && rowResult.isSuccess()) {
            syncDeviceMatchFlag(device, false, operatorId);
            return result;
        }
        throw revokeFailed(rowResult, result);
    }

    private ActivityDefinitionBaseQueryModel requireActivityBase(Long activityDefId) {
        ActivityDefinitionDetailQueryModel detail = activityDefinitionQueryService.getServiceCatalog(activityDefId);
        if (detail.getBaseInfo() == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND, "诊疗项目定义不存在");
        }
        return detail.getBaseInfo();
    }

    private DeviceDefinition requireDeviceDefinition(Long deviceDefId) {
        DeviceDefinition device = deviceDefinitionQueryService.getDeviceDefinition(deviceDefId);
        if (device == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DEFINITION_NOT_FOUND, "耗材定义不存在");
        }
        return device;
    }

    private Catalog3301UploadResultDto invokeUpload3301(String contractBusNo, List<UploadCatalog3301RowRequest> rows) {
        UploadCatalog3301Request upload = new UploadCatalog3301Request()
            .setContractBusNo(contractBusNo)
            .setRows(rows);
        return catalogMappingOperations.uploadCatalog3301(upload);
    }

    private Catalog3302RevokeResultDto invokeRevoke3302(String contractBusNo, List<RevokeCatalog3302RowRequest> rows) {
        RevokeCatalog3302Request revoke = new RevokeCatalog3302Request()
            .setContractBusNo(contractBusNo)
            .setRows(rows);
        return catalogMappingOperations.revokeCatalog3302(revoke);
    }

    private static String resolveListType(String requested, String defaultListType) {
        if (StringUtils.isNotBlank(requested)) {
            return requested.trim();
        }
        return defaultListType;
    }

    private static String resolveFixmedinsHilistId(String busNo, Long id) {
        if (StringUtils.isNotBlank(busNo)) {
            return busNo.trim();
        }
        return String.valueOf(id);
    }

    private static Catalog3301UploadRowResultDto firstRowResult(Catalog3301UploadResultDto result) {
        if (result.getRows() == null || result.getRows().isEmpty()) {
            return null;
        }
        return result.getRows().get(0);
    }

    private static Catalog3302RevokeRowResultDto firstRevokeRowResult(Catalog3302RevokeResultDto result) {
        if (result.getRows() == null || result.getRows().isEmpty()) {
            return null;
        }
        return result.getRows().get(0);
    }

    private static BusinessException uploadFailed(
            Catalog3301UploadRowResultDto rowResult,
            Catalog3301UploadResultDto result) {
        String err;
        if (rowResult != null && StringUtils.isNotBlank(rowResult.getErrorMsg())) {
            err = rowResult.getErrorMsg();
        } else {
            err = result.getSummary();
        }
        return new BusinessException("CATALOG_3301_FAILED", "3301 上传失败：" + err);
    }

    private static BusinessException revokeFailed(
            Catalog3302RevokeRowResultDto rowResult,
            Catalog3302RevokeResultDto result) {
        String err;
        if (rowResult != null && StringUtils.isNotBlank(rowResult.getErrorMsg())) {
            err = rowResult.getErrorMsg();
        } else {
            err = result.getSummary();
        }
        return new BusinessException("CATALOG_3302_FAILED", "3302 撤销失败：" + err);
    }

    private void syncMedicationMatchFlag(
            Long medId,
            String ybNo,
            Integer chrgitmLv,
            boolean matched,
            Long operatorId) {
        SyncInsuranceCatalogMappingCommand command = new SyncInsuranceCatalogMappingCommand();
        command.setOperatorId(operatorId);
        SyncInsuranceCatalogMappingCommand.SyncInsuranceCatalogMappingItemCommand item =
            new SyncInsuranceCatalogMappingCommand.SyncInsuranceCatalogMappingItemCommand();
        item.setId(medId);
        item.setYbNo(ybNo);
        item.setYbMatchFlag(matched);
        item.setChrgitmLv(chrgitmLv);
        item.setSyncTime(LocalDateTime.now());
        command.setItems(List.of(item));
        medicationDefinitionDomainService.syncInsuranceCatalogMapping(command);
    }

    private void syncActivityMatchFlag(
            Long activityId,
            String ybNo,
            Integer chrgitmLv,
            boolean matched,
            Long operatorId) {
        SyncActivityYbMappingCommand command = new SyncActivityYbMappingCommand();
        command.setOperatorId(operatorId);
        SyncActivityYbMappingCommand.SyncActivityYbMappingItemCommand item =
            new SyncActivityYbMappingCommand.SyncActivityYbMappingItemCommand();
        item.setActivityId(activityId);
        item.setYbNo(ybNo);
        item.setYbMatchFlag(matched);
        item.setChrgitmLv(chrgitmLv);
        command.setItems(List.of(item));
        activityDefinitionDomainService.syncActivityYbMapping(command);
    }

    private void syncDeviceMatchFlag(DeviceDefinition device, boolean matched, Long operatorId) {
        SyncDeviceDefinitionYbMappingCommand command = new SyncDeviceDefinitionYbMappingCommand();
        command.setOperatorId(operatorId);
        SyncDeviceDefinitionYbMappingCommand.Item item = new SyncDeviceDefinitionYbMappingCommand.Item();
        item.setId(device.getId());
        item.setYbFlag(device.getYbFlag());
        item.setYbNo(device.getYbNo());
        item.setYbOrgNo(device.getYbOrgNo());
        item.setYbMatchFlag(matched);
        item.setChrgitmLv(device.getChrgitmLv());
        command.setItems(List.of(item));
        deviceDefinitionDomainService.syncDeviceDefinitionYbMapping(command);
    }
}
