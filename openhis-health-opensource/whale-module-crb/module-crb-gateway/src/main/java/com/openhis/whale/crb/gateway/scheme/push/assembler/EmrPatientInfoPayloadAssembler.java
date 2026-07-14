/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.scheme.push.assembler;

import com.openhis.whale.crb.gateway.scheme.push.model.EmrPatientInfoPayload;
import com.openhis.whale.crb.gateway.scheme.push.model.PushOperationMeta;

/**
 * {@code emrPatientInfo} 报文组装器（P10-C/D 参考实现）。
 *
 * <p><b>职责：</b>将患者基本字段与 {@link PushOperationMeta} 合并为平台 receive 报文体。</p>
 * <p><b>实现位置：</b>本类（gateway {@code scheme.push.assembler}）</p>
 * <p><b>调用方：</b>bridge {@code EmrPatientInfoMapper}</p>
 * <p><b>架构边界：</b>gateway scheme 组装；身份证类型默认值（01/居民身份证）在本类集中维护。</p>
 * <p><b>迁移阶段：</b>P10-C/D</p>
 */
public final class EmrPatientInfoPayloadAssembler {

    private EmrPatientInfoPayloadAssembler() {
    }

    /**
     * 组装 emrPatientInfo payload。
     *
     * <p>当 idCard 非空时自动填充 idCardTypeCode=01、idCardTypeName=居民身份证。</p>
     *
     * @param id 患者国标 id
     * @param patientName 患者姓名
     * @param idCard 证件号
     * @param genderCode 性别代码
     * @param birthDate 出生日期 yyyy-MM-dd
     * @param tel 联系电话
     * @param workunit 工作单位
     * @param nationCode 民族代码
     * @param meta 操作元数据；null 时不写入 org/operator 字段
     * @return 完整 {@link EmrPatientInfoPayload}
     */
    public static EmrPatientInfoPayload assemble(
            String id,
            String patientName,
            String idCard,
            String genderCode,
            String birthDate,
            String tel,
            String workunit,
            String nationCode,
            PushOperationMeta meta) {
        EmrPatientInfoPayload payload = new EmrPatientInfoPayload()
                .setId(id)
                .setPatientName(patientName);
        if (idCard != null && !idCard.isBlank()) {
            payload.setIdCard(idCard);
            payload.setIdCardTypeCode("01");
            payload.setIdCardTypeName("居民身份证");
        }
        if (genderCode != null) {
            payload.setGenderCode(genderCode);
        }
        if (birthDate != null) {
            payload.setBirthDate(birthDate);
        }
        if (tel != null) {
            payload.setTel(tel);
        }
        if (workunit != null) {
            payload.setWorkunit(workunit);
        }
        if (nationCode != null) {
            payload.setNationCode(nationCode);
        }
        if (meta != null) {
            payload.setOrgCode(meta.getOrgCode());
            payload.setOrgName(meta.getOrgName());
            payload.setOperatorId(meta.getOperatorId());
            payload.setOperationTime(meta.getOperationTime());
        }
        return payload;
    }
}
