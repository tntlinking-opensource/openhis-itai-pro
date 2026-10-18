/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.report;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.workflow.devicedispense.entity.DeviceDispense;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 收费明细「活动操作人」解析：诊疗跟 Procedure 主执行人，耗材跟 DeviceDispense 发放/配药人。
 *
 * <p>不再读 {@code adm_charge_item} 上已移除的操作人戳列；供护士站费用明细、门/住院费用明细报表共用。</p>
 */
@Component
@RequiredArgsConstructor
public class ChargeItemActivityOperatorResolver {

    private final ProcedureQueryService procedureQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /**
     * 批量解析收费行活动操作人（chargeItemId → practitionerId）。
     *
     * <p>优先 {@code procedure_id} → Procedure 主执行人；否则耗材发放表锚点 →
     * {@code performer_id} 非空取其，否则 {@code preparer_id}。</p>
     *
     * @param chargeItems 收费明细集合；可空
     * @return chargeItemId → 操作人；无锚点或未命中则不入表
     */
    public Map<Long, Long> mapOperatorPractitionerIdByChargeItemId(Collection<ChargeItem> chargeItems) {
        LinkedHashMap<Long, Long> result = new LinkedHashMap<>();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return result;
        }
        // Pre：收集执行记录与耗材发放主键
        LinkedHashSet<Long> procedureIds = new LinkedHashSet<>();
        LinkedHashSet<Long> deviceDispenseIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            if (chargeItem.getProcedureId() != null) {
                procedureIds.add(chargeItem.getProcedureId());
            }
            if (this.isDeviceDispenseAnchor(chargeItem) && chargeItem.getDispenseId() != null) {
                deviceDispenseIds.add(chargeItem.getDispenseId());
            }
        }
        Map<Long, Long> practitionerByProcedureId = Map.of();
        if (!procedureIds.isEmpty()) {
            practitionerByProcedureId =
                this.procedureQueryService.mapPrimaryPractitionerIdByProcedureIds(procedureIds);
        }
        Map<Long, Long> practitionerByDispenseId = this.mapDeviceDispenseOperatorByIds(deviceDispenseIds);
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            // Branch：诊疗执行记录优先
            if (chargeItem.getProcedureId() != null) {
                Long fromProcedure = practitionerByProcedureId.get(chargeItem.getProcedureId());
                if (fromProcedure != null) {
                    result.put(chargeItem.getId(), fromProcedure);
                    continue;
                }
            }
            // Branch：耗材发放人/配药人
            if (this.isDeviceDispenseAnchor(chargeItem) && chargeItem.getDispenseId() != null) {
                Long fromDispense = practitionerByDispenseId.get(chargeItem.getDispenseId());
                if (fromDispense != null) {
                    result.put(chargeItem.getId(), fromDispense);
                }
            }
        }
        return result;
    }

    /**
     * 从收费候选行收集操作人主键集合（供批量查 Practitioner 姓名）。
     *
     * @param chargeItems 收费明细
     * @return 去重操作人 id；无则空集
     */
    public Set<Long> collectOperatorPractitionerIds(Collection<ChargeItem> chargeItems) {
        return new LinkedHashSet<>(this.mapOperatorPractitionerIdByChargeItemId(chargeItems).values());
    }

    /**
     * 判定收费行发放锚点是否为耗材发放表。
     *
     * @param chargeItem 收费行
     * @return true 表示可按 DeviceDispense 解析操作人
     */
    private boolean isDeviceDispenseAnchor(ChargeItem chargeItem) {
        if (chargeItem == null || StringUtils.isEmpty(chargeItem.getDispenseTable())) {
            return false;
        }
        return TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getDispenseTable().trim());
    }

    /**
     * 批量解析耗材发放操作人：已发优先 {@code performer_id}，否则 {@code preparer_id}。
     *
     * @param dispenseIds 发放主键
     * @return dispenseId → practitionerId
     */
    private Map<Long, Long> mapDeviceDispenseOperatorByIds(Collection<Long> dispenseIds) {
        HashMap<Long, Long> result = new HashMap<>();
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return result;
        }
        List<DeviceDispense> rows = this.deviceDispenseQueryService.listDeviceDispensesByIds(dispenseIds);
        for (DeviceDispense row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            Long operatorId = row.getPerformerId();
            if (operatorId == null) {
                operatorId = row.getPreparerId();
            }
            if (operatorId != null) {
                result.put(row.getId(), operatorId);
            }
        }
        return result;
    }
}
