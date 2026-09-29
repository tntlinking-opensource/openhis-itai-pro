/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * {@code ServiceRequest.category_enum} 码表（DM-WKF-004 §4.3 ActivityDefCategory）。
 *
 * <p>落库与域内比较一律使用本接口 {@code String} code；HTTP 请求可另传枚举名
 * （如 {@code TREATMENT}），由编排层解析为 code 后写入 {@code wkf_service_request}。</p>
 */
public interface ServiceRequestCategoryConstants {

    /** 诊疗类目：治疗（枚举名 {@code TREATMENT}）。 */
    String SERVICE_CATEGORY_TREATMENT = "21";

    /** 诊疗类目：检验（枚举名 {@code PROOF}）。 */
    String SERVICE_CATEGORY_PROOF = "22";

    /** 诊疗类目：检查（枚举名 {@code TEST}）。 */
    String SERVICE_CATEGORY_TEST = "23";

    /** 诊疗类目：出院（枚举名 {@code DISCHARGE}；反结/召回撤销出院嘱 Pre）。 */
    String SERVICE_CATEGORY_DISCHARGE = "34";

    /** 诊疗类目：床位服务（枚举名 {@code BED_SERVICE}；住院滚费床位费/采暖费/空调费等）。 */
    String SERVICE_CATEGORY_BED_SERVICE = "51";

    /**
     * 诊疗类目：嘱托（枚举名 {@code ENTRUST}；住院嘱托医嘱）。
     *
     * <p>目录固定项名称「嘱托」；{@code ActivityDefinition.category_code=52} →
     * {@code ServiceRequest.category_enum=52}；正文 {@code entrust_content}；见 AS-IP-DOR-003 · BR-IP-ORD-028。</p>
     */
    String SERVICE_CATEGORY_ENTRUST = "52";

    /**
     * 诊疗类目：诊查费（枚举名 {@code CONSULTATION}）。
     *
     * <p>目录类别 {@code activity_category_code=53}；业务类型配合 {@code ActivityType.CONSULTATION}（{@code type_enum=7}）。</p>
     */
    String SERVICE_CATEGORY_CONSULTATION = "53";

    /** 诊疗类目：护理级别（枚举名 {@code NURSING}；住院滚费护理级别医嘱）。 */
    String SERVICE_CATEGORY_NURSING = "26";

    /** 诊疗类目：手术（枚举名 {@code PROCEDURE}；DM-WKF-004 ActivityDefCategory）。 */
    String SERVICE_CATEGORY_PROCEDURE = "24";

    /**
     * 诊疗类目：麻醉（枚举名 {@code ANESTHESIA}；AS-IP-DOR-012 / 业务口径 §3.1）。
     *
     * <p>{@code ActivityDefinition.category_code=25} → {@code ServiceRequest.category_enum=25}；
     * 与手术 {@link #SERVICE_CATEGORY_PROCEDURE} 分单分执行。</p>
     */
    String SERVICE_CATEGORY_ANESTHESIA = "25";

    /** 请求入参枚举名：麻醉（解析为 {@link #SERVICE_CATEGORY_ANESTHESIA}）。 */
    String CATEGORY_ENUM_NAME_ANESTHESIA = "ANESTHESIA";

    /** 请求入参枚举名：治疗（解析为 {@link #SERVICE_CATEGORY_TREATMENT}）。 */
    String CATEGORY_ENUM_NAME_TREATMENT = "TREATMENT";

    /** 请求入参枚举名：检验（解析为 {@link #SERVICE_CATEGORY_PROOF}）。 */
    String CATEGORY_ENUM_NAME_PROOF = "PROOF";

    /** 请求入参枚举名：检查（解析为 {@link #SERVICE_CATEGORY_TEST}）。 */
    String CATEGORY_ENUM_NAME_TEST = "TEST";
}
