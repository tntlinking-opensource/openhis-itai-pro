/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.booking.outpatient;

import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.OutpatientRegistrationCancelExtensionIn;
import com.zxplat.domain.infrastructure.integration.booking.ExtensionIn.OutpatientWalkInSlotAllocateExtensionIn;

/**
 * 门诊挂号写侧衔接预约（IF-REG-07/10、现场占号）。
 *
 * <p><b>实现</b>：{@code OutpatientBookingRegistrationExtensionImpl}（booking-adapter）。</p>
 */
public interface OutpatientBookingRegistrationExtension {

    /**
     * 门诊预约写侧扩展是否已装配。
     *
     * @return false 时挂号不写回 booking、不占现场号源
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 挂号成功后关联就诊与预约（IF-REG-07）。
     *
     * @param appointmentId 预约主键
     * @param encounterId   新产生的就诊主键
     */
    default void linkEncounter(Long appointmentId, Long encounterId) {
        // NoOp
    }

    /**
     * 现场挂号占用当日 slot（模式 C）。
     *
     * @param command 占号参数
     * @return 占用的 slot 主键；失败或未装配时返回 null
     */
    default Long allocateWalkInSlot(OutpatientWalkInSlotAllocateExtensionIn command) {
        return null;
    }

    /**
     * 挂号完成后到诊签到（IF-REG-10）。
     *
     * @param appointmentId 预约主键
     * @param encounterId   就诊主键
     */
    default void checkInAfterRegister(Long appointmentId, Long encounterId) {
        // NoOp
    }

    /**
     * 退号后按策略释放预约或回滚占号。
     *
     * @param encounterId 已退号就诊主键
     * @param reason      退号原因与释放策略
     */
    default void onRegistrationCancelled(Long encounterId, OutpatientRegistrationCancelExtensionIn reason) {
        // NoOp
    }
}
