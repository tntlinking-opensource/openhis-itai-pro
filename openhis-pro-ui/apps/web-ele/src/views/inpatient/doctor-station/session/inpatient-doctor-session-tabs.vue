<script setup lang="ts">
import type { InpatientDoctorStationPatient } from '../doctorPatientStore';

import { computed } from 'vue';

import { ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus';

import '#/views/inpatient/doctor-station/session/inpatient-doctor-session-tabs.css';
import {
  buildDoctorSessionBedLabel,
  buildDoctorSessionPatientName,
  buildDoctorSessionTabTooltip,
  resolveDoctorSessionEncounterId,
} from './map-inpatient-doctor-session';

defineOptions({
  name: 'InpatientDoctorSessionTabs',
});

const props = withDefaults(
  defineProps<{
    sessions?: InpatientDoctorStationPatient[];
    activeEncounterId?: string;
  }>(),
  {
    sessions: () => [],
    activeEncounterId: '',
  },
);

const emit = defineEmits<{
  activate: [patient: InpatientDoctorStationPatient];
  close: [patient: InpatientDoctorStationPatient];
  closeAll: [];
  closeOthers: [patient: InpatientDoctorStationPatient];
}>();

const tabViews = computed(() => {
  const activeId = resolveDoctorSessionEncounterId(props.activeEncounterId);
  return props.sessions.map((patient) => {
    const encounterId = resolveDoctorSessionEncounterId(patient);
    return {
      patient,
      encounterId,
      active: encounterId === activeId,
      bed: buildDoctorSessionBedLabel(patient),
      name: buildDoctorSessionPatientName(patient),
      tooltip: buildDoctorSessionTabTooltip(patient),
    };
  });
});

function handleCommand(command: string, patient: InpatientDoctorStationPatient) {
  if (command === 'close') {
    emit('close', patient);
    return;
  }
  if (command === 'closeOthers') {
    emit('closeOthers', patient);
    return;
  }
  if (command === 'closeAll') {
    emit('closeAll');
  }
}
</script>

<template>
  <div class="ip-session-tabs">
    <ElDropdown
      v-for="tab in tabViews"
      :key="tab.encounterId"
      trigger="contextmenu"
      class="ip-session-tabs__menu"
      @command="(cmd) => handleCommand(String(cmd), tab.patient)"
    >
      <div
        class="ip-session-tabs__tab"
        :class="{ 'is-active': tab.active }"
        :title="tab.tooltip"
        @click="emit('activate', tab.patient)"
      >
        <span class="ip-session-tabs__bed">{{ tab.bed }}</span>
        <span class="ip-session-tabs__name">{{ tab.name }}</span>
        <span class="ip-session-tabs__close" title="关闭" @click.stop="emit('close', tab.patient)">
          ×
        </span>
      </div>
      <template #dropdown>
        <ElDropdownMenu>
          <ElDropdownItem command="close">关闭当前</ElDropdownItem>
          <ElDropdownItem command="closeOthers" :disabled="sessions.length <= 1">
            关闭其他
          </ElDropdownItem>
          <ElDropdownItem command="closeAll">关闭全部</ElDropdownItem>
        </ElDropdownMenu>
      </template>
    </ElDropdown>
    <span v-if="sessions.length === 0" class="ip-session-tabs__hint">
      左侧点患者即可打开，互不影响
    </span>
  </div>
</template>
