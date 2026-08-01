<script setup lang="ts">
import type { AdviceImportItem } from './format-advice-import-text';

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

import { httpListDoctorStationAdvice } from '#/api/doctor-station/advice.http';
import { httpListInpatientDoctorStationAdvice } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import type { DoctorStationAdviceRowDto } from '#/api/doctor-station/advice.types';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import { buildAdviceImportItems, formatAdviceImportText } from './format-advice-import-text';

defineOptions({ name: 'EmrImportAdviceDialog' });

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    patientId?: string;
    /** outpatient | inpatient */
    stationScope?: 'inpatient' | 'outpatient';
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
const items = ref<AdviceImportItem[]>([]);
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
    // 医嘱 list 走 PagedResult.content（与医嘱 Tab 同源）；勿读已废弃的 rows
    const res =
      props.stationScope === 'inpatient'
        ? await httpListInpatientDoctorStationAdvice({ encounterId, patientId })
        : await httpListDoctorStationAdvice({ encounterId, patientId });
    const { content: rows } = parsePagedRowsFromApiData<DoctorStationAdviceRowDto>(res);
    items.value = buildAdviceImportItems(rows);
    checkedKeys.value = items.value.filter((i) => i.defaultChecked).map((i) => i.key);
    if (items.value.length === 0) {
      ElMessage.info('暂无医嘱');
    }
  } catch {
    items.value = [];
    checkedKeys.value = [];
    ElMessage.error('加载医嘱列表失败');
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

function statusTagType(status: string): 'danger' | 'info' | 'primary' | 'success' | 'warning' {
  const code = status.toUpperCase();
  if (code === 'DRAFT') return 'primary';
  if (code === 'ACTIVE' || code === 'COMPLETED') return 'success';
  if (code === 'STOPPED') return 'danger';
  if (code === 'HOLD' || code === 'VERIFIED') return 'warning';
  return 'info';
}

function handleInsert() {
  const selected = items.value.filter((i) => checkedKeys.value.includes(i.key));
  if (selected.length === 0) {
    ElMessage.warning('请至少勾选一条医嘱');
    return;
  }
  const text = formatAdviceImportText(selected);
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
    title="带入医嘱"
    width="640px"
    destroy-on-close
    append-to-body
    :close-on-click-modal="false"
  >
    <div v-loading="loading" class="emr-import-adv">
      <div class="emr-import-adv__head">
        <ElCheckbox v-model="allChecked" :indeterminate="indeterminate">全选</ElCheckbox>
        <span class="emr-import-adv__hint">默认含草稿与申请单；已停默认不勾选</span>
        <span class="emr-import-adv__count">共 {{ items.length }} 条</span>
      </div>
      <ElScrollbar max-height="380px">
        <div v-if="items.length === 0 && !loading" class="emr-import-adv__empty">
          <ElEmpty description="暂无医嘱" :image-size="64" />
        </div>
        <label v-for="item in items" :key="item.key" class="emr-import-adv__row">
          <ElCheckbox
            :model-value="checkedKeys.includes(item.key)"
            @change="(v: boolean | string | number) => toggleKey(item.key, Boolean(v))"
          />
          <ElTag size="small" :type="statusTagType(item.statusEnum)" effect="plain">
            {{ item.statusText }}
          </ElTag>
          <span class="emr-import-adv__body">
            <span class="emr-import-adv__title">{{ item.title }}</span>
            <span v-if="item.subtitle" class="emr-import-adv__sub">{{ item.subtitle }}</span>
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
.emr-import-adv__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  margin-bottom: 8px;
}

.emr-import-adv__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.emr-import-adv__count {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.emr-import-adv__empty {
  padding: 24px 0;
}

.emr-import-adv__row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  font-size: 13px;
  line-height: 1.4;
}

.emr-import-adv__body {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.emr-import-adv__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.emr-import-adv__sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
