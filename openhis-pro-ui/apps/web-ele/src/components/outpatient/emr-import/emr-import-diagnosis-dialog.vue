<script setup lang="ts">
import type { DiagnosisImportItem } from './format-diagnosis-import-text';

import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElCheckbox,
  ElDialog,
  ElEmpty,
  ElMessage,
  ElScrollbar,
  ElTag,
} from 'element-plus';

import {
  fetchEncounterDiagnosisTabData,
  type DoctorStationDiagnosisScope,
} from '#/components/doctor-station/diagnosis/services/encounter-diagnosis.service';

import {
  buildDiagnosisImportItems,
  formatDiagnosisImportText,
} from './format-diagnosis-import-text';

defineOptions({ name: 'EmrImportDiagnosisDialog' });

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    patientId?: string;
    /** outpatient | inpatient */
    stationScope?: DoctorStationDiagnosisScope;
  }>(),
  {
    encounterId: '',
    patientId: '',
    stationScope: 'outpatient',
  },
);

const emit = defineEmits<{
  insert: [text: string];
}>();

const open = defineModel<boolean>('open', { default: false });

const loading = ref(false);
const items = ref<DiagnosisImportItem[]>([]);
const checkedKeys = ref<string[]>([]);

const allChecked = computed({
  get: () => items.value.length > 0 && checkedKeys.value.length === items.value.length,
  set: (v: boolean) => {
    checkedKeys.value = v ? items.value.map((i) => i.key) : [];
  },
});

const indeterminate = computed(
  () => checkedKeys.value.length > 0 && checkedKeys.value.length < items.value.length,
);

async function loadList() {
  const encounterId = props.encounterId.trim();
  const patientId = props.patientId.trim();
  if (!encounterId || !patientId) {
    items.value = [];
    checkedKeys.value = [];
    return;
  }
  loading.value = true;
  try {
    const data = await fetchEncounterDiagnosisTabData(
      { encounterId, patientId },
      props.stationScope,
    );
    const list = data?.displayDiagnosisList ?? data?.diagnosisList ?? [];
    items.value = buildDiagnosisImportItems(list);
    checkedKeys.value = items.value.map((i) => i.key);
    if (items.value.length === 0) {
      ElMessage.info('暂无诊断，请先在诊断页录入');
    }
  } catch {
    items.value = [];
    checkedKeys.value = [];
    ElMessage.error('加载诊断列表失败');
  } finally {
    loading.value = false;
  }
}

watch(open, (v) => {
  if (v) void loadList();
});

function toggleKey(key: string, checked: boolean) {
  if (checked) {
    if (!checkedKeys.value.includes(key)) {
      checkedKeys.value = [...checkedKeys.value, key];
    }
  } else {
    checkedKeys.value = checkedKeys.value.filter((k) => k !== key);
  }
}

function handleInsert() {
  const selected = items.value.filter((i) => checkedKeys.value.includes(i.key));
  if (selected.length === 0) {
    ElMessage.warning('请至少勾选一条诊断');
    return;
  }
  const text = formatDiagnosisImportText(selected);
  if (!text) {
    ElMessage.warning('没有可插入的内容');
    return;
  }
  emit('insert', text);
  open.value = false;
}
</script>

<template>
  <ElDialog
    v-model="open"
    title="插入诊断"
    width="560px"
    destroy-on-close
    append-to-body
    :close-on-click-modal="false"
  >
    <div v-loading="loading" class="emr-import-dx">
      <div class="emr-import-dx__head">
        <ElCheckbox v-model="allChecked" :indeterminate="indeterminate">全选</ElCheckbox>
        <span class="emr-import-dx__count">共 {{ items.length }} 条</span>
      </div>
      <ElScrollbar max-height="360px">
        <div v-if="items.length === 0 && !loading" class="emr-import-dx__empty">
          <ElEmpty description="暂无诊断，请先在诊断页录入" :image-size="64" />
        </div>
        <label v-for="item in items" :key="item.key" class="emr-import-dx__row">
          <ElCheckbox
            :model-value="checkedKeys.includes(item.key)"
            @change="(v: boolean | string | number) => toggleKey(item.key, Boolean(v))"
          />
          <ElTag size="small" :type="item.kind === 'TCM' ? 'success' : 'primary'" effect="plain">
            {{ item.kind === 'TCM' ? '中医' : '西医' }}
          </ElTag>
          <span v-if="item.code" class="emr-import-dx__code">{{ item.code }}</span>
          <span class="emr-import-dx__title">{{ item.title }}</span>
          <ElTag v-if="item.mainDiagnosis" size="small" type="danger" effect="plain">主诊</ElTag>
          <span v-if="item.iptDiseaseTypeText" class="emr-import-dx__type">
            {{ item.iptDiseaseTypeText }}
          </span>
        </label>
      </ElScrollbar>
    </div>
    <template #footer>
      <ElButton @click="open = false">取消</ElButton>
      <ElButton type="primary" :disabled="items.length === 0" @click="handleInsert">
        插入到光标
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.emr-import-dx__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.emr-import-dx__count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.emr-import-dx__empty {
  padding: 24px 0;
}

.emr-import-dx__row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  font-size: 13px;
  line-height: 1.4;
}

.emr-import-dx__code {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, monospace;
  font-size: 12px;
}

.emr-import-dx__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.emr-import-dx__type {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
