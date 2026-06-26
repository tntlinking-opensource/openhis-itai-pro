/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.support;

import com.openhis.chs.domain.inventory.ChsInventoryConstants;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/** 进销存 tracePolicy 判定（§4.8.6）。 */
@Component
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryTracePolicyResolver {

    @Value("${openhis.chs.inventory.trace-required-default:true}")
    private boolean traceRequiredDefault;

    @Value("${openhis.chs.inventory.policy-exempt-catalog-types:}")
    private List<String> policyExemptCatalogTypes = Collections.emptyList();

    @Value("${openhis.chs.inventory.no-code-library-catalog-types:}")
    private List<String> noCodeLibraryCatalogTypes = Collections.emptyList();

    public String resolve(MedicationDefinition medication, MedicationDispense dispense) {
        String dispenseUnitCode;
        if (dispense == null) {
            dispenseUnitCode = null;
        } else {
            dispenseUnitCode = dispense.getUnitCode();
        }
        return resolve(medication, dispenseUnitCode);
    }

    public String resolve(MedicationDefinition medication, String dispenseUnitCode) {
        if (medication == null || StringUtils.isBlank(medication.getYbNo())) {
            return ChsInventoryConstants.TRACE_POLICY_NO_YB_NO;
        }
        if (matchesCatalogType(medication.getCategoryCode(), policyExemptCatalogTypes)) {
            return ChsInventoryConstants.TRACE_POLICY_POLICY_EXEMPT;
        }
        if (matchesCatalogType(medication.getCategoryCode(), noCodeLibraryCatalogTypes)) {
            return ChsInventoryConstants.TRACE_POLICY_NO_CODE_LIBRARY;
        }
        if (ChsInventoryConstants.TRDN_FLAG_SPLIT.equals(resolveTrdnFlag(medication, dispenseUnitCode))) {
            return ChsInventoryConstants.TRACE_POLICY_SPLIT_DISPENSE;
        }
        if (traceRequiredDefault) {
            return ChsInventoryConstants.TRACE_POLICY_TRACE_REQUIRED;
        }
        return ChsInventoryConstants.TRACE_POLICY_NO_CODE_LIBRARY;
    }

    public String resolveTrdnFlag(MedicationDefinition medication, MedicationDispense dispense) {
        String dispenseUnitCode;
        if (dispense == null) {
            dispenseUnitCode = null;
        } else {
            dispenseUnitCode = dispense.getUnitCode();
        }
        return resolveTrdnFlag(medication, dispenseUnitCode);
    }

    public String resolveTrdnFlag(MedicationDefinition medication, String dispenseUnitCode) {
        if (medication == null) {
            return ChsInventoryConstants.TRDN_FLAG_WHOLE;
        }
        if (StringUtils.isNotBlank(medication.getDefEncounterUnitCode())
            && StringUtils.equals(medication.getDefEncounterUnitCode(), dispenseUnitCode)) {
            return ChsInventoryConstants.TRDN_FLAG_WHOLE;
        }
        return ChsInventoryConstants.TRDN_FLAG_SPLIT;
    }

    private static boolean matchesCatalogType(String categoryCode, List<String> configuredTypes) {
        if (StringUtils.isBlank(categoryCode) || configuredTypes == null || configuredTypes.isEmpty()) {
            return false;
        }
        String normalized = categoryCode.trim();
        for (String configured : configuredTypes) {
            if (configured != null && normalized.equalsIgnoreCase(configured.trim())) {
                return true;
            }
        }
        return false;
    }
}
