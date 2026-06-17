package com.openhis.whale.booking.contracts.port;

import com.openhis.whale.booking.contracts.command.RegistrationCancelReason;
import com.openhis.whale.booking.contracts.command.WalkInSlotAllocateCommand;

/**
 * 挂号写侧衔接 Port：回填 encounter、现场占号、退号释放。
 */
public interface BookingRegistrationPort {

    /** IF-REG-07：挂号成功后 Appointment 回填 encounterId。 */
    void linkEncounter(Long appointmentId, Long encounterId);

    /** 模式 C：现场挂号占当日 Slot。 */
    Long allocateWalkInSlot(WalkInSlotAllocateCommand command);

    /** 机构策略：挂号成功自动报到入队。 */
    void checkInAfterRegister(Long appointmentId, Long encounterId);

    /** IF-REG-10 退号后释放或取消预约关联。 */
    void onRegistrationCancelled(Long encounterId, RegistrationCancelReason reason);
}
