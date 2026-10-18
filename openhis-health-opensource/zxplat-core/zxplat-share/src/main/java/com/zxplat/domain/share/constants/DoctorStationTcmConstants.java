/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

import java.util.List;

/**
 * 门诊医生站中医 Tab 跨层共享常量（AS-OP-DOR-010）。
 */
public interface DoctorStationTcmConstants {

    /** IF-DOR-31：目录角色——中医病名。 */
    String CATALOG_ROLE_DISEASE = "DISEASE";

    /** IF-DOR-31：目录角色——中医证候。 */
    String CATALOG_ROLE_SYNDROME = "SYNDROME";

    /** 中医病名目录来源编码（术语 {@code tcm_dx}）。 */
    String TCM_SOURCE_DISEASE = DoctorStationDiagnosisConstants.SOURCE_TCM_DX;

    /** 中医证候目录来源编码（术语 {@code tcm_syndrome}）。 */
    String TCM_SOURCE_SYNDROME = DoctorStationDiagnosisConstants.SOURCE_TCM_SYNDROME;

    /** IF-DOR-35：中药饮片目录分类码（中草药）。 */
    String HERBAL_CATEGORY_CODE = "4";

    /** IF-DOR-38：签退动作。 */
    String ACTION_WITHDRAW = "WITHDRAW";

    /** IF-DOR-38：删除动作。 */
    String ACTION_DELETE = "DELETE";

    /** IF-DOR-38：整方范围。 */
    String SCOPE_PRESCRIPTION = "PRESCRIPTION";

    /** IF-DOR-38：单行范围。 */
    String SCOPE_LINE = "LINE";

    /**
     * 代煎中药诊疗项目名称（产品内置；{@code ActivityDefinition.name} 精确匹配）。
     *
     * <p>见 {@link BuiltInActivityDefinitionConstants}：禁止删/改名/改类型与类目。</p>
     */
    String DECOCTION_ACTIVITY_NAME = "代煎中药";
    /** IF-DOR-31 病名 source 集合。 */
    List<String> TCM_DISEASE_SOURCES = List.of(TCM_SOURCE_DISEASE);

    /** IF-DOR-31 证候 source 集合。 */
    List<String> TCM_SYNDROME_SOURCES = List.of(TCM_SOURCE_SYNDROME);
}
