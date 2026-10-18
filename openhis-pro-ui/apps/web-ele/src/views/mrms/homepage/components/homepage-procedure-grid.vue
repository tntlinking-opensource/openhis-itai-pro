<script setup lang="ts">
import type { ProcedureLineDto } from '#/api/mrms/types';
import type { ApiOptionItem } from '#/types/api-response';
import type { ProcedurePickPayload } from './mrms-procedure-picker.vue';

import { onMounted, ref } from 'vue';

import {
  ElButton,
  ElDatePicker,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { MAIN_OPRN_FLAG_OPTIONS } from '../../mrms.constants';
import { HOMEPAGE_EMPTY_DISPLAY } from '../homepage.constants';
import { displayHomepageEmpty, newProcedureRow } from '../homepage-form.util';
import { loadHomepageProcedureOptions } from '../services/load-homepage-procedure-options';
import MrmsProcedurePicker from './mrms-procedure-picker.vue';

defineOptions({ name: 'HomepageProcedureGrid' });

interface Props {
  readonly?: boolean;
  /** 住院就诊主键；用于拉麻醉室医师下拉 */
  encounterId?: string;
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false,
  encounterId: '',
});

const rows = defineModel<ProcedureLineDto[]>('modelValue', { default: () => [] });

const emit = defineEmits<{
  dirty: [];
}>();

const levelOptions = ref<ApiOptionItem[]>([]);
const incisionHealOptions = ref<ApiOptionItem[]>([]);
const anesthesiaMethodOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const nurseOptions = ref<ApiOptionItem[]>([]);
const anesthetistOptions = ref<ApiOptionItem[]>([]);

/** 进页加载字典 + 术者/护士/麻醉室医师下拉 */
onMounted(async () => {
  try {
    const bundle = await loadHomepageProcedureOptions(props.encounterId);
    levelOptions.value = bundle.levelOptions;
    incisionHealOptions.value = bundle.incisionHealOptions;
    anesthesiaMethodOptions.value = bundle.anesthesiaMethodOptions;
    practitionerOptions.value = bundle.practitionerOptions;
    nurseOptions.value = bundle.nurseOptions;
    anesthetistOptions.value = bundle.anesthetistOptions;
  } catch {
    levelOptions.value = [];
    incisionHealOptions.value = [];
    anesthesiaMethodOptions.value = [];
    practitionerOptions.value = [];
    nurseOptions.value = [];
    anesthetistOptions.value = [];
  }
});

/** 标记脏数据 */
function markDirty() {
  emit('dirty');
}

/** 新增手术行 */
function handleAdd() {
  const nextSort = (rows.value?.length ?? 0) + 1;
  const list = [...(rows.value ?? [])];
  list.push(newProcedureRow(nextSort));
  rows.value = list;
  markDirty();
}

/** 按当前顺序重写 sortNo（1 起） */
function reindex(list: ProcedureLineDto[]) {
  list.forEach((row, i) => {
    row.sortNo = i + 1;
  });
}

/** 删除手术行 */
function handleRemove(index: number) {
  const list = [...(rows.value ?? [])];
  list.splice(index, 1);
  reindex(list);
  rows.value = list;
  markDirty();
}

/**
 * 上移/下移手术行（与诊断出院组同构）。
 *
 * @param index 当前行下标
 * @param direction -1 上移 / 1 下移
 */
function handleMove(index: number, direction: -1 | 1) {
  const list = [...(rows.value ?? [])];
  const target = index + direction;
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
  reindex(list);
  rows.value = list;
  markDirty();
}

/**
 * 手术时间变更：同步 oprnDate（YYYY-MM-DD），供 4401 日期列。
 *
 * @param row 手术行
 */
function handleOprnTimeChange(row: ProcedureLineDto) {
  const raw = String(row.oprnTime ?? '').trim();
  if (raw.length >= 10) {
    row.oprnDate = raw.slice(0, 10);
  }
  markDirty();
}

/**
 * 点选国临手术码：同步编码与名称，禁止手填术式。
 *
 * @param row 手术行
 * @param payload 选码结果
 */
function handleProcedurePick(row: ProcedureLineDto, payload: ProcedurePickPayload) {
  row.oprnOprtCode = payload.code;
  row.oprnOprtName = payload.name;
  markDirty();
}

/**
 * 清空手术选码。
 *
 * @param row 手术行
 */
function handleProcedureClear(row: ProcedureLineDto) {
  row.oprnOprtCode = '';
  row.oprnOprtName = '';
  markDirty();
}

/**
 * 主手术「是/否」变更：落库 1/0；选「是」时其余行自动改「否」（至多一条主手术）。
 *
 * @param row 当前行
 * @param value 选中值 1/0
 */
function handleMainOprnChange(row: ProcedureLineDto, value: string) {
  const flag = String(value ?? '').trim();
  row.mainOprnFlag = flag === '1' ? '1' : '0';
  // 选「是」时清空其他行主手术，避免保存被 EX-MRMS-HP-PR-203 拦截
  if (row.mainOprnFlag === '1') {
    for (const other of rows.value ?? []) {
      if (other !== row) {
        other.mainOprnFlag = '0';
      }
    }
  }
  markDirty();
}

/**
 * 切口类别变更：与手术申请同源组合码；同步愈合列供 4401 sinc_heal_lv_code。
 *
 * @param row 手术行
 * @param value 选中码
 */
function handleIncisionChange(row: ProcedureLineDto, value: null | string | undefined) {
  const code = String(value ?? '').trim();
  row.oprnIncisionCode = code;
  row.healGradeCode = code;
  markDirty();
}

/**
 * 愈合等级变更：终改真源；同步切口列保持与申请/导入口径一致。
 *
 * @param row 手术行
 * @param value 选中码
 */
function handleHealChange(row: ProcedureLineDto, value: null | string | undefined) {
  const code = String(value ?? '').trim();
  row.healGradeCode = code;
  row.oprnIncisionCode = code;
  markDirty();
}

/**
 * 选项码 → 展示名；未知码回落原文（兼容历史手填）。
 *
 * @param code 字典码或姓名
 * @param options 下拉
 */
function displayOptionLabel(code?: string, options: ApiOptionItem[] = []): string {
  const raw = String(code ?? '').trim();
  if (!raw) {
    return HOMEPAGE_EMPTY_DISPLAY;
  }
  const hit = options.find((opt) => String(opt.value) === raw);
  if (hit?.label) {
    return hit.label;
  }
  return raw;
}

/**
 * 下拉选项：历史手填值不在字典时临时插入，避免回显空白。
 *
 * @param current 当前值
 * @param options 基础选项
 */
function optionsWithCurrent(current: string | undefined, options: ApiOptionItem[]): ApiOptionItem[] {
  const raw = String(current ?? '').trim();
  if (!raw) {
    return options;
  }
  if (options.some((opt) => String(opt.value) === raw)) {
    return options;
  }
  return [{ label: raw, value: raw }, ...options];
}

/**
 * 主手术只读展示：1→是，0→否。
 *
 * @param flag 主手术标志
 */
function displayMainOprn(flag?: string): string {
  if (flag === '1') {
    return '是';
  }
  if (flag === '0') {
    return '否';
  }
  return displayHomepageEmpty(flag);
}
</script>

<template>
  <div class="hp-proc-grid">
    <div v-if="!props.readonly" class="mb-2 flex justify-end">
      <ElButton type="primary" size="small" @click="handleAdd">新增手术</ElButton>
    </div>
    <ElTable :data="rows" border size="small">
      <ElTableColumn type="index" label="序号" width="52" align="center" />
      <ElTableColumn label="编码" width="140" align="center">
        <template #default="{ row }">
          <span>{{ displayHomepageEmpty(row.oprnOprtCode) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="手术操作名称" min-width="220">
        <template #default="{ row }">
          <MrmsProcedurePicker
            v-if="!props.readonly"
            :model-value="row.oprnOprtCode"
            :selected-label="row.oprnOprtName"
            @pick="(payload) => handleProcedurePick(row, payload)"
            @clear="handleProcedureClear(row)"
          />
          <span v-else>{{ displayHomepageEmpty(row.oprnOprtName) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="手术时间" width="178" align="center">
        <template #default="{ row }">
          <ElDatePicker
            v-model="row.oprnTime"
            type="datetime"
            size="small"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            style="width: 100%"
            @change="handleOprnTimeChange(row)"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="麻醉开始" width="178" align="center">
        <template #default="{ row }">
          <ElDatePicker
            v-model="row.anstStartTime"
            type="datetime"
            size="small"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            style="width: 100%"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="麻醉结束" width="178" align="center">
        <template #default="{ row }">
          <ElDatePicker
            v-model="row.anstEndTime"
            type="datetime"
            size="small"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled="props.readonly"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            style="width: 100%"
            @change="markDirty"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="级别" width="110" align="center">
        <template #default="{ row }">
          <ElSelect
            v-if="!props.readonly"
            v-model="row.oprnLevelCode"
            size="small"
            clearable
            filterable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in optionsWithCurrent(row.oprnLevelCode, levelOptions)"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
          <span v-else>{{ displayOptionLabel(row.oprnLevelCode, levelOptions) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="切口" width="168" align="center">
        <template #default="{ row }">
          <ElSelect
            v-if="!props.readonly"
            :model-value="row.oprnIncisionCode"
            size="small"
            clearable
            filterable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            class="w-full"
            @change="(v: string) => handleIncisionChange(row, v)"
          >
            <ElOption
              v-for="opt in optionsWithCurrent(row.oprnIncisionCode, incisionHealOptions)"
              :key="'inc-' + opt.value"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
          <span v-else>{{ displayOptionLabel(row.oprnIncisionCode, incisionHealOptions) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="愈合" width="168" align="center">
        <template #default="{ row }">
          <ElSelect
            v-if="!props.readonly"
            :model-value="row.healGradeCode"
            size="small"
            clearable
            filterable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            class="w-full"
            @change="(v: string) => handleHealChange(row, v)"
          >
            <ElOption
              v-for="opt in optionsWithCurrent(row.healGradeCode, incisionHealOptions)"
              :key="'heal-' + opt.value"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
          <span v-else>{{ displayOptionLabel(row.healGradeCode, incisionHealOptions) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="麻醉" width="120" align="center">
        <template #default="{ row }">
          <ElSelect
            v-if="!props.readonly"
            v-model="row.anstWayCode"
            size="small"
            clearable
            filterable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in optionsWithCurrent(row.anstWayCode, anesthesiaMethodOptions)"
              :key="'anst-' + opt.value"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
          <span v-else>{{ displayOptionLabel(row.anstWayCode, anesthesiaMethodOptions) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="术者" width="110" align="center">
        <template #default="{ row }">
          <ElSelect
            v-if="!props.readonly"
            v-model="row.operDrName"
            size="small"
            clearable
            filterable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in optionsWithCurrent(row.operDrName, practitionerOptions)"
              :key="'op-' + opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <span v-else>{{ displayHomepageEmpty(row.operDrName) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="一助" width="110" align="center">
        <template #default="{ row }">
          <ElSelect
            v-if="!props.readonly"
            v-model="row.asit1Name"
            size="small"
            clearable
            filterable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in optionsWithCurrent(row.asit1Name, practitionerOptions)"
              :key="'a1-' + opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <span v-else>{{ displayHomepageEmpty(row.asit1Name) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="二助" width="110" align="center">
        <template #default="{ row }">
          <ElSelect
            v-if="!props.readonly"
            v-model="row.asit2Name"
            size="small"
            clearable
            filterable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in optionsWithCurrent(row.asit2Name, practitionerOptions)"
              :key="'a2-' + opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <span v-else>{{ displayHomepageEmpty(row.asit2Name) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="护士" width="110" align="center">
        <template #default="{ row }">
          <ElSelect
            v-if="!props.readonly"
            v-model="row.nurseName"
            size="small"
            clearable
            filterable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in optionsWithCurrent(row.nurseName, nurseOptions)"
              :key="'n-' + opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <span v-else>{{ displayHomepageEmpty(row.nurseName) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="麻醉医师" width="110" align="center">
        <template #default="{ row }">
          <ElSelect
            v-if="!props.readonly"
            v-model="row.anstDrName"
            size="small"
            clearable
            filterable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            class="w-full"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in optionsWithCurrent(row.anstDrName, anesthetistOptions)"
              :key="'an-' + opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <span v-else>{{ displayHomepageEmpty(row.anstDrName) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="主手术" width="88" align="center">
        <template #default="{ row }">
          <ElSelect
            v-if="!props.readonly"
            :model-value="row.mainOprnFlag || '0'"
            size="small"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            class="w-full"
            @change="(v: string) => handleMainOprnChange(row, v)"
          >
            <ElOption
              v-for="opt in MAIN_OPRN_FLAG_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="String(opt.value)"
            />
          </ElSelect>
          <span v-else>{{ displayMainOprn(row.mainOprnFlag) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="!props.readonly" label="操作" width="156" align="center" fixed="right">
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
  </div>
</template>
