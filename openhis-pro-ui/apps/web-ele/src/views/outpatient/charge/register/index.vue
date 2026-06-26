<script setup lang="ts">
import type { RegisterRightPanelExpose, RegisterSlotSelectionPayload } from './services';

import type { BookingEntityId } from '#/api/booking/booking-id';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { toBookingId } from '#/api/booking/booking-id';

import RegisterLeftPanel from './components/register-left-panel.vue';
import RegisterRightPanel from './components/register-right-panel.vue';

defineOptions({
  name: 'ChargeOpRegister',
});

const leftPanelRef = ref<null | {
  applySlotSelection: (payload: RegisterSlotSelectionPayload) => Promise<void>;
}>(null);
const rightPanelRef = ref<null | RegisterRightPanelExpose>(null);
const slotOrganizationId = ref<BookingEntityId | undefined>();
const slotPractitionerId = ref<BookingEntityId | undefined>();
const slotHealthcareServiceId = ref<BookingEntityId | undefined>();

/**
 * 左侧挂号结算成功后，刷新右侧统计与当前 Tab。
 */
async function handleRegisterSuccess() {
  await rightPanelRef.value?.refresh();
}

/** 当日号源点选：仅回填左侧挂号表单，不改右侧筛选条 */
async function handleSlotSelected(payload: RegisterSlotSelectionPayload) {
  await leftPanelRef.value?.applySlotSelection(payload);
}

/** 左侧科室变更时同步右侧号源查询科室 */
function handleDepartmentChange(organizationId: string) {
  slotOrganizationId.value = toBookingId(organizationId);
  slotPractitionerId.value = undefined;
  slotHealthcareServiceId.value = undefined;
}

/** 左侧医师变更时同步右侧号源筛选 */
function handleDoctorChange(practitionerId: string) {
  slotPractitionerId.value = toBookingId(practitionerId);
}

/** 左侧号源类型变更时同步右侧号源筛选 */
function handleServiceTypeChange(serviceTypeId: string) {
  slotHealthcareServiceId.value = toBookingId(serviceTypeId);
}
</script>

<template>
  <Page>
    <div class="flex h-full min-h-0 flex-1 flex-row gap-2 !overflow-hidden !p-2">
      <RegisterLeftPanel
        ref="leftPanelRef"
        @register-success="handleRegisterSuccess"
        @register-cancelled="handleRegisterSuccess"
        @department-change="handleDepartmentChange"
        @doctor-change="handleDoctorChange"
        @service-type-change="handleServiceTypeChange"
      />
      <RegisterRightPanel
        ref="rightPanelRef"
        :organization-id="slotOrganizationId"
        :practitioner-id="slotPractitionerId"
        :healthcare-service-id="slotHealthcareServiceId"
        @slot-selected="handleSlotSelected"
      />
    </div>
  </Page>
</template>
