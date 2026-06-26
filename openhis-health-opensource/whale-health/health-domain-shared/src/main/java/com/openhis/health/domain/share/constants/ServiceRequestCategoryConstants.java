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

    /** 诊疗类目：护理级别（枚举名 {@code NURSING}；住院滚费护理级别医嘱）。 */
    String SERVICE_CATEGORY_NURSING = "26";

    /** 诊疗类目：手术（枚举名 {@code PROCEDURE}；DM-WKF-004 ActivityDefCategory）。 */
    String SERVICE_CATEGORY_PROCEDURE = "24";

    /** 请求入参枚举名：治疗（解析为 {@link #SERVICE_CATEGORY_TREATMENT}）。 */
    String CATEGORY_ENUM_NAME_TREATMENT = "TREATMENT";

    /** 请求入参枚举名：检验（解析为 {@link #SERVICE_CATEGORY_PROOF}）。 */
    String CATEGORY_ENUM_NAME_PROOF = "PROOF";

    /** 请求入参枚举名：检查（解析为 {@link #SERVICE_CATEGORY_TEST}）。 */
    String CATEGORY_ENUM_NAME_TEST = "TEST";
}
