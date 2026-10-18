/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.support;

import com.zxplat.application.finance.priceadjustment.dto.FreshUnsignedDraftTipDto;
import com.zxplat.application.shared.advice.UnsignedOrderDraftAbandonSupport;
import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.zxplat.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.medication.medicationrequest.queries.model.StaleUnsignedMedicationRequestDraftQueryModel;
import com.zxplat.domain.share.constants.CustomConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.workflow.devicerequest.queries.model.StaleUnsignedDeviceRequestDraftQueryModel;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 调价物流单未签发草稿策略：超过新鲜窗口静默废弃；窗口内组装开立医生/患者/项目提示。
 */
@Component
@RequiredArgsConstructor
public class PriceAdjustmentUnsignedDraftPolicySupport {

    private final UnsignedOrderDraftAbandonSupport unsignedOrderDraftAbandonSupport;
    private final PractitionerQueryService practitionerQueryService;
    private final PatientQueryService patientQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /**
     * 对调价单号上物流项目：自动废弃超过新鲜窗口的未签发草稿。
     *
     * @param busNo      调价单号
     * @param operatorId 操作人
     * @return 废弃条数
     */
    public int abandonStaleByBusNo(String busNo, Long operatorId) {
        return this.unsignedOrderDraftAbandonSupport.abandonStaleUnsignedDraftsByPriceAdjustmentBusNo(
            busNo, operatorId);
    }

    /**
     * 对项目行：自动废弃超过新鲜窗口的未签发草稿。
     *
     * @param lines      调价项目行
     * @param operatorId 操作人
     * @return 废弃条数
     */
    public int abandonStaleForLines(List<PriceAdjustmentLineQueryModel> lines, Long operatorId) {
        return this.unsignedOrderDraftAbandonSupport.abandonStaleUnsignedDraftsForLines(lines, operatorId);
    }

    /**
     * 按调价单号装载新鲜窗口内未签发草稿提示（含医生/患者/项目名）。
     *
     * @param busNo 调价单号
     * @return 提示行；无则空列表
     */
    public List<FreshUnsignedDraftTipDto> listFreshTipsByBusNo(String busNo) {
        List<StaleUnsignedMedicationRequestDraftQueryModel> medDrafts =
            this.unsignedOrderDraftAbandonSupport.listFreshMedicationDraftsByBusNo(busNo);
        List<StaleUnsignedDeviceRequestDraftQueryModel> deviceDrafts =
            this.unsignedOrderDraftAbandonSupport.listFreshDeviceDraftsByBusNo(busNo);
        return this.toTips(medDrafts, deviceDrafts);
    }

    /**
     * 按项目行装载新鲜窗口内未签发草稿提示。
     *
     * @param lines 调价项目行
     * @return 提示行；无则空列表
     */
    public List<FreshUnsignedDraftTipDto> listFreshTipsForLines(List<PriceAdjustmentLineQueryModel> lines) {
        List<StaleUnsignedMedicationRequestDraftQueryModel> medDrafts =
            this.unsignedOrderDraftAbandonSupport.listFreshMedicationDraftsForLines(lines);
        List<StaleUnsignedDeviceRequestDraftQueryModel> deviceDrafts =
            this.unsignedOrderDraftAbandonSupport.listFreshDeviceDraftsForLines(lines);
        return this.toTips(medDrafts, deviceDrafts);
    }

    /**
     * 拼装用户可读提示文案（写明谁给谁开了什么药/耗）。
     *
     * @param tips 提示行
     * @return 文案；空列表返回默认句
     */
    public String buildFreshDraftBlockMessage(List<FreshUnsignedDraftTipDto> tips) {
        int hours = CustomConstants.PriceAdjustment.UNSIGNED_DRAFT_FRESH_HOURS;
        if (tips == null || tips.isEmpty()) {
            return "存在" + hours + "小时内未签发医嘱草稿，请联系开立医生自行删除后再调价";
        }
        StringBuilder builder = new StringBuilder("存在")
            .append(hours)
            .append("小时内未签发医嘱草稿，请联系开立医生自行删除：");
        int index = 0;
        for (FreshUnsignedDraftTipDto tip : tips) {
            if (tip == null) {
                continue;
            }
            if (index > 0) {
                builder.append("；");
            }
            String doctor = tip.getDoctorName();
            if (StringUtils.isEmpty(doctor)) {
                doctor = "未知医生";
            }
            String patient = tip.getPatientName();
            if (StringUtils.isEmpty(patient)) {
                patient = "未知患者";
            }
            String itemName = tip.getItemName();
            if (StringUtils.isEmpty(itemName)) {
                itemName = "未知项目";
            }
            // 例：张三大夫给患者李四开的【阿莫西林胶囊】
            builder.append(doctor)
                .append("大夫给患者")
                .append(patient)
                .append("开的【")
                .append(itemName)
                .append("】");
            index++;
            // 业务分支：提示过长时截断，避免前端弹窗爆长
            if (index >= 8) {
                builder.append("…");
                break;
            }
        }
        return builder.toString();
    }

    /**
     * 药/耗草稿读模型转提示 DTO，并批量 enrich 医生/患者/项目名称。
     *
     * @param medDrafts    药品草稿
     * @param deviceDrafts 耗材草稿
     * @return 提示列表
     */
    private List<FreshUnsignedDraftTipDto> toTips(
        List<StaleUnsignedMedicationRequestDraftQueryModel> medDrafts,
        List<StaleUnsignedDeviceRequestDraftQueryModel> deviceDrafts
    ) {
        List<FreshUnsignedDraftTipDto> tips = new ArrayList<>();
        Set<Long> doctorIds = new HashSet<>();
        Set<Long> patientIds = new HashSet<>();
        Set<Long> medicationIds = new HashSet<>();
        Set<Long> deviceDefIds = new HashSet<>();
        if (medDrafts != null) {
            for (StaleUnsignedMedicationRequestDraftQueryModel draft : medDrafts) {
                if (draft == null || draft.getId() == null) {
                    continue;
                }
                FreshUnsignedDraftTipDto tip = new FreshUnsignedDraftTipDto();
                tip.setRequestId(draft.getId());
                tip.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
                tip.setItemId(draft.getMedicationId());
                tip.setDoctorId(draft.getRequesterId());
                tip.setPatientId(draft.getPatientId());
                tip.setOpenedAt(draft.getOpenedAt());
                tips.add(tip);
                if (draft.getRequesterId() != null) {
                    doctorIds.add(draft.getRequesterId());
                }
                if (draft.getPatientId() != null) {
                    patientIds.add(draft.getPatientId());
                }
                if (draft.getMedicationId() != null) {
                    medicationIds.add(draft.getMedicationId());
                }
            }
        }
        if (deviceDrafts != null) {
            for (StaleUnsignedDeviceRequestDraftQueryModel draft : deviceDrafts) {
                if (draft == null || draft.getId() == null) {
                    continue;
                }
                FreshUnsignedDraftTipDto tip = new FreshUnsignedDraftTipDto();
                tip.setRequestId(draft.getId());
                tip.setItemTable(TableNameConstants.ADM_DEVICE_DEFINITION);
                tip.setItemId(draft.getDeviceDefId());
                tip.setDoctorId(draft.getRequesterId());
                tip.setPatientId(draft.getPatientId());
                tip.setOpenedAt(draft.getOpenedAt());
                tips.add(tip);
                if (draft.getRequesterId() != null) {
                    doctorIds.add(draft.getRequesterId());
                }
                if (draft.getPatientId() != null) {
                    patientIds.add(draft.getPatientId());
                }
                if (draft.getDeviceDefId() != null) {
                    deviceDefIds.add(draft.getDeviceDefId());
                }
            }
        }
        Map<Long, String> doctorNames = this.practitionerQueryService.mapNamesByIds(doctorIds);
        Map<Long, String> patientNames = this.patientQueryService.findPatientNamesByIds(patientIds);
        Map<Long, String> medicationNames = this.mapMedicationNames(medicationIds);
        Map<Long, String> deviceNames = this.mapDeviceNames(deviceDefIds);
        for (FreshUnsignedDraftTipDto tip : tips) {
            if (tip.getDoctorId() != null && doctorNames != null) {
                tip.setDoctorName(doctorNames.get(tip.getDoctorId()));
            }
            // 业务分支：mapNames 未命中时回落单条读，避免空白提示
            if (StringUtils.isEmpty(tip.getDoctorName()) && tip.getDoctorId() != null) {
                Practitioner practitioner = this.practitionerQueryService.findById(tip.getDoctorId());
                if (practitioner != null) {
                    tip.setDoctorName(practitioner.getName());
                }
            }
            if (tip.getPatientId() != null && patientNames != null) {
                tip.setPatientName(patientNames.get(tip.getPatientId()));
            }
            // 业务分支：按项目表回填药品或耗材名称
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(tip.getItemTable())
                && tip.getItemId() != null) {
                tip.setItemName(medicationNames.get(tip.getItemId()));
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(tip.getItemTable())
                && tip.getItemId() != null) {
                tip.setItemName(deviceNames.get(tip.getItemId()));
            }
        }
        return tips;
    }

    /**
     * 批量解析药品定义名称。
     *
     * @param medicationIds 药品定义主键集合
     * @return id → name；无命中为空 Map
     */
    private Map<Long, String> mapMedicationNames(Set<Long> medicationIds) {
        Map<Long, String> names = new HashMap<>();
        if (medicationIds == null || medicationIds.isEmpty()) {
            return names;
        }
        List<MedicationDefinition> definitions =
            this.medicationDefinitionQueryService.findAllByIds(medicationIds);
        if (definitions == null) {
            return names;
        }
        for (MedicationDefinition definition : definitions) {
            if (definition == null || definition.getId() == null) {
                continue;
            }
            names.put(definition.getId(), definition.getName());
        }
        return names;
    }

    /**
     * 批量解析耗材定义名称。
     *
     * @param deviceDefIds 耗材定义主键集合
     * @return id → name；无命中为空 Map
     */
    private Map<Long, String> mapDeviceNames(Set<Long> deviceDefIds) {
        Map<Long, String> names = new HashMap<>();
        if (deviceDefIds == null || deviceDefIds.isEmpty()) {
            return names;
        }
        List<DeviceDefinition> definitions =
            this.deviceDefinitionQueryService.getAllDeviceDefinitions(deviceDefIds);
        if (definitions == null) {
            return names;
        }
        for (DeviceDefinition definition : definitions) {
            if (definition == null || definition.getId() == null) {
                continue;
            }
            names.put(definition.getId(), definition.getName());
        }
        return names;
    }
}
