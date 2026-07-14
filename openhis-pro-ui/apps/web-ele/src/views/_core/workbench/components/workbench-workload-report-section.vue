<!--
  首页工作量统计入口：点击打开全屏弹窗报表。
-->
<script setup lang="ts">
import { ref } from 'vue';

import { ElButton } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import NurseWorkloadPanel from '#/views/report/nurse-workload/nurse-workload-panel.vue';
import PractitionerWorkloadPanel from '#/views/report/practitioner-workload/practitioner-workload-panel.vue';

import WorkbenchStatCard from './workbench-stat-card.vue';

defineOptions({ name: 'WorkbenchWorkloadReportSection' });

const practitionerDialogVisible = ref(false);
const nurseDialogVisible = ref(false);

/** 打开科室/医生工作量弹窗。 */
function openPractitionerDialog() {
  practitionerDialogVisible.value = true;
}

/** 打开护士工作量弹窗。 */
function openNurseDialog() {
  nurseDialogVisible.value = true;
}

/** 关闭科室/医生工作量弹窗。 */
function closePractitionerDialog() {
  practitionerDialogVisible.value = false;
}
</script>

<template>
  <WorkbenchStatCard
    title="科室医生工作量"
    hint="门诊/住院 · 汇总与明细"
    icon="lucide:bar-chart-3"
    icon-gradient="linear-gradient(135deg, #bfdbfe 0%, #93c5fd 100%)"
    icon-color="#1d4ed8"
    card-tint="#eff6ff"
    accent-color="#2563eb"
    @click="openPractitionerDialog"
  />
  <WorkbenchStatCard
    title="护士工作量"
    hint="诊疗执行 · 耗材使用"
    icon="lucide:clipboard-list"
    icon-gradient="linear-gradient(135deg, #a7f3d0 0%, #6ee7b7 100%)"
    icon-color="#047857"
    card-tint="#ecfdf5"
    accent-color="#059669"
    @click="openNurseDialog"
  />

  <AppDialog
    v-model="practitionerDialogVisible"
    title="科室医生工作量统计"
    width="80vw"
    top="10vh"
    destroy-on-close
    class="workbench-workload-report__dialog"
  >
    <PractitionerWorkloadPanel embedded />

    <template #footer>
      <div class="workbench-workload-report__footer">
        <ElButton @click="closePractitionerDialog">关闭</ElButton>
      </div>
    </template>
  </AppDialog>

  <AppDialog
    v-model="nurseDialogVisible"
    title="护士工作量统计"
    width="80vw"
    top="10vh"
    destroy-on-close
    class="workbench-workload-report__dialog"
  >
    <NurseWorkloadPanel embedded />
  </AppDialog>
</template>

<style>
.workbench-workload-report__dialog.el-dialog {
  display: flex;
  width: 80vw !important;
  max-width: 80vw;
  height: 80vh;
  margin-top: 10vh !important;
  flex-direction: column;
  overflow: hidden;
  border-radius: 14px;
  background: #f0f4f8;
}

.workbench-workload-report__dialog .el-dialog__header {
  padding: 18px 24px 14px;
  margin-right: 0;
  background: transparent;
  border-bottom: none;
}

.workbench-workload-report__dialog .el-dialog__title {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--el-text-color-primary);
}

.workbench-workload-report__dialog .el-dialog__headerbtn {
  top: 18px;
  right: 20px;
  width: 32px;
  height: 32px;
}

.workbench-workload-report__dialog .el-dialog__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 0 24px 24px;
  overflow: hidden;
  background: transparent;
}

.workbench-workload-report__dialog .el-dialog__footer {
  flex-shrink: 0;
  padding: 0 24px 20px;
  background: transparent;
  border-top: none;
}

.workbench-workload-report__footer {
  display: flex;
  width: 100%;
  justify-content: flex-end;
}
</style>
