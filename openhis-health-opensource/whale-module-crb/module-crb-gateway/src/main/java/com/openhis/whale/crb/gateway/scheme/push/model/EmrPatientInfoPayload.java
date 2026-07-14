/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.scheme.push.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * {@code emrPatientInfo} receive POST 报文体（P10-C/D 参考实现）。
 *
 * <p><b>职责：</b>定义患者基本信息推送的平台 JSON 字段模型，与传染病前置软件接口文档对齐。</p>
 * <p><b>实现位置：</b>本类；由 {@code EmrPatientInfoPayloadAssembler} 组装</p>
 * <p><b>调用方：</b>bridge {@code EmrPatientInfoMapper} → {@code PushSchemeJsonSupport.toObjectNode}</p>
 * <p><b>架构边界：</b>gateway scheme model；bridge 禁止直建 {@code ObjectNode} 字段 key。</p>
 * <p><b>迁移阶段：</b>P10-C/D</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class EmrPatientInfoPayload {

    /** 患者国标 id，如 P123（JSON key: {@code id}）。 */
    private String id;

    /** 患者姓名（JSON key: {@code patientName}）。 */
    private String patientName;

    /** 证件号码（JSON key: {@code idCard}）。 */
    private String idCard;

    /** 证件类型代码，如 01=居民身份证（JSON key: {@code idCardTypeCode}）。 */
    private String idCardTypeCode;

    /** 证件类型名称（JSON key: {@code idCardTypeName}）。 */
    private String idCardTypeName;

    /** 性别代码（JSON key: {@code genderCode}）。 */
    private String genderCode;

    /** 出生日期，格式 yyyy-MM-dd（JSON key: {@code birthDate}）。 */
    private String birthDate;

    /** 联系电话（JSON key: {@code tel}）。 */
    private String tel;

    /** 工作单位（JSON key: {@code workunit}）。 */
    private String workunit;

    /** 民族代码（JSON key: {@code nationCode}）。 */
    private String nationCode;

    /** 国家机构代码（JSON key: {@code orgCode}）。 */
    private String orgCode;

    /** 机构名称（JSON key: {@code orgName}）。 */
    private String orgName;

    /** 操作人 id（JSON key: {@code operatorId}）。 */
    private String operatorId;

    /** 操作时间（JSON key: {@code operationTime}）。 */
    private String operationTime;
}
