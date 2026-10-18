<script setup lang="ts">
import type { InpatientDoctorStationPatient } from '../doctorPatientStore';

import { computed } from 'vue';

import { ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus';

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

<style scoped>
.ip-session-tabs {
  align-items: center;
  background: #eef1f6;
  border-bottom: 1px solid var(--el-border-color);
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  min-height: 40px;
  overflow-x: auto;
  padding: 5px 8px 5px 0;
}

.ip-session-tabs__menu,
.ip-session-tabs__menu :deep(.el-tooltip__trigger) {
  display: inline-flex;
  flex-shrink: 0;
}

.ip-session-tabs__tab {
  align-items: center;
  background: #e5e7eb;
  border: 1px solid #c5cad3;
  border-radius: 8px;
  color: #303133;
  cursor: pointer;
  display: inline-flex;
  gap: 6px;
  min-height: 30px;
  padding: 0 12px 0 14px;
}

.ip-session-tabs__tab:hover {
  border-color: var(--el-color-primary-light-5);
}

.ip-session-tabs__tab.is-active {
  background: #fff;
  border-color: var(--el-color-primary-light-5);
}

.ip-session-tabs__bed {
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.ip-session-tabs__name {
  font-size: 15px;
  font-weight: 600;
  max-width: 6em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ip-session-tabs__close {
  border-radius: 4px;
  color: #303133;
  font-size: 14px;
  line-height: 1;
  opacity: 0.7;
  padding: 0 2px;
}

.ip-session-tabs__close:hover {
  background: rgb(0 0 0 / 8%);
  opacity: 1;
}

.ip-session-tabs__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  padding: 0 4px;
}
</style>
