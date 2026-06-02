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

package com.openhis.health.application.emergency.patientmanagement.service;

import com.openhis.health.application.emergency.patientmanagement.dto.PatientDisplayFlagDto;
import com.openhis.health.application.emergency.patientmanagement.query.model.PatientManagementListItemQueryModel;
import com.openhis.health.domain.emergency.patientalert.entity.EmgPatientDisplayFlagDef;
import com.openhis.health.domain.emergency.patientalert.entity.PatientAlert;
import com.openhis.health.domain.emergency.patientalert.repository.EmgPatientDisplayFlagDefRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按配置表组装患者列表展示标识（§5.3-7）。
 */
@Service
@RequiredArgsConstructor
public class PatientDisplayFlagAssembler {

    private final EmgPatientDisplayFlagDefRepository flagDefRepository;

    public List<EmgPatientDisplayFlagDef> loadEnabledDefinitions() {
        return flagDefRepository.findAllByEnabledTrueAndIsDeletedFalseOrderBySortOrderAsc();
    }

    public List<PatientDisplayFlagDto> assemble(
        List<EmgPatientDisplayFlagDef> defs,
        PatientManagementListItemQueryModel item,
        boolean hasBlacklist) {
        if (defs == null || defs.isEmpty() || item == null) {
            return List.of();
        }
        List<PatientDisplayFlagDto> flags = new ArrayList<>();
        for (EmgPatientDisplayFlagDef def : defs) {
            if (!Boolean.TRUE.equals(def.getEnabled())) {
                continue;
            }
            String code = def.getFlagCode();
            if (code == null) {
                continue;
            }
            if (!matches(code, item, hasBlacklist)) {
                continue;
            }
            flags.add(new PatientDisplayFlagDto()
                .setFlagCode(code)
                .setDisplayLabel(resolveLabel(def, item))
                .setSeverity(def.getSeverity()));
        }
        return flags;
    }

    private static boolean matches(String code, PatientManagementListItemQueryModel item, boolean hasBlacklist) {
        return switch (code) {
            case "TRIAGE_LEVEL" -> item.getTriageLevelEnum() != null;
            case "NURSING_LEVEL" -> StringUtils.isNotBlank(item.getNursingLevelEnum());
            case "REGISTERED" -> StringUtils.isNotBlank(item.getEncounterBusNo());
            case "ADMISSION_24H" -> item.getRegistrationTime() != null
                && ChronoUnit.HOURS.between(item.getRegistrationTime(), LocalDateTime.now()) < 24;
            case "CRITICAL_ILL" -> containsTag(item.getSpecialPopulationTagsJson(), "CRITICAL", "病危");
            case "SERIOUS_ILL" -> containsTag(item.getSpecialPopulationTagsJson(), "SERIOUS", "病重");
            case "GREEN_CHANNEL" -> item.isGreenChannel();
            case "FOCUS" -> item.isFocused();
            case "BLACKLIST" -> hasBlacklist;
            default -> false;
        };
    }

    private static String resolveLabel(EmgPatientDisplayFlagDef def, PatientManagementListItemQueryModel item) {
        if ("TRIAGE_LEVEL".equals(def.getFlagCode()) && item.getTriageLevelEnum() != null) {
            return formatTriageLevel(item.getTriageLevelEnum());
        }
        if ("NURSING_LEVEL".equals(def.getFlagCode()) && StringUtils.isNotBlank(item.getNursingLevelDisplay())) {
            return item.getNursingLevelDisplay();
        }
        return def.getDisplayLabel();
    }

    private static String formatTriageLevel(Integer level) {
        return switch (level) {
            case 1 -> "Ⅰ级";
            case 2 -> "Ⅱ级";
            case 3 -> "Ⅲ级";
            case 4 -> "Ⅳ级";
            case 5 -> "Ⅴ级";
            default -> level + "级";
        };
    }

    private static boolean containsTag(String json, String... tokens) {
        if (StringUtils.isBlank(json)) {
            return false;
        }
        String upper = json.toUpperCase();
        for (String token : tokens) {
            if (upper.contains(token.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    public static String markTypeDisplay(String markTypeCode) {
        if (StringUtils.isBlank(markTypeCode)) {
            return "黑名单";
        }
        return switch (markTypeCode) {
            case "FRAUD" -> "欺诈";
            case "VIOLENCE" -> "暴力闹访";
            case "ARREARS" -> "恶意欠费";
            case "MEDICAL_DISPUTE" -> "医疗纠纷";
            case "OTHER" -> "其他";
            default -> markTypeCode;
        };
    }

    public static Set<Long> patientIdsWithBlacklist(
        Map<Long, List<PatientAlert>> alertsByPatient, LocalDateTime asOf) {
        return alertsByPatient.entrySet().stream()
            .filter(e -> e.getValue().stream().anyMatch(a ->
                PatientAlert.TYPE_BLACKLIST.equals(a.getAlertTypeEnum()) && a.isEffectiveAt(asOf)))
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
    }
}
