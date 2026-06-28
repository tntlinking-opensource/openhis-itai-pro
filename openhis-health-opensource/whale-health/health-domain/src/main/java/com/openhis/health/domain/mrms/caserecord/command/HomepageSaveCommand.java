/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.caserecord.command;

import com.openhis.health.domain.mrms.caserecord.entity.HomepageDiagnosis;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageProcedure;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页保存命令（领域写路径入参，Wave 1 显式字段）。
 *
 * <p>非 null 标量写入 {@code mrms_case_record_homepage} 并标记 manual 来源；子表非 null 时全量替换。</p>
 */
@Getter
@Setter
public class HomepageSaveCommand {

    /** 病案主档主键 {@code mrms_case_record.id}。 */
    private Long caseRecordId;

    /** 操作人 {@code adm_practitioner.id}，写入审计字段。 */
    private Long operatorId;

    /** 患者姓名（4401 psn_name）。 */
    private String psnName;

    /** 性别字典码（4401 gend）。 */
    private String gendCode;

    /** 出生日期（4401 brdy）。 */
    private LocalDate brdy;

    /** 证件号码（4401 certno）。 */
    private String certno;

    /** 住院号（4401 关联住院号 / {@code adm_encounter.bus_no}）。 */
    private String iptNo;

    /** 入院途径字典码（4401 adm_way，提交校验必填）。 */
    private String admWayCode;

    /** 入院科别编码（4401 adm_caty）。 */
    private String admCatyCode;

    /** 入院时间（4401 adm_time）。 */
    private LocalDateTime admTime;

    /** 出院科别编码（4401 dscg_caty）。 */
    private String dscgCatyCode;

    /** 出院时间（4401 dscg_time，须不早于 adm_time）。 */
    private LocalDateTime dscgTime;

    /** 住院天数（4401 ipt_days）。 */
    private Integer iptDays;

    /** 离院方式字典码（4401 dscg_way，提交校验必填）。 */
    private String dscgWayCode;

    /** 主任/副主任医师姓名（4401 chfdr，提交校验必填）。 */
    private String chfdrName;

    /** 主治医师姓名（4401 atddr，提交校验必填）。 */
    private String atddrName;

    /** 住院医师姓名（4401 ipt_dr，提交校验必填）。 */
    private String iptDrName;

    /** 扩展 JSON 键值（合并入 {@code extension_json}，如费用分项）。 */
    private Map<String, Object> extension = new HashMap<>();

    /** 诊断子表全量列表；null 表示本次保存不替换子表。 */
    private List<HomepageDiagnosis> diagnoses;

    /** 手术子表全量列表；null 表示本次保存不替换子表。 */
    private List<HomepageProcedure> procedures;
}
