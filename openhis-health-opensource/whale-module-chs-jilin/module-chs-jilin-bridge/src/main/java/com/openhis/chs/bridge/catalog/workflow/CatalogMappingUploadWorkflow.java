/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.workflow;

import static com.openhis.chs.bridge.catalog.support.CatalogMappingRowSupport.firstRevokeRow;
import static com.openhis.chs.bridge.catalog.support.CatalogMappingRowSupport.firstUploadRow;
import static com.openhis.chs.bridge.catalog.support.CatalogMappingRowSupport.revokeFailed;
import static com.openhis.chs.bridge.catalog.support.CatalogMappingRowSupport.uploadFailed;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.bridge.catalog.invoker.Cat3301Invoker;
import com.openhis.chs.bridge.catalog.invoker.Cat3302Invoker;
import com.openhis.chs.bridge.catalog.support.CatalogMappingHealthAssemblySupport;
import com.openhis.chs.bridge.catalog.support.CatalogPlatformSupport;
import com.openhis.chs.domain.catalog.access.CatalogMappingUploadAccess;
import com.openhis.chs.domain.catalog.command.RevokeActivityCatalog3302Command;
import com.openhis.chs.domain.catalog.command.RevokeCatalog3302Command;
import com.openhis.chs.domain.catalog.command.RevokeCatalog3302RowCommand;
import com.openhis.chs.domain.catalog.command.RevokeDeviceCatalog3302Command;
import com.openhis.chs.domain.catalog.command.RevokeFromInstitutionMatchCommand;
import com.openhis.chs.domain.catalog.command.RevokeMedicationCatalog3302Command;
import com.openhis.chs.domain.catalog.command.UploadActivityCatalog3301Command;
import com.openhis.chs.domain.catalog.command.UploadCatalog3301Command;
import com.openhis.chs.domain.catalog.command.UploadCatalog3301RowCommand;
import com.openhis.chs.domain.catalog.command.UploadDeviceCatalog3301Command;
import com.openhis.chs.domain.catalog.command.UploadMedicationCatalog3301Command;
import com.openhis.chs.domain.catalog.entity.YbInstitutionCatalogMatch;
import com.openhis.chs.domain.catalog.repository.HospitalCatalogPresenceLookup;
import com.openhis.chs.domain.catalog.result.Catalog3301UploadResult;
import com.openhis.chs.domain.catalog.result.Catalog3301UploadRowResult;
import com.openhis.chs.domain.catalog.result.Catalog3302RevokeResult;
import com.openhis.chs.domain.catalog.result.Catalog3302RevokeRowResult;
import com.openhis.chs.domain.catalog.result.HospitalCatalogPresence;
import com.openhis.chs.domain.catalog.service.YbInstitutionCatalogMatchQueryService;
import com.openhis.chs.domain.share.enums.ListType;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionDetailQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionBaseQueryModel;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 3301/3302 目录对照 bridge 编排实现（P13）。
 *
 * <p><b>职责：</b>从 health 主数据组装上行行，调用 Cat3301/Cat3302 invoker 并按结果回写 yb_match_flag。</p>
 * <p><b>实现端口：</b>{@link com.openhis.chs.domain.catalog.access.CatalogMappingUploadAccess}</p>
 * <p><b>调用方：</b>{@code InsuranceCatalogMappingAppService}</p>
 * <p><b>架构边界：</b>bridge 层组装 gateway 出站上下文；application 只依赖 domain 端口。</p>
 * <p><b>迁移阶段：</b>P13</p>
 */
@Component
@RequiredArgsConstructor
public class CatalogMappingUploadWorkflow implements CatalogMappingUploadAccess {

    private final Cat3301Invoker cat3301Invoker;
    private final Cat3302Invoker cat3302Invoker;
    private final CatalogMappingHealthAssemblySupport healthAssembly;
    private final CatalogPlatformSupport catalogPlatformSupport;
    private final YbInstitutionCatalogMatchQueryService institutionCatalogMatchQueryService;
    private final HospitalCatalogPresenceLookup hospitalCatalogPresenceLookup;

    @Override
    public Catalog3301UploadResult uploadMedication(UploadMedicationCatalog3301Command command) {
        Long operatorId = catalogPlatformSupport.requireOperatorId("请先登录");
        MedicationDefinitionDetailQueryModel med = healthAssembly.requireMedication(command.getMedicationDefId());
        if (isEmpty(med.getYbNo())) {
            throw new BusinessException("YB_NO_REQUIRED", "请先选择国家医保目录编码（yb_no）后再上传 3301");
        }
        UploadCatalog3301RowCommand row = healthAssembly.toMedicationUploadRow(med, command.getListType());
        Catalog3301UploadResult result = invokeUpload3301(command.getContractBusNo(), List.of(row));
        return finalizeUpload3301(med.getId(), med.getYbNo(), med.getChrgitmLv(), operatorId, result,
            (id, ybNo, lv, matched, op) -> healthAssembly.syncMedicationMatchFlag(id, ybNo, lv, matched, op));
    }

    @Override
    public Catalog3301UploadResult uploadActivity(UploadActivityCatalog3301Command command) {
        Long operatorId = catalogPlatformSupport.requireOperatorId("请先登录");
        ActivityDefinitionBaseQueryModel activity = healthAssembly.requireActivityBase(command.getActivityDefId());
        if (isEmpty(activity.getYbNo())) {
            throw new BusinessException("YB_NO_REQUIRED", "请先选择国家医保目录编码（yb_no）后再上传 3301");
        }
        UploadCatalog3301RowCommand row = healthAssembly.toActivityUploadRow(activity, command.getListType());
        Catalog3301UploadResult result = invokeUpload3301(command.getContractBusNo(), List.of(row));
        return finalizeUpload3301(activity.getId(), activity.getYbNo(), activity.getChrgitmLv(), operatorId, result,
            (id, ybNo, lv, matched, op) -> healthAssembly.syncActivityMatchFlag(id, ybNo, lv, matched, op));
    }

    @Override
    public Catalog3301UploadResult uploadDevice(UploadDeviceCatalog3301Command command) {
        Long operatorId = catalogPlatformSupport.requireOperatorId("请先登录");
        DeviceDefinition device = healthAssembly.requireDevice(command.getDeviceDefId());
        if (isEmpty(device.getYbNo())) {
            throw new BusinessException("YB_NO_REQUIRED", "请先选择国家医保目录编码（yb_no）后再上传 3301");
        }
        UploadCatalog3301RowCommand row = healthAssembly.toDeviceUploadRow(device, command.getListType());
        Catalog3301UploadResult result = invokeUpload3301(command.getContractBusNo(), List.of(row));
        Catalog3301UploadRowResult rowResult = firstUploadRow(result);
        if (rowResult != null && rowResult.isSuccess()) {
            healthAssembly.syncDeviceMatchFlag(device, true, operatorId);
            return result;
        }
        healthAssembly.syncDeviceMatchFlag(device, false, operatorId);
        throw uploadFailed(rowResult, result);
    }

    @Override
    public Catalog3302RevokeResult revokeMedication(RevokeMedicationCatalog3302Command command) {
        Long operatorId = catalogPlatformSupport.requireOperatorId("请先登录");
        MedicationDefinitionDetailQueryModel med = healthAssembly.requireMedication(command.getMedicationDefId());
        if (isEmpty(med.getYbNo())) {
            throw new BusinessException("YB_NO_REQUIRED", "无医保编码，无法撤销 3302 对照");
        }
        RevokeCatalog3302RowCommand row = healthAssembly.toMedicationRevokeRow(med, command.getListType());
        return finalizeRevoke3302(command.getContractBusNo(), List.of(row), operatorId, med.getId(),
            med.getYbNo(), med.getChrgitmLv(),
            (id, ybNo, lv, matched, op) -> healthAssembly.syncMedicationMatchFlag(id, ybNo, lv, matched, op));
    }

    @Override
    public Catalog3302RevokeResult revokeActivity(RevokeActivityCatalog3302Command command) {
        Long operatorId = catalogPlatformSupport.requireOperatorId("请先登录");
        ActivityDefinitionBaseQueryModel activity = healthAssembly.requireActivityBase(command.getActivityDefId());
        if (isEmpty(activity.getYbNo())) {
            throw new BusinessException("YB_NO_REQUIRED", "无医保编码，无法撤销 3302 对照");
        }
        RevokeCatalog3302RowCommand row = healthAssembly.toActivityRevokeRow(activity, command.getListType());
        return finalizeRevoke3302(command.getContractBusNo(), List.of(row), operatorId, activity.getId(),
            activity.getYbNo(), activity.getChrgitmLv(),
            (id, ybNo, lv, matched, op) -> healthAssembly.syncActivityMatchFlag(id, ybNo, lv, matched, op));
    }

    @Override
    public Catalog3302RevokeResult revokeDevice(RevokeDeviceCatalog3302Command command) {
        Long operatorId = catalogPlatformSupport.requireOperatorId("请先登录");
        DeviceDefinition device = healthAssembly.requireDevice(command.getDeviceDefId());
        if (isEmpty(device.getYbNo())) {
            throw new BusinessException("YB_NO_REQUIRED", "无医保编码，无法撤销 3302 对照");
        }
        RevokeCatalog3302RowCommand row = healthAssembly.toDeviceRevokeRow(device, command.getListType());
        Catalog3302RevokeResult result = invokeRevoke3302(command.getContractBusNo(), List.of(row));
        Catalog3302RevokeRowResult rowResult = firstRevokeRow(result);
        if (rowResult != null && rowResult.isSuccess()) {
            healthAssembly.syncDeviceMatchFlag(device, false, operatorId);
            return result;
        }
        throw revokeFailed(rowResult, result);
    }

    @Override
    public Catalog3302RevokeResult revokeFromInstitutionMatch(RevokeFromInstitutionMatchCommand command) {
        Long operatorId = catalogPlatformSupport.requireOperatorId("请先登录");
        if (command == null || command.getMatchId() == null) {
            throw new BusinessException("MATCH_ID_REQUIRED", "请指定平台已对照镜像行");
        }
        if (isEmpty(command.getContractBusNo())) {
            throw new BusinessException("CONTRACT_BUS_NO_REQUIRED", "请选择医保契约");
        }
        YbInstitutionCatalogMatch match = institutionCatalogMatchQueryService.findActiveById(command.getMatchId());
        if (match == null) {
            throw new BusinessException("MATCH_NOT_FOUND", "平台已对照镜像行不存在");
        }
        if (isEmpty(match.getMedinsListCodg()) || isEmpty(match.getMedListCodg()) || isEmpty(match.getListType())) {
            throw new BusinessException("MATCH_INCOMPLETE", "镜像缺少院内编码/医保编码/目录类别，无法撤销");
        }

        String listType = match.getListType().trim();
        String busNo = match.getMedinsListCodg().trim();
        String medListCodg = match.getMedListCodg().trim();
        HospitalCatalogPresence presence = hospitalCatalogPresenceLookup
            .resolve(listType, List.of(busNo))
            .get(HospitalCatalogPresenceLookup.key(listType, busNo));

        RevokeCatalog3302RowCommand row = new RevokeCatalog3302RowCommand()
            .setFixmedinsHilistId(busNo)
            .setListType(listType)
            .setMedListCodg(medListCodg);
        if (presence != null) {
            row.setProductTable(presence.getProductTable());
            row.setProductId(presence.getProductId());
        } else {
            row.setProductTable(HospitalCatalogPresenceLookup.productTableOf(listType));
        }

        Catalog3302RevokeResult result = invokeRevoke3302(command.getContractBusNo().trim(), List.of(row));
        Catalog3302RevokeRowResult rowResult = firstRevokeRow(result);
        if (rowResult != null && rowResult.isSuccess()) {
            if (presence != null && presence.getProductId() != null) {
                clearHospitalMatchFlag(listType, presence, operatorId);
            }
            institutionCatalogMatchQueryService.markInvalid(match);
            return result;
        }
        throw revokeFailed(rowResult, result);
    }

    private void clearHospitalMatchFlag(String listType, HospitalCatalogPresence presence, Long operatorId) {
        if (ListType.MEDICAL_SERVICE.getCode().equals(listType)) {
            healthAssembly.syncActivityMatchFlag(
                presence.getProductId(), presence.getYbNo(), null, false, operatorId);
            return;
        }
        if (ListType.MEDICAL_CONSUMABLE.getCode().equals(listType)) {
            DeviceDefinition device = healthAssembly.requireDevice(presence.getProductId());
            healthAssembly.syncDeviceMatchFlag(device, false, operatorId);
            return;
        }
        healthAssembly.syncMedicationMatchFlag(
            presence.getProductId(), presence.getYbNo(), null, false, operatorId);
    }

    private Catalog3301UploadResult finalizeUpload3301(
            Long productId,
            String ybNo,
            Integer chrgitmLv,
            Long operatorId,
            Catalog3301UploadResult result,
            MatchSync sync
    ) {
        Catalog3301UploadRowResult rowResult = firstUploadRow(result);
        if (rowResult != null && rowResult.isSuccess()) {
            sync.apply(productId, ybNo, chrgitmLv, true, operatorId);
            return result;
        }
        sync.apply(productId, ybNo, chrgitmLv, false, operatorId);
        throw uploadFailed(rowResult, result);
    }

    private Catalog3302RevokeResult finalizeRevoke3302(
            String contractBusNo,
            List<RevokeCatalog3302RowCommand> rows,
            Long operatorId,
            Long productId,
            String ybNo,
            Integer chrgitmLv,
            MatchSync sync
    ) {
        Catalog3302RevokeResult result = invokeRevoke3302(contractBusNo, rows);
        Catalog3302RevokeRowResult rowResult = firstRevokeRow(result);
        if (rowResult != null && rowResult.isSuccess()) {
            sync.apply(productId, ybNo, chrgitmLv, false, operatorId);
            return result;
        }
        throw revokeFailed(rowResult, result);
    }

    private Catalog3301UploadResult invokeUpload3301(String contractBusNo, List<UploadCatalog3301RowCommand> rows) {
        return cat3301Invoker.invoke(new UploadCatalog3301Command()
            .setContractBusNo(contractBusNo)
            .setRows(rows));
    }

    private Catalog3302RevokeResult invokeRevoke3302(String contractBusNo, List<RevokeCatalog3302RowCommand> rows) {
        return cat3302Invoker.invoke(new RevokeCatalog3302Command()
            .setContractBusNo(contractBusNo)
            .setRows(rows));
    }

    @FunctionalInterface
    private interface MatchSync {
        void apply(Long productId, String ybNo, Integer chrgitmLv, boolean matched, Long operatorId);
    }
}
