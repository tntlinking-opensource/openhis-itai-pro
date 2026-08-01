/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.support;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.domain.catalog.command.UpsertInstitutionCatalogMatchCommand;
import com.openhis.chs.domain.share.enums.ListType;
import com.openhis.chs.domain.share.enums.ValiFlag;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 1317 正向回写：按 medins_list_codg=bus_no 命中院内品项后写 yb_no / yb_match_flag。
 * <p>仅有效行回写；未命中计 skipped，不清院内已对码。</p>
 */
@Component
@RequiredArgsConstructor
public class Catalog1317WriteBackSupport {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    private final CatalogMappingHealthAssemblySupport healthAssemblySupport;

    /**
     * 对一批 upsert 命令尝试正向回写院内对码。
     *
     * @param rows       已映射命令（可含无效行）
     * @param operatorId 操作人
     * @return int[2]：{writtenBack, skipped}
     */
    public int[] writeBack(List<UpsertInstitutionCatalogMatchCommand> rows, Long operatorId) {
        int written = 0;
        int skipped = 0;
        if (rows == null || rows.isEmpty()) {
            return new int[]{0, 0};
        }
        for (UpsertInstitutionCatalogMatchCommand row : rows) {
            if (row == null) {
                skipped++;
                continue;
            }
            // 非有效行只落库不回写，避免把停用对照刷进院内
            if (!ValiFlag.VALID.getCode().equals(trimOrEmpty(row.getValiFlag()))) {
                skipped++;
                continue;
            }
            if (isEmpty(row.getMedinsListCodg()) || isEmpty(row.getMedListCodg())) {
                skipped++;
                continue;
            }
            String busNo = row.getMedinsListCodg().trim();
            String ybNo = row.getMedListCodg().trim();
            String listType = trimOrEmpty(row.getListType());
            boolean ok = writeOne(listType, busNo, ybNo, operatorId);
            if (ok) {
                written++;
            } else {
                skipped++;
            }
        }
        return new int[]{written, skipped};
    }

    /**
     * 按 list_type 路由到药品/诊疗/耗材并回写。
     *
     * @param listType   目录类别
     * @param busNo      院内编码
     * @param ybNo       医保编码
     * @param operatorId 操作人
     * @return 是否命中并回写成功
     */
    private boolean writeOne(String listType, String busNo, String ybNo, Long operatorId) {
        if (ListType.MEDICAL_SERVICE.getCode().equals(listType)) {
            return writeActivity(busNo, ybNo, operatorId);
        }
        if (ListType.MEDICAL_CONSUMABLE.getCode().equals(listType)) {
            return writeDevice(busNo, ybNo, operatorId);
        }
        // 101/102 及其他默认按药品处理
        return writeMedication(busNo, ybNo, operatorId);
    }

    /**
     * 按 bus_no 回写药品对码。
     *
     * @param busNo      院内编码
     * @param ybNo       医保编码
     * @param operatorId 操作人
     * @return 是否命中
     */
    private boolean writeMedication(String busNo, String ybNo, Long operatorId) {
        List<MedicationDefinition> list = medicationDefinitionQueryService.findByBusNos(List.of(busNo));
        if (list == null || list.isEmpty()) {
            return false;
        }
        MedicationDefinition med = list.get(0);
        healthAssemblySupport.syncMedicationMatchFlag(med.getId(), ybNo, med.getChrgitmLv(), true, operatorId);
        return true;
    }

    /**
     * 按 bus_no 回写诊疗对码。
     *
     * @param busNo      院内编码
     * @param ybNo       医保编码
     * @param operatorId 操作人
     * @return 是否命中
     */
    private boolean writeActivity(String busNo, String ybNo, Long operatorId) {
        List<ActivityDefinition> list = activityDefinitionQueryService.findByBusNos(List.of(busNo));
        if (list == null || list.isEmpty()) {
            return false;
        }
        ActivityDefinition activity = list.get(0);
        healthAssemblySupport.syncActivityMatchFlag(
            activity.getId(), ybNo, activity.getChrgitmLv(), true, operatorId);
        return true;
    }

    /**
     * 按 bus_no 回写耗材对码（含 yb_no）。
     *
     * @param busNo      院内编码
     * @param ybNo       医保编码
     * @param operatorId 操作人
     * @return 是否命中
     */
    private boolean writeDevice(String busNo, String ybNo, Long operatorId) {
        List<DeviceDefinition> list = deviceDefinitionQueryService.findByBusNos(List.of(busNo));
        if (list == null || list.isEmpty()) {
            return false;
        }
        DeviceDefinition device = list.get(0);
        // 1317 medins_list_codg=bus_no；回写 yb_no（截前 15）与对码标志
        healthAssemblySupport.syncDeviceYbMapping(
            device.getId(),
            device.getYbFlag(),
            ybNo,
            true,
            device.getChrgitmLv(),
            operatorId);
        return true;
    }

    /**
     * 空白安全 trim。
     *
     * @param value 原值
     * @return trim 后或空串
     */
    private static String trimOrEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
