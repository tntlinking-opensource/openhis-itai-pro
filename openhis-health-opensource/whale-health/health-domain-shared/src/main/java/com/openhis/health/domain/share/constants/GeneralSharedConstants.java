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

import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** 跨模块通用字面量与格式（拼接模板、正则、时间格式、批量上限等）。 */
public interface GeneralSharedConstants {

    /**
     * 格式符
     */
    String FORMAT = "%s";

    /**
     * SQL {@code LIKE} 百分号通配符（匹配任意长度子串），字面量 {@code %}。
     */
    String LIKE_PERCENT = "%";

    /**
     * 逗号（用于拼接）
     */
    String COMMA_FORMAT = "%s,%s";

    /**
     * 拼接符（三项）
     */
    String MONTAGE_FORMAT = "%s%s%s";

    /**
     * 拼接符(s-s-d-d)
     */
    String SS_DD_FORMAT = "%s-%s-%d-%d";

    /**
     * 拼接符(s-d-d)
     */
    String S_DD_FORMAT = "%s-%d-%d";

    /**
     * 逗号
     */
    String COMMA = ",";

    /**
     * 分号（多片段备注/文本 {@code split} 与写侧拼接共用）。
     */
    String SEMICOLON = ";";

    /**
     * 分号加空格（多片段备注拼接时的间隔符）。
     */
    String SEMICOLON_SPACE = "; ";

    /**
     * 点
     */
    String POINT = ".";

    /**
     * 1（计数/次数等缺省整型字面量）。
     */
    Integer ONE = 1;

    /**
     * 平台出厂/种子数据租户标识（{@code tenant_id=0}）。
     *
     * <p>用于「租户行覆盖平台/种子行」场景，例如打印模板 fallback（DM-PRT-001 §4.2）、
     * MRMS 质控种子（V81）、prt_print_binding 出厂 seed 等。</p>
     */
    Long PLATFORM_TENANT_ID = 0L;

    /**
     * 3（用于字符串截位）
     */
    Integer THREE = 3;

    /**
     * 中杠
     */
    String DASH = "-";

    /**
     * 手机号正则（中国大陆）。
     */
    String PHONE_REGEX = "^1[3-9]\\d{9}$";

    /**
     * 手机号正则 Pattern（预编译，避免重复编译）。
     */
    Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEX);

    /**
     * 身份证号正则（18 位）。
     */
    String ID_CARD_REGEX = "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])([0-2][1-9]|3[0-1])\\d{3}[0-9Xx]$";

    /**
     * 证件类型：居民身份证。
     */
    String ID_CARD_TYPE_CODE = "ID_CARD";

    /**
     * 证件类型：预检分配的三无临时分诊号（存 {@code adm_patient_identifier}，号码为 identifier_no）。
     */
    String PROVISIONAL_TRIAGE_NO_TYPE_CODE = "PROVISIONAL_TRIAGE_NO";

    /**
     * 批量处理最大条数。
     */
    Integer BATCH_LIMIT = 100;

    /**
     * 列表/工作站查询未显式传入 {@code pageSize} 时的默认条数（与多数 QueryService 回落口径一致）。
     */
    Integer DEFAULT_QUERY_PAGE_SIZE = 20;

    /**
     * 产品定义读模型分页单次最大条数（医嘱开立目录、采购选品等与 AS 上限一致）。
     */
    Integer PRODUCT_DEFINITION_RM_PAGE_SIZE_MAX = 100;

    /**
     * 产品「来源表名 + 定义主键」复合键拼接分隔符（ASCII SOH），避免与表名/业务编号中常见字符碰撞（全量规范 A.9.1）。
     */
    Character PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR = '\u0001';

    /**
     * 医嘱开立方目录请求 {@code pricingFlag}：取该值时排除药品表行（AS-SDD IF-DOR-11）。
     */
    Integer PRODUCT_DEFINITION_PRICING_FLAG_EXCLUDE_MEDICATION = 1;

    /**
     * TRC-WIDE-R-001 / WHS-IN-SDD-R-008：承载「原始追溯扫码串」的 HTTP/DTO 与目标 DDL 列（如 {@code wkf_supply_request.trace_no}）最大字符宽度。
     */
    int TRACE_NO_WIDE_MAX_LENGTH = 50000;

    /**
     * 学生体检结果：合格。
     */
    Integer STUDENT_PHYSICAL_EXAM_PASS = 2;

    /**
     * 身份证号地区码（默认北京东城示例值）。
     */
    String AREA_CODE = "110101";

    /**
     * 通用日期格式（前端字符串 ↔ 后端 LocalDate，如结账日、发药日展示）。
     */
    String DATE_DISPLAY_PATTERN = "yyyy-MM-dd";

    /**
     * 通用日期格式化器（预编译，避免重复创建）。
     */
    DateTimeFormatter DATE_DISPLAY_FORMATTER = DateTimeFormatter.ofPattern(DATE_DISPLAY_PATTERN);

    /**
     * 通用时间格式（前端字符串 ↔ 后端 LocalDateTime）。
     */
    String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 通用时间格式化器（预编译，避免重复创建）。
     */
    DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    /**
     * 导出文件名时间戳格式（紧凑 {@code yyyyMMddHHmmss}，无分隔符）。
     */
    String EXPORT_FILE_TIMESTAMP_PATTERN = "yyyyMMddHHmmss";

    /**
     * 导出文件名时间戳格式化器。
     */
    DateTimeFormatter EXPORT_FILE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern(EXPORT_FILE_TIMESTAMP_PATTERN);

    /**
     * 年月格式（医保累计等 {@code YearMonth} 传参，如 {@code 202506}）。
     */
    String YEAR_MONTH_PATTERN = "yyyyMM";

    /**
     * 年月格式化器。
     */
    DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern(YEAR_MONTH_PATTERN);

    /**
     * ISO-8601 无时区本地日期时间格式化器（{@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}，日期与时间以 {@code T} 分隔）。
     * <p>用于 API 字符串字段需与常见 JSON {@code LocalDateTime} 序列化形态对齐的场景（如医生站医嘱列表开立时刻 {@code authoredAt}）。</p>
     * <p>若产品契约固定为 {@code yyyy-MM-dd HH:mm:ss}（空格分隔），应使用 {@link #DATE_TIME_FORMATTER}，二者不可混用以免前端解析失败。</p>
     */
    DateTimeFormatter ISO_LOCAL_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 本地时间格式（仅时分，护理记录/体温单记录时间展示与解析）。
     */
    String TIME_HH_MM_PATTERN = "HH:mm";

    /**
     * 本地时间格式（时分秒，护理记录保存请求回落解析）。
     */
    String TIME_HH_MM_SS_PATTERN = "HH:mm:ss";

    /**
     * {@link #TIME_HH_MM_PATTERN} 预编译格式化器。
     */
    DateTimeFormatter TIME_HH_MM_FORMATTER = DateTimeFormatter.ofPattern(TIME_HH_MM_PATTERN);

    /**
     * {@link #TIME_HH_MM_SS_PATTERN} 预编译格式化器。
     */
    DateTimeFormatter TIME_HH_MM_SS_FORMATTER = DateTimeFormatter.ofPattern(TIME_HH_MM_SS_PATTERN);

    /**
     * 患者年龄整岁展示后缀，与数字拼接为「N岁」。
     */
    String PATIENT_AGE_YEAR_SUFFIX = "岁";

    /**
     * 执行记录历史状态码（与 {@link com.openhis.health.domain.share.enums.EventStatus#CANCEL} 同语义）。
     *
     * <p>Flyway {@code V43__Migrate_procedure_status_aborted_to_cancel} 收敛后库表不应再出现本值。</p>
     */
    String EVENT_STATUS_LEGACY_ABORTED_CODE = "ABORTED";

    /**
     * 展示/装配回落用的空字符串（列表列需非 null 文本且业务上表示「无值」时使用，与 {@code null} 区分）。
     *
     * <p>禁止在业务代码中散落 {@code return ""} / {@code setXxx("")}；统一引用本常量（B.10.3）。</p>
     */
    String EMPTY_STRING = "";

    /**
     * 零
     */
    String ZERO = "0";

    /**
     * {@code whale_settings} 开关型配置开启值（如自动扣库、学生开关等）。
     */
    String WHALE_SETTING_SWITCH_ON = "1";

    /**
     * 皮试检查诊疗项目名称（与主数据 {@code ActivityDefinition.name} 精确匹配；对齐初版 {@code SKIN_TEST_INSPECTION}）。
     */
    String SKIN_TEST_INSPECTION_ACTIVITY_NAME = "皮试检查";

}

