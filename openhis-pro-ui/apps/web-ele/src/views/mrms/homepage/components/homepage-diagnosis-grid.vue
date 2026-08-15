<script setup lang="ts">
/**
 * 病案首页诊断子表：新增/改选走 DiagnosisPickerDialog（pickOnly，不写就诊诊断）；
 * 仅随首页 saveHomepage 落库 mrms_homepage_diagnosis。
 */
import type { DiagnosisLineDto } from '#/api/mrms/types';
import type { DiagnosisConfirmPayload } from '#/components/doctor-station/diagnosis/services/diagnosis.types';

import { computed, ref } from 'vue';

import {
  ElButton,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { DiagnosisPickerDialog } from '#/components/doctor-station';

import { ADM_COND_OPTIONS, HOMEPAGE_DIAG_TYPES } from '../../mrms.constants';
import { newDiagnosisRow } from '../homepage-form.util';

defineOptions({ name: 'HomepageDiagnosisGrid' });

interface Props {
  readonly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false,
});

const rows = defineModel<DiagnosisLineDto[]>('modelValue', { default: () => [] });

const emit = defineEmits<{
  dirty: [];
}>();

const pickerVisible = ref(false);
/** 编辑已有行时指向下标；新增为 null */
const editIndex = ref<null | number>(null);

const typeOptions = computed(() =>
  HOMEPAGE_DIAG_TYPES.map((t) => ({ label: t.label, value: t.code })),
);

/** 标记脏数据 */
function markDirty() {
  emit('dirty');
}

/** 按当前数组下标重写 sortNo（1 起），保证保存顺序与界面一致 */
function reindexSortNo(list: DiagnosisLineDto[]) {
  // 首页诊断子表全量替换按 sortNo 落库
  list.forEach((row, i) => {
    row.sortNo = i + 1;
  });
}

/** 打开住院同款诊断目录弹窗（新增） */
function handleAdd() {
  editIndex.value = null;
  pickerVisible.value = true;
}

/** 打开弹窗改某一行的 ICD */
function handleOpenPickerForRow(index: number) {
  if (props.readonly) {
    return;
  }
  editIndex.value = index;
  pickerVisible.value = true;
}

/** 弹窗确认：新增一行或回填指定行编码/名称 */
function handlePickerConfirm(payload: DiagnosisConfirmPayload) {
  const code = (payload.selected?.code ?? '').trim();
  const name = (payload.selected?.name ?? '').trim();
  if (!code && !name) {
    ElMessage.warning('未选中有效诊断');
    return;
  }
  const list = [...(rows.value ?? [])];
  const idx = editIndex.value;
  // 改已有行：只更新编码名称
  if (idx != null && idx >= 0 && idx < list.length) {
    const row = list[idx];
    if (row) {
      row.diagCode = code;
      row.diagName = name;
    }
    rows.value = list;
    markDirty();
    editIndex.value = null;
    ElMessage.success('已更新，保存病案首页后生效');
    return;
  }
  // 新增：主诊勾选 → 出院主诊断，否则出院其他
  const diagType = payload.saveAsMainDiagnosis ? 'discharge_main' : 'discharge_other';
  const nextSort = list.length + 1;
  const row = newDiagnosisRow(diagType, nextSort);
  row.diagCode = code;
  row.diagName = name;
  list.push(row);
  rows.value = list;
  markDirty();
  editIndex.value = null;
  ElMessage.success('已加入列表，保存病案首页后生效');
}

/** 删除指定行并重排序号 */
function handleRemove(index: number) {
  const list = [...(rows.value ?? [])];
  list.splice(index, 1);
  reindexSortNo(list);
  rows.value = list;
  markDirty();
}

/**
 * 诊断行上移/下移（同表整体顺序；首行不可上、末行不可下）。
 *
 * @param index 当前行下标
 * @param direction -1 上移 / 1 下移
 */
function handleMove(index: number, direction: -1 | 1) {
  const list = [...(rows.value ?? [])];
  const target = index + direction;
  // 越界则忽略，避免首末行误操作
  if (target < 0 || target >= list.length) {
    return;
  }
  const current = list[index];
  const neighbor = list[target];
  if (!current || !neighbor) {
    return;
  }
  list[index] = neighbor;
  list[target] = current;
  reindexSortNo(list);
  rows.value = list;
  markDirty();
}
</script>

<template>
  <div class="hp-diag-grid">
    <div v-if="!props.readonly" class="mb-2 flex justify-end">
      <ElButton type="primary" size="small" @click="handleAdd">新增诊断</ElButton>
    </div>
    <ElTable :data="rows" border size="small">
      <ElTableColumn label="类型" width="150" align="center">
        <template #default="{ row }">
          <ElSelect
            v-model="row.diagTypeCode"
            size="small"
            :disabled="props.readonly"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in typeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </template>
      </ElTableColumn>
      <ElTableColumn label="编码" width="160" align="center">
        <template #default="{ row, $index }">
          <ElInput
            v-if="!props.readonly"
            :model-value="row.diagCode"
            size="small"
            readonly
            placeholder="点击选择"
            class="cursor-pointer"
            @click="handleOpenPickerForRow($index)"
          />
          <span v-else>{{ row.diagCode }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="诊断名称" min-width="200">
        <template #default="{ row, $index }">
          <ElInput
            v-if="!props.readonly"
            :model-value="row.diagName"
            size="small"
            readonly
            placeholder="点击选择"
            class="cursor-pointer"
            @click="handleOpenPickerForRow($index)"
          />
          <span v-else>{{ row.diagName }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="入院病情" width="140" align="center">
        <template #default="{ row }">
          <ElSelect
            v-model="row.admCondCode"
            clearable
            size="small"
            :disabled="props.readonly"
            placeholder="请选择"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in ADM_COND_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="!props.readonly" label="操作" width="180" align="center" fixed="right">
        <template #default="{ $index }">
          <ElButton link type="primary" :disabled="$index <= 0" @click="handleMove($index, -1)">
            上移
          </ElButton>
          <ElButton
            link
            type="primary"
            :disabled="$index >= (rows?.length ?? 0) - 1"
            @click="handleMove($index, 1)"
          >
            下移
          </ElButton>
          <ElButton link type="danger" @click="handleRemove($index)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <DiagnosisPickerDialog
      v-model="pickerVisible"
      :pick-only="true"
      station-scope="inpatient"
      @confirm="handlePickerConfirm"
    />
  </div>
</template>
