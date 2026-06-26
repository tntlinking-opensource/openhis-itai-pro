package com.openhis.whale.report.application.report.support;

import com.openhis.whale.report.application.report.response.ReportSqlMetadataColumnDto;
import com.openhis.whale.report.application.report.response.ReportSqlMetadataJoinDto;
import com.openhis.whale.report.application.report.response.ReportSqlMetadataTableDto;

import java.util.List;

/**
 * C 类 SQL 报表常用 Whale 表元数据（设计器可视化建表用）。
 */
public final class ReportSqlMetadataCatalog {

    private ReportSqlMetadataCatalog() {}

    /**
     * 设计器可用表元数据列表（静态出厂目录）。
     *
     * @return 表、列与推荐 JOIN 元数据
     */
    public static List<ReportSqlMetadataTableDto> tables() {
        return List.of(
                encounterTable(),
                patientTable(),
                locationTable(),
                encounterLocationTable(),
                chargeItemTable(),
                inventoryItemTable(),
                medicationDispenseTable(),
                accountTable());
    }

    private static ReportSqlMetadataTableDto encounterTable() {
        ReportSqlMetadataTableDto dto = base(
                "adm_encounter",
                "就诊",
                "门诊/住院就诊主表",
                "e",
                "start_time");
        dto.setColumns(List.of(
                col("bus_no", "就诊号", "group"),
                col("patient_id", "患者ID", "group"),
                col("organization_id", "科室ID", "group"),
                col("class_enum", "就诊类别", "filter"),
                col("status_enum", "状态", "filter"),
                col("first_enum", "初复诊", "group"),
                col("start_time", "开始时间", "filter")));
        dto.setJoins(List.of(
                join("adm_patient", "p", "患者", "p.id = e.patient_id AND p.is_deleted = FALSE"),
                join("adm_location", "loc", "科室", "loc.id = e.organization_id AND loc.is_deleted = FALSE")));
        return dto;
    }

    private static ReportSqlMetadataTableDto patientTable() {
        ReportSqlMetadataTableDto dto = base("adm_patient", "患者", "患者主索引", "p", null);
        dto.setColumns(List.of(
                col("bus_no", "患者编号", "group"),
                col("name", "姓名", "group"),
                col("gender_enum", "性别", "group"),
                col("birth_date", "出生日期", "filter")));
        return dto;
    }

    private static ReportSqlMetadataTableDto locationTable() {
        ReportSqlMetadataTableDto dto = base("adm_location", "科室/地点", "组织与地点", "loc", null);
        dto.setColumns(List.of(
                col("name", "名称", "group"),
                col("form_enum", "类型", "filter"),
                col("bus_no", "编码", "group")));
        return dto;
    }

    private static ReportSqlMetadataTableDto encounterLocationTable() {
        ReportSqlMetadataTableDto dto = base(
                "adm_encounter_location",
                "就诊地点",
                "就诊与病区/床位",
                "el",
                "start_time");
        dto.setColumns(List.of(
                col("encounter_id", "就诊ID", "group"),
                col("location_id", "地点ID", "group"),
                col("form_enum", "形式", "filter")));
        dto.setJoins(List.of(
                join("adm_encounter", "e", "就诊", "e.id = el.encounter_id AND e.is_deleted = FALSE"),
                join("adm_location", "ward", "病区", "ward.id = el.location_id AND ward.is_deleted = FALSE")));
        return dto;
    }

    private static ReportSqlMetadataTableDto chargeItemTable() {
        ReportSqlMetadataTableDto dto = base(
                "adm_charge_item",
                "收费明细",
                "门诊/住院费用明细",
                "ci",
                "occurrence_time");
        dto.setColumns(List.of(
                col("bus_no", "单据号", "group"),
                col("total_price", "金额", "sum"),
                col("occurrence_time", "发生时间", "filter"),
                col("encounter_id", "就诊ID", "group")));
        dto.setJoins(List.of(
                join("adm_encounter", "e", "就诊", "e.id = ci.encounter_id AND e.is_deleted = FALSE")));
        return dto;
    }

    private static ReportSqlMetadataTableDto inventoryItemTable() {
        ReportSqlMetadataTableDto dto = base(
                "wkf_inventory_item",
                "库存明细",
                "库房库存",
                "inv",
                "expiration_date");
        dto.setColumns(List.of(
                col("name", "品名", "group"),
                col("quantity", "数量", "sum"),
                col("location_id", "库房ID", "group"),
                col("lot_number", "批号", "group"),
                col("unit_code", "单位", "group"),
                col("expiration_date", "效期", "filter")));
        dto.setJoins(List.of(
                join("adm_location", "loc", "库房", "loc.id = inv.location_id AND loc.is_deleted = FALSE")));
        return dto;
    }

    private static ReportSqlMetadataTableDto medicationDispenseTable() {
        ReportSqlMetadataTableDto dto = base(
                "med_medication_dispense",
                "发药记录",
                "门诊/住院发药",
                "md",
                "dispense_time");
        dto.setColumns(List.of(
                col("bus_no", "发药单号", "group"),
                col("quantity", "数量", "sum"),
                col("dispense_time", "发药时间", "filter")));
        return dto;
    }

    private static ReportSqlMetadataTableDto accountTable() {
        ReportSqlMetadataTableDto dto = base("adm_account", "账户", "预交金/账户", "acct", null);
        dto.setColumns(List.of(
                col("encounter_id", "就诊ID", "group"),
                col("balance", "余额", "sum"),
                col("type_code", "账户类型", "filter")));
        return dto;
    }

    private static ReportSqlMetadataTableDto base(
            String table, String label, String description, String alias, String dateColumn) {
        ReportSqlMetadataTableDto dto = new ReportSqlMetadataTableDto();
        dto.setTableName(table);
        dto.setLabel(label);
        dto.setDescription(description);
        dto.setDefaultAlias(alias);
        dto.setDateColumn(dateColumn);
        return dto;
    }

    private static ReportSqlMetadataColumnDto col(String field, String label, String role) {
        ReportSqlMetadataColumnDto dto = new ReportSqlMetadataColumnDto();
        dto.setField(field);
        dto.setLabel(label);
        dto.setRole(role);
        return dto;
    }

    private static ReportSqlMetadataJoinDto join(String table, String alias, String label, String on) {
        ReportSqlMetadataJoinDto dto = new ReportSqlMetadataJoinDto();
        dto.setTableName(table);
        dto.setAlias(alias);
        dto.setLabel(label);
        dto.setOnClause(on);
        return dto;
    }
}
