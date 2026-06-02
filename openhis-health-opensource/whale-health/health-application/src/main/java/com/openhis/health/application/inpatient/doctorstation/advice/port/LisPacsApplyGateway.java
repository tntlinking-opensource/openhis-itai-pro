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

package com.openhis.health.application.inpatient.doctorstation.advice.port;

/**
 * IF-IP-DOR-15：检验/检查外发网关（严格/忽略模式由租户配置驱动，实现侧捕获异常）。
 *
 * <p>诊疗医嘱签发成功后由 {@link com.openhis.health.application.inpatient.doctorstation.advice.service.InpatientDoctorStationAdviceAppService}
 * 调用；未对接外部系统时使用 {@link NoopLisPacsApplyGateway} 占位。实现类须根据活动类型判断是否为检验/检查，
 * 非目标类型应空操作，避免误发。</p>
 */
public interface LisPacsApplyGateway {

    /**
     * 若目标为检验/检查类诊疗则提交外发，否则空操作。
     *
     * @param command 外发上下文（就诊/患者锚点）；实现可接受 {@code null} 并直接返回
     * @throws com.openhis.whale.core.application.exception.BusinessException 严格模式下外发失败时由实现抛出（忽略模式应吞掉并记录）
     */
    void submitIfLabOrImaging(LisPacsAdviceApplyCommand command);
}
