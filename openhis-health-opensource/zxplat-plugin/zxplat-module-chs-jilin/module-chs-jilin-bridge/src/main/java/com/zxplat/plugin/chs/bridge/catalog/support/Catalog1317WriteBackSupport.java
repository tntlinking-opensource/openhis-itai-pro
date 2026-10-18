/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.zxplat.plugin.chs.domain.catalog.command.UpsertInstitutionCatalogMatchCommand;
import com.zxplat.plugin.chs.domain.share.enums.ListType;
import com.zxplat.plugin.chs.domain.share.enums.ValiFlag;
import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.zxplat.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 1317 正向回写：按 medins_list_codg=bus_no 命中院内品项后仅写 yb_match_flag。
 * <p>不回写 med_list_codg→yb_no（耗材平台短码会覆盖院内完整码）；未命中计 skipped，不清院内已对码。</p>
 */
@Component
@RequiredArgsConstructor
public class Catalog1317WriteBackSupport {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;
    private final CatalogMappingHealthAssemblySupport healthAssemblySupport;

    /**
     * 对一批 upsert 命令尝试正向回写院内对码标志。
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
            // med_list_codg 仅作平台有效对照门禁，不写入院内 yb_no
            if (isEmpty(row.getMedinsListCodg()) || isEmpty(row.getMedListCodg())) {
                skipped++;
                continue;
            }
            String busNo = row.getMedinsListCodg().trim();
            String listType = trimOrEmpty(row.getListType());
            boolean ok = writeOne(listType, busNo, operatorId);
            if (ok) {
                written++;
            } else {
                skipped++;
            }
        }
        return new int[]{written, skipped};
    }

    /**
     * 按 list_type 路由到药品/诊疗/耗材并回写对码标志。
     *
     * @param listType   目录类别
     * @param busNo      院内编码
     * @param operatorId 操作人
     * @return 是否命中并回写成功
     */
    private boolean writeOne(String listType, String busNo, Long operatorId) {
        if (ListType.MEDICAL_SERVICE.getCode().equals(listType)) {
            return writeActivity(busNo, operatorId);
        }
        if (ListType.MEDICAL_CONSUMABLE.getCode().equals(listType)) {
            return writeDevice(busNo, operatorId);
        }
        // 101/102 及其他默认按药品处理
        return writeMedication(busNo, operatorId);
    }

    /**
     * 按 bus_no 回写药品对码标志（保留院内 yb_no）。
     *
     * @param busNo      院内编码
     * @param operatorId 操作人
     * @return 是否命中
     */
    private boolean writeMedication(String busNo, Long operatorId) {
        List<MedicationDefinition> list = medicationDefinitionQueryService.findByBusNos(List.of(busNo));
        if (list == null || list.isEmpty()) {
            return false;
        }
        MedicationDefinition med = list.get(0);
        healthAssemblySupport.syncMedicationMatchFlag(
            med.getId(), med.getYbNo(), med.getChrgitmLv(), true, operatorId);
        return true;
    }

    /**
     * 按 bus_no 回写诊疗对码标志（保留院内 yb_no）。
     *
     * @param busNo      院内编码
     * @param operatorId 操作人
     * @return 是否命中
     */
    private boolean writeActivity(String busNo, Long operatorId) {
        List<ActivityDefinition> list = activityDefinitionQueryService.findByBusNos(List.of(busNo));
        if (list == null || list.isEmpty()) {
            return false;
        }
        ActivityDefinition activity = list.get(0);
        healthAssemblySupport.syncActivityMatchFlag(
            activity.getId(), activity.getYbNo(), activity.getChrgitmLv(), true, operatorId);
        return true;
    }

    /**
     * 按 bus_no 回写耗材对码标志（保留院内完整 yb_no）。
     *
     * @param busNo      院内编码
     * @param operatorId 操作人
     * @return 是否命中
     */
    private boolean writeDevice(String busNo, Long operatorId) {
        List<DeviceDefinition> list = deviceDefinitionQueryService.findByBusNos(List.of(busNo));
        if (list == null || list.isEmpty()) {
            return false;
        }
        DeviceDefinition device = list.get(0);
        // 1317：只标已对码，不把平台 med_list_codg（耗材可能是 15 位短码）写入 yb_no
        healthAssemblySupport.syncDeviceYbMapping(
            device.getId(),
            device.getYbFlag(),
            device.getYbNo(),
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
