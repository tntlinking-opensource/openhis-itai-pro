/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.specimentubemerge.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 合管分组读模型：同一检验设备下的某一合管组（DM-CUS-014）。
 *
 * <p>采血贴/开嘱派生/LIS 外发均按 {@code deviceId + groupNo} 聚组，不同组不合管。</p>
 */
@Getter
@Setter
public class SpecimenTubeMergeGroupQueryModel {

    /** 检验设备主键 {@code adm_device.id}。 */
    private Long deviceId;

    /** 设备内合管组序号（从 1 起）。 */
    private Integer groupNo;

    /**
     * 构造分组引用。
     *
     * @param deviceId 设备主键
     * @param groupNo  组序号
     * @return 引用；任一为空时返回 null
     */
    public static SpecimenTubeMergeGroupQueryModel of(Long deviceId, Integer groupNo) {
        if (deviceId == null || groupNo == null) {
            return null;
        }
        SpecimenTubeMergeGroupQueryModel ref = new SpecimenTubeMergeGroupQueryModel();
        ref.setDeviceId(deviceId);
        ref.setGroupNo(groupNo);
        return ref;
    }

    /**
     * 稳定分组键（同设备同组相同），供同批去重与前端聚组。
     *
     * @return {@code deviceId:groupNo}；字段不全时返回空串
     */
    public String toGroupKey() {
        if (this.deviceId == null || this.groupNo == null) {
            return "";
        }
        return this.deviceId + ":" + this.groupNo;
    }

    /**
     * 同设备同组视为同一合管分组。
     *
     * @param obj 比较对象
     * @return 是否同一分组
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SpecimenTubeMergeGroupQueryModel other)) {
            return false;
        }
        if (this.deviceId == null || other.deviceId == null) {
            return false;
        }
        if (this.groupNo == null || other.groupNo == null) {
            return false;
        }
        return this.deviceId.equals(other.deviceId) && this.groupNo.equals(other.groupNo);
    }

    /**
     * 与 {@link #equals} 一致的哈希。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        int result = 17;
        if (this.deviceId != null) {
            result = 31 * result + this.deviceId.hashCode();
        }
        if (this.groupNo != null) {
            result = 31 * result + this.groupNo.hashCode();
        }
        return result;
    }
}
