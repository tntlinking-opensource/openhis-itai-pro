/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.converter;

import com.openhis.whale.mrms.domain.enums.CaseRecordStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 病案状态枚举与 {@code status_code} 列互转。
 */
@Converter(autoApply = false)
public class CaseRecordStatusConverter implements AttributeConverter<CaseRecordStatus, String> {

    @Override
    public String convertToDatabaseColumn(CaseRecordStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public CaseRecordStatus convertToEntityAttribute(String dbData) {
        return CaseRecordStatus.fromCode(dbData);
    }
}
