/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.common.assembler.nursestation;

import com.openhis.health.domain.diagnosis.vitalsign.command.SaveVitalSignDetailItemCommand;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignDetailRowQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.support.VitalSignDetailSupport;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.VitalSignConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.module.common.dto.nursestation.TemperatureSheetDetailItemDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 体温单 {@link TemperatureSheetDetailItemDto} ↔ 领域明细命令/读模型互转。
 */
public final class TemperatureSheetDetailItemSupport {

    private TemperatureSheetDetailItemSupport() {
    }

    /**
     * 保存请求明细项 → 领域命令 + 主表 remark。
     *
     * @param detailItems API 明细项
     * @return 映射结果
     */
    public static DetailItemMappingResult toSaveCommands(List<TemperatureSheetDetailItemDto> detailItems) {
        List<SaveVitalSignDetailItemCommand> commands = new ArrayList<>();
        StringBuilder remarkBuilder = new StringBuilder();
        if (detailItems == null) {
            return new DetailItemMappingResult(commands, null);
        }
        for (TemperatureSheetDetailItemDto item : detailItems) {
            if (item == null || trimToNull(item.getItemCode()) == null) {
                continue;
            }
            String itemCode = item.getItemCode().trim();
            String termCode = VitalSignDetailSupport.normalizeTermCode(item.getTermCode());
            String itemValue = trimToNull(item.getItemValue());
            // 业务分支：标记类特殊事件无附加值时用占位符落库（领域层 item_value 非空约束）
            if (itemValue == null
                && TerminologyCodeSystemNameConstants.SPECIAL_EVENT_CODE.equals(termCode)) {
                itemValue = VitalSignConstants.SpecialEvent.FLAG_VALUE;
            }
            // 业务分支：皮试阳性/其他仍写主表 remark（兼容原 SDD 策略）
            if (VitalSignConstants.SKIN_TEST_POSITIVE.equals(itemCode)) {
                String skinSegment = VitalSignConstants.SKIN_TEST_POSITIVE_REMARK_PREFIX;
                if (itemValue != null) {
                    skinSegment = skinSegment + itemValue;
                }
                appendRemarkSegment(remarkBuilder, skinSegment);
                continue;
            }
            if (VitalSignConstants.OTHER.equals(itemCode)) {
                appendRemarkSegment(remarkBuilder, itemValue);
                continue;
            }
            SaveVitalSignDetailItemCommand command = new SaveVitalSignDetailItemCommand();
            command.setTermCode(termCode);
            command.setItemCode(itemCode);
            command.setItemValue(itemValue);
            command.setRemark(trimToNull(item.getRemark()));
            commands.add(command);
        }
        String remark = null;
        if (remarkBuilder.length() > 0) {
            remark = remarkBuilder.toString();
        }
        return new DetailItemMappingResult(commands, remark);
    }

    /**
     * 明细读模型 + 主表 remark → API 明细项列表。
     *
     * @param details      同一主表下明细
     * @param headerRemark 主表 remark
     * @return 明细项
     */
    public static List<TemperatureSheetDetailItemDto> toItemDtos(
        List<VitalSignDetailRowQueryModel> details,
        String headerRemark) {
        List<TemperatureSheetDetailItemDto> items = new ArrayList<>();
        if (details != null) {
            for (VitalSignDetailRowQueryModel detail : details) {
                if (detail == null || trimToNull(detail.getItemCode()) == null) {
                    continue;
                }
                TemperatureSheetDetailItemDto dto = new TemperatureSheetDetailItemDto();
                dto.setTermCode(VitalSignDetailSupport.normalizeTermCode(detail.getTermCode()));
                dto.setItemCode(detail.getItemCode().trim());
                dto.setItemValue(normalizeDetailItemValueForApi(dto.getTermCode(), dto.getItemCode(),
                    trimToNull(detail.getItemValue())));
                dto.setRemark(trimToNull(detail.getRemark()));
                items.add(dto);
            }
        }
        appendRemarkAsDetailItems(items, headerRemark);
        return items;
    }

    /**
     * 判断是否有有效明细项（含 remark 投影项）。
     *
     * @param detailItems 明细项
     * @return 是否有内容
     */
    public static boolean hasAnyDetailContent(List<TemperatureSheetDetailItemDto> detailItems) {
        if (detailItems == null || detailItems.isEmpty()) {
            return false;
        }
        for (TemperatureSheetDetailItemDto item : detailItems) {
            if (item != null && trimToNull(item.getItemCode()) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 明细按主表 id 索引。
     *
     * @param details 明细读模型
     * @return vitalSignId → 明细列表
     */
    public static Map<Long, List<VitalSignDetailRowQueryModel>> indexDetailsByHeaderId(
        List<VitalSignDetailRowQueryModel> details) {
        Map<Long, List<VitalSignDetailRowQueryModel>> result = new LinkedHashMap<>();
        if (details == null) {
            return result;
        }
        for (VitalSignDetailRowQueryModel detail : details) {
            if (detail == null || detail.getVitalSignId() == null) {
                continue;
            }
            result.computeIfAbsent(detail.getVitalSignId(), key -> new ArrayList<>()).add(detail);
        }
        return result;
    }

    /**
     * 读侧占位值还原为 API 空值（标记类特殊事件）。
     *
     * @param termCode  术语体系
     * @param itemCode  字典项
     * @param itemValue 库内值
     * @return API 展示值
     */
    private static String normalizeDetailItemValueForApi(String termCode, String itemCode, String itemValue) {
        if (itemValue == null) {
            return null;
        }
        if (TerminologyCodeSystemNameConstants.SPECIAL_EVENT_CODE.equals(termCode)
            && VitalSignConstants.SpecialEvent.FLAG_VALUE.equals(itemValue)) {
            return null;
        }
        return itemValue;
    }

    /**
     * 主表 remark 拆回皮试阳性/其他明细项。
     *
     * @param items        目标列表
     * @param headerRemark 主表 remark
     */
    private static void appendRemarkAsDetailItems(
        List<TemperatureSheetDetailItemDto> items,
        String headerRemark) {
        String remark = trimToNull(headerRemark);
        if (remark == null) {
            return;
        }
        String skinValue = null;
        String otherValue = null;
        String[] segments = remark.split(GeneralSharedConstants.SEMICOLON);
        for (String segment : segments) {
            String text = trimToNull(segment);
            if (text == null) {
                continue;
            }
            if (text.startsWith(VitalSignConstants.SKIN_TEST_POSITIVE_REMARK_PREFIX)) {
                skinValue = text.substring(VitalSignConstants.SKIN_TEST_POSITIVE_REMARK_PREFIX.length()).trim();
                continue;
            }
            if (otherValue == null) {
                otherValue = text;
            } else {
                otherValue = otherValue + GeneralSharedConstants.SEMICOLON_SPACE + text;
            }
        }
        if (trimToNull(skinValue) != null) {
            TemperatureSheetDetailItemDto skinItem = new TemperatureSheetDetailItemDto();
            skinItem.setTermCode(TerminologyCodeSystemNameConstants.VITAL_SIGNS_CODE);
            skinItem.setItemCode(VitalSignConstants.SKIN_TEST_POSITIVE);
            skinItem.setItemValue(skinValue);
            items.add(skinItem);
        }
        if (trimToNull(otherValue) != null) {
            TemperatureSheetDetailItemDto otherItem = new TemperatureSheetDetailItemDto();
            otherItem.setTermCode(TerminologyCodeSystemNameConstants.VITAL_SIGNS_CODE);
            otherItem.setItemCode(VitalSignConstants.OTHER);
            otherItem.setItemValue(otherValue);
            items.add(otherItem);
        }
    }

    /**
     * 追加 remark 片段。
     *
     * @param builder 目标
     * @param segment 片段
     */
    private static void appendRemarkSegment(StringBuilder builder, String segment) {
        String text = trimToNull(segment);
        if (text == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(GeneralSharedConstants.SEMICOLON_SPACE);
        }
        builder.append(text);
    }

    /** 明细项 → 领域命令映射结果。 */
    public static final class DetailItemMappingResult {

        /** 待写入领域的明细命令列表。 */
        private final List<SaveVitalSignDetailItemCommand> commands;

        /** 皮试阳性/其他等投影到主表 remark 的合并文本（可空）。 */
        private final String headerRemark;

        /**
         * @param commands     明细命令
         * @param headerRemark 主表 remark
         */
        public DetailItemMappingResult(
            List<SaveVitalSignDetailItemCommand> commands,
            String headerRemark) {
            this.commands = commands;
            this.headerRemark = headerRemark;
        }

        /**
         * @return 待写入领域的明细命令列表
         */
        public List<SaveVitalSignDetailItemCommand> getCommands() {
            return commands;
        }

        /**
         * @return 主表 remark（皮试阳性/其他等投影项合并文本，可空）
         */
        public String getHeaderRemark() {
            return headerRemark;
        }
    }
}
