<script setup lang="ts">
import { computed } from 'vue';

import { CopyDocument } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

defineOptions({
  name: 'InpatientPatientInfoHeader',
});

interface PatientInfoHeaderData {
  age?: number | string;
  admitAt?: string;
  bedNo?: string;
  diagnosis?: string;
  gender?: string;
  inpatientDays?: number | string;
  inpatientNo?: string;
  inWardAt?: string;
  insuranceType?: string;
  name?: string;
}

interface Props {
  sticky?: boolean;
  patientInfo?: PatientInfoHeaderData | null;
}

const props = withDefaults(defineProps<Props>(), {
  sticky: true,
  patientInfo: null,
});

/** 组合患者基础身份文案。 */
const patientIdentityText = computed(() => {
  const p = props.patientInfo;
  if (!p) {
    return '当前未选中患者';
  }
  const chunks: string[] = [];
  if (p.bedNo) {
    chunks.push(`${p.bedNo}床`);
  }
  if (p.name) {
    chunks.push(p.name);
  }
  if (p.gender) {
    chunks.push(p.gender);
  }
  if (p.age !== undefined && p.age !== null && `${p.age}`.length > 0) {
    chunks.push(`${p.age}岁`);
  }
  return chunks.length > 0 ? chunks.join(' ') : '当前未选中患者';
});

/** 生成住院阶段信息行。 */
const stageInfoList = computed(() => {
  const p = props.patientInfo;
  if (!p) {
    return [];
  }
  return [
    { label: '住院', value: p.inpatientDays ? `${p.inpatientDays}天` : '-' },
    { label: '入科', value: p.inWardAt || '-' },
    { label: '入院时间', value: p.admitAt || '-' },
  ];
});

/** 复制住院号到系统剪贴板。 */
async function handleCopyInpatientNo() {
  const inpatientNo = props.patientInfo?.inpatientNo;
  if (!inpatientNo) {
    ElMessage.warning('当前无可复制的住院号');
    return;
  }
  try {
    await navigator.clipboard.writeText(inpatientNo);
    ElMessage.success('住院号已复制');
  } catch {
    ElMessage.error('复制失败，请手动复制');
  }
}
</script>

<template>
  <div class="inpatient-patient-info-header" :class="{ 'is-sticky': sticky }">
    <div class="inpatient-patient-info-header__main">
      <span class="inpatient-patient-info-header__identity">{{ patientIdentityText }}</span>
      <ElTag
        v-if="patientInfo?.insuranceType"
        type="primary"
        effect="light"
        size="small"
        class="inpatient-patient-info-header__insurance-tag"
      >
        {{ patientInfo.insuranceType }}
      </ElTag>
    </div>

    <div class="inpatient-patient-info-header__stage">
      <span
        v-for="item in stageInfoList"
        :key="item.label"
        class="inpatient-patient-info-header__meta"
      >
        <span class="inpatient-patient-info-header__label">{{ item.label }}：</span>
        <span>{{ item.value }}</span>
      </span>
    </div>

    <div class="inpatient-patient-info-header__extra">
      <span class="inpatient-patient-info-header__meta">
        <span class="inpatient-patient-info-header__label">住院号：</span>
        <span>{{ patientInfo?.inpatientNo || '-' }}</span>
        <ElIcon
          v-if="patientInfo?.inpatientNo"
          class="inpatient-patient-info-header__copy"
          @click="handleCopyInpatientNo"
        >
          <CopyDocument />
        </ElIcon>
      </span>
      <span class="inpatient-patient-info-header__meta">
        <span class="inpatient-patient-info-header__label">诊断：</span>
        <span>{{ patientInfo?.diagnosis || '-' }}</span>
      </span>
    </div>
  </div>
</template>

<style scoped>
.inpatient-patient-info-header {
  align-items: center;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-light);
  color: var(--el-text-color-primary);
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-bottom: 8px;
  min-height: 42px;
  padding: 6px 12px;
}

.inpatient-patient-info-header.is-sticky {
  position: sticky;
  top: 0;
  z-index: 20;
}

.inpatient-patient-info-header__main,
.inpatient-patient-info-header__stage,
.inpatient-patient-info-header__extra {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.inpatient-patient-info-header__identity {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.2;
}

.inpatient-patient-info-header__meta {
  color: var(--el-text-color-primary);
  display: inline-flex;
  font-size: 14px;
  gap: 2px;
}

.inpatient-patient-info-header__label {
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.inpatient-patient-info-header__copy {
  color: var(--el-color-primary);
  cursor: pointer;
  margin-left: 2px;
}

.inpatient-patient-info-header__insurance-tag {
  --el-tag-bg-color: rgb(64 158 255 / 12%);
  --el-tag-border-color: rgb(64 158 255 / 28%);
  --el-tag-text-color: var(--el-color-primary);
}
</style>
