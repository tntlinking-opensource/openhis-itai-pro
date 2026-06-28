/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.caserecord.converter;

import com.openhis.health.domain.share.enums.CaseRecordStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 病案状态枚举与 {@code status_code} 列互转。
 */
@Converter(autoApply = false)
public class CaseRecordStatusConverter implements AttributeConverter<CaseRecordStatus, String> {

    /**
     * 实体枚举 → 数据库 {@code status_code} 字符串。
     *
     * @param attribute 病案状态枚举
     * @return 持久化 code；attribute 为 null 时返回 null
     */
    @Override
    public String convertToDatabaseColumn(CaseRecordStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getCode();
    }

    /**
     * 数据库字符串 → 实体枚举；未知 code 由 {@link EnumUtil} 处理。
     *
     * @param dbData 库表 status_code
     * @return 枚举实例；dbData 为空或未知时可能为 null
     */
    @Override
    public CaseRecordStatus convertToEntityAttribute(String dbData) {
        return EnumUtil.fromCode(CaseRecordStatus.class, dbData);
    }
}
