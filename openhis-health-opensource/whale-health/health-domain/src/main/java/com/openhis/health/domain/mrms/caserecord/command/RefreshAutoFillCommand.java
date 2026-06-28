/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.caserecord.command;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页 HIS 自动取值刷新命令。
 *
 * <p>委托 {@link com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService#mergeAutoFillFromHis}，
 * 跨聚合读经 {@link com.openhis.health.domain.mrms.caserecord.support.CaseRecordAutoFillSupport}。</p>
 */
@Getter
@Setter
public class RefreshAutoFillCommand {

    /** 病案主档主键 {@code mrms_case_record.id}。 */
    private Long caseRecordId;

    /** 刷新分区：{@code basic/inpatient/staff/diagnosis/procedure/all}。 */
    private List<String> sections = new ArrayList<>();

    /** 是否强制覆盖 manual 锁定字段（默认 false，尊重 field_sources_json）。 */
    private boolean forceOverwrite;

    /** 操作人 {@code adm_practitioner.id}。 */
    private Long operatorId;
}
