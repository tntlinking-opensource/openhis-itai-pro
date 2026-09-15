<script setup lang="ts">
/**
 * 病案首页诊断子表（按分组：入院 / 出院 / 病理损伤）。
 * 与父组件共用完整 diagnoses 数组，本组仅展示并回写对应 diagTypeCode 行。
 */
import type { DiagnosisLineDto } from '#/api/mrms/types';
import type { OptionDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station.types';
import type { DiagnosisConfirmPayload } from '#/components/doctor-station/diagnosis/services/diagnosis.types';

import { computed, ref } from 'vue';

import {
  ElButton,
  ElDatePicker,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { DiagnosisPickerDialog } from '#/components/doctor-station';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  ADM_COND_OPTIONS,
  HOMEPAGE_DIAG_GROUP_TYPES,
  HOMEPAGE_DIAG_TYPES,
  NEED_YB_UP_OPTIONS,
  OUT_ILL_STATUS_OPTIONS,
  type HomepageDiagGroup,
  type HomepageDiagTypeCode,
} from '../../mrms.constants';
import { HOMEPAGE_EMPTY_DISPLAY } from '../homepage.constants';
import { displayHomepageEmpty, newDiagnosisRow } from '../homepage-form.util';

defineOptions({ name: 'HomepageDiagnosisGrid' });

interface Props {
  /** 诊断分组：决定类型选项与列集合 */
  group: HomepageDiagGroup;
  readonly?: boolean;
  /** 分组标题（可选） */
  title?: string;
  /** 出院诊断医生下拉（入院科室出诊医生） */
  doctorOptions?: OptionDto[];
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false,
  title: '',
  doctorOptions: () => [],
});

/** 完整诊断子表（含入院/出院/病理损伤） */
const allRows = defineModel<DiagnosisLineDto[]>('modelValue', { default: () => [] });

const emit = defineEmits<{
  dirty: [];
}>();

const pickerVisible = ref(false);
/** 编辑已有行时指向「全表」下标；新增为 null */
const editIndex = ref<null | number>(null);

const groupTypeCodes = computed(() => HOMEPAGE_DIAG_GROUP_TYPES[props.group]);

const typeOptions = computed(() =>
  HOMEPAGE_DIAG_TYPES.filter((t) =>
    groupTypeCodes.value.includes(t.code as HomepageDiagTypeCode),
  ).map((t) => ({ label: t.label, value: t.code })),
);

/** 本组可见行（保持在全表中的相对顺序）；出院上传空值展示为「是」 */
const displayRows = computed(() => {
  const codes = new Set(groupTypeCodes.value);
  const rows = (allRows.value ?? []).filter((r) =>
    codes.has(r.diagTypeCode as HomepageDiagTypeCode),
  );
  // 回显缺省：出院诊断 needYbUp 空 → 1（是）
  if (props.group === 'discharge') {
    for (const row of rows) {
      if (!String(row.needYbUp ?? '').trim()) {
        row.needYbUp = '1';
      }
    }
  }
  return rows;
});

/** 标记脏数据 */
function markDirty() {
  emit('dirty');
}

/** 按全表顺序重写 sortNo（1 起） */
function reindexAll(list: DiagnosisLineDto[]) {
  list.forEach((row, i) => {
    row.sortNo = i + 1;
  });
}

/**
 * 合并：用本组新行替换全表中同组行，再按 入院→出院→特殊 稳定重排。
 *
 * @param groupList 本组最新行
 */
function commitGroupRows(groupList: DiagnosisLineDto[]) {
  const codes = new Set(groupTypeCodes.value);
  // 保留其他分组行
  const others = (allRows.value ?? []).filter(
    (r) => !codes.has(r.diagTypeCode as HomepageDiagTypeCode),
  );
  // 出院行上传空值补默认「是」
  if (props.group === 'discharge') {
    for (const row of groupList) {
      if (!String(row.needYbUp ?? '').trim()) {
        row.needYbUp = '1';
      }
    }
  }
  const nextAdmission =
    props.group === 'admission' ? groupList : filterByGroup(others, 'admission');
  const nextDischarge =
    props.group === 'discharge' ? groupList : filterByGroup(others, 'discharge');
  const nextSpecial = props.group === 'special' ? groupList : filterByGroup(others, 'special');
  const next = [...nextAdmission, ...nextDischarge, ...nextSpecial];
  reindexAll(next);
  allRows.value = next;
  markDirty();
}

/**
 * 从列表中筛出指定分组行。
 *
 * @param list 诊断行
 * @param group 分组
 */
function filterByGroup(list: DiagnosisLineDto[], group: HomepageDiagGroup): DiagnosisLineDto[] {
  const codes = new Set(HOMEPAGE_DIAG_GROUP_TYPES[group]);
  return list.filter((r) => codes.has(r.diagTypeCode as HomepageDiagTypeCode));
}

/** 本组默认新增类型（主诊勾选在确认回调里再覆盖） */
function defaultAddType(): HomepageDiagTypeCode {
  if (props.group === 'admission') {
    return 'admission_other';
  }
  if (props.group === 'special') {
    return 'injury_poison';
  }
  return 'discharge_other';
}

/** 打开诊断目录弹窗（新增） */
function handleAdd() {
  editIndex.value = null;
  pickerVisible.value = true;
}

/**
 * 打开弹窗改某一行 ICD（index 为本组 display 下标）。
 *
 * @param displayIndex 本组行下标
 */
function handleOpenPickerForRow(displayIndex: number) {
  if (props.readonly) {
    return;
  }
  const row = displayRows.value[displayIndex];
  if (!row) {
    return;
  }
  // 定位到全表下标，便于改码回写
  editIndex.value = (allRows.value ?? []).indexOf(row);
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
  const list = [...(allRows.value ?? [])];
  const idx = editIndex.value;
  // 改已有行：只更新编码名称
  if (idx != null && idx >= 0 && idx < list.length) {
    const row = list[idx];
    if (row) {
      row.diagCode = code;
      row.diagName = name;
    }
    allRows.value = list;
    markDirty();
    editIndex.value = null;
    ElMessage.success('已更新，保存病案首页后生效');
    return;
  }
  // 新增：主诊勾选 → 本组主诊断类型，否则本组其他
  let diagType: HomepageDiagTypeCode = defaultAddType();
  if (payload.saveAsMainDiagnosis) {
    if (props.group === 'admission') {
      diagType = 'admission_main';
    } else if (props.group === 'discharge') {
      diagType = 'discharge_main';
    }
  }
  const groupList = [...displayRows.value];
  const row = newDiagnosisRow(diagType, groupList.length + 1);
  row.diagCode = code;
  row.diagName = name;
  if (props.group === 'discharge') {
    row.needYbUp = '1';
  }
  groupList.push(row);
  commitGroupRows(groupList);
  editIndex.value = null;
  ElMessage.success('已加入列表，保存病案首页后生效');
}

/**
 * 删除本组指定行。
 *
 * @param displayIndex 本组行下标
 */
function handleRemove(displayIndex: number) {
  const groupList = [...displayRows.value];
  groupList.splice(displayIndex, 1);
  commitGroupRows(groupList);
}

/**
 * 本组内上移/下移。
 *
 * @param displayIndex 当前行下标
 * @param direction -1 上移 / 1 下移
 */
function handleMove(displayIndex: number, direction: -1 | 1) {
  const groupList = [...displayRows.value];
  const target = displayIndex + direction;
  if (target < 0 || target >= groupList.length) {
    return;
  }
  const current = groupList[displayIndex];
  const neighbor = groupList[target];
  if (!current || !neighbor) {
    return;
  }
  groupList[displayIndex] = neighbor;
  groupList[target] = current;
  commitGroupRows(groupList);
}

/**
 * 出院诊断医生下拉变更：写主键并冗余姓名。
 *
 * @param row 诊断行
 * @param practitionerId 选中医生主键
 */
function handleConfirmDoctorChange(row: DiagnosisLineDto, practitionerId: unknown) {
  const id = preserveApiIdForRequest(practitionerId) || '';
  row.confirmDrId = id;
  // 按选项回填姓名；清空时一并清名
  if (!id) {
    row.confirmDrName = '';
    markDirty();
    return;
  }
  const hit = props.doctorOptions.find((opt) => String(opt.value) === id);
  row.confirmDrName = hit?.label ?? row.confirmDrName ?? '';
  markDirty();
}
</script>

<template>
  <div class="hp-diag-grid" :class="{ 'hp-diag-grid--discharge': props.group === 'discharge' }">
    <div class="mb-2 flex items-center justify-between">
      <span v-if="props.title" class="hp-diag-grid__title">{{ props.title }}</span>
      <ElButton
        v-if="!props.readonly"
        type="primary"
        size="small"
        class="ml-auto"
        @click="handleAdd"
      >
        新增诊断
      </ElButton>
    </div>
    <div class="hp-diag-grid__scroll">
      <ElTable :data="displayRows" border size="small" empty-text="暂无数据">
        <ElTableColumn type="index" label="序号" width="52" align="center" />
        <ElTableColumn label="诊断类型" width="132" align="center">
          <template #default="{ row }">
            <ElSelect
              v-model="row.diagTypeCode"
              size="small"
              :disabled="props.readonly"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
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
        <ElTableColumn label="诊断名称" min-width="180">
          <template #default="{ row, $index }">
            <ElInput
              v-if="!props.readonly"
              :model-value="row.diagName"
              size="small"
              readonly
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
              class="cursor-pointer"
              @click="handleOpenPickerForRow($index)"
            />
            <span v-else>{{ displayHomepageEmpty(row.diagName) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="诊断编码" width="130" align="center">
          <template #default="{ row, $index }">
            <ElInput
              v-if="!props.readonly"
              :model-value="row.diagCode"
              size="small"
              readonly
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
              class="cursor-pointer"
              @click="handleOpenPickerForRow($index)"
            />
            <span v-else>{{ displayHomepageEmpty(row.diagCode) }}</span>
          </template>
        </ElTableColumn>

        <!-- 入院诊断：老系统「病情」= 有/未确定/不明/无（对应 admCond） -->
        <ElTableColumn v-if="props.group === 'admission'" label="病情" width="120" align="center">
          <template #default="{ row }">
            <ElSelect
              v-model="row.admCondCode"
              clearable
              size="small"
              :disabled="props.readonly"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
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

        <!-- 出院诊断：入院病情 + 病情(转归) + 诊断医生 + 上传 -->
        <ElTableColumn
          v-if="props.group === 'discharge'"
          label="入院病情"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <ElSelect
              v-model="row.admCondCode"
              clearable
              size="small"
              :disabled="props.readonly"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
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
        <ElTableColumn v-if="props.group === 'discharge'" label="病情" width="100" align="center">
          <template #default="{ row }">
            <ElSelect
              v-model="row.outIllStatusCode"
              clearable
              size="small"
              :disabled="props.readonly"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
              class="w-full"
              @change="markDirty"
            >
              <ElOption
                v-for="opt in OUT_ILL_STATUS_OPTIONS"
                :key="String(opt.value)"
                :label="opt.label"
                :value="String(opt.value)"
              />
            </ElSelect>
          </template>
        </ElTableColumn>
        <ElTableColumn
          v-if="props.group === 'discharge'"
          label="诊断医生"
          width="140"
          align="center"
        >
          <template #default="{ row }">
            <ElSelect
              v-if="!props.readonly"
              :model-value="row.confirmDrId || undefined"
              clearable
              filterable
              size="small"
              class="w-full"
              :placeholder="doctorOptions.length ? HOMEPAGE_EMPTY_DISPLAY : '无科室医生'"
              @change="(v) => handleConfirmDoctorChange(row, v)"
            >
              <ElOption
                v-for="opt in doctorOptions"
                :key="String(opt.value)"
                :label="opt.label"
                :value="String(opt.value)"
              />
            </ElSelect>
            <span v-else>{{ displayHomepageEmpty(row.confirmDrName) }}</span>
          </template>
        </ElTableColumn>

        <ElTableColumn v-if="props.group !== 'special'" label="确诊时间" width="178" align="center">
          <template #default="{ row }">
            <ElDatePicker
              v-if="!props.readonly"
              v-model="row.confirmTime"
              type="datetime"
              size="small"
              value-format="YYYY-MM-DD HH:mm:ss"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
              class="w-full"
              @change="markDirty"
            />
            <span v-else>{{ displayHomepageEmpty(row.confirmTime) }}</span>
          </template>
        </ElTableColumn>

        <ElTableColumn v-if="props.group === 'discharge'" label="上传" width="80" align="center">
          <template #default="{ row }">
            <ElSelect
              v-model="row.needYbUp"
              size="small"
              :disabled="props.readonly"
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
              class="w-full"
              @change="markDirty"
            >
              <ElOption
                v-for="opt in NEED_YB_UP_OPTIONS"
                :key="String(opt.value)"
                :label="opt.label"
                :value="String(opt.value)"
              />
            </ElSelect>
          </template>
        </ElTableColumn>

        <ElTableColumn label="说明" min-width="140">
          <template #default="{ row }">
            <ElInput
              v-if="!props.readonly"
              v-model="row.diagRemark"
              size="small"
              maxlength="200"
              clearable
              :placeholder="HOMEPAGE_EMPTY_DISPLAY"
              @change="markDirty"
            />
            <span v-else>{{ displayHomepageEmpty(row.diagRemark) }}</span>
          </template>
        </ElTableColumn>

        <ElTableColumn
          v-if="!props.readonly"
          label="操作"
          :width="props.group === 'discharge' ? 156 : 72"
          align="center"
          fixed="right"
        >
          <template #default="{ $index }">
            <ElButton
              v-if="props.group === 'discharge'"
              link
              type="primary"
              :disabled="$index <= 0"
              @click="handleMove($index, -1)"
            >
              上移
            </ElButton>
            <ElButton
              v-if="props.group === 'discharge'"
              link
              type="primary"
              :disabled="$index >= displayRows.length - 1"
              @click="handleMove($index, 1)"
            >
              下移
            </ElButton>
            <ElButton link type="danger" @click="handleRemove($index)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>

    <DiagnosisPickerDialog
      v-model="pickerVisible"
      :pick-only="true"
      station-scope="inpatient"
      @confirm="handlePickerConfirm"
    />
  </div>
</template>

<style scoped>
.hp-diag-grid__title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.hp-diag-grid__scroll {
  width: 100%;
  overflow-x: auto;
}

.hp-diag-grid--discharge .hp-diag-grid__scroll :deep(.el-table) {
  min-width: 1180px;
}
</style>
