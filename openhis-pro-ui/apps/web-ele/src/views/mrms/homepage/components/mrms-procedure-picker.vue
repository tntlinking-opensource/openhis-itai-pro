<script setup lang="ts">
/**
 * 病案首页手术操作选码（ICD-9-CM-3 / 国临手术 3.0）。
 * 优先 mrmsCaseRecordCoding/searchIcd9Cm3；若空则回落医生站术式分页（打开即可浏览）。
 * 禁止手填术式名，须点选目录回填编码+名称。
 */
import type { IcdItemDto } from '#/api/mrms/types';

import { httpSearchClinicalProcV3 } from '#/api/inpatient/doctor-station/inpatient-doctor-station-surgery-anesth.http';
import { searchIcd9Cm3Api } from '#/api/mrms/case-record-coding';
import SelectTable from '#/components/select-table.vue';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'MrmsProcedurePicker' });

export interface ProcedurePickPayload {
  code: string;
  name: string;
  id?: string;
  ybNo?: string;
}

const props = withDefaults(
  defineProps<{
    /** 国临手术码（落库 oprnOprtCode） */
    modelValue?: string;
    /** 手术操作名称回显 */
    selectedLabel?: string;
    disabled?: boolean;
  }>(),
  {
    modelValue: '',
    selectedLabel: '',
    disabled: false,
  },
);

const emit = defineEmits<{
  'update:modelValue': [code: string];
  pick: [payload: ProcedurePickPayload];
  clear: [];
}>();

/** 下拉列：国临码 + 名称 + 医保对照提示 */
const columns = [
  { prop: 'code', label: '国临码', width: 120 },
  { prop: 'name', label: '手术操作名称', minWidth: 220 },
  { prop: 'ybNo', label: '医保码', width: 120 },
  { prop: 'pyStr', label: '拼音', width: 90 },
];

/**
 * 将选码行规范为下拉表格行。
 *
 * @param item ICD / 术式命中
 */
function mapPickRow(item: {
  id?: string;
  code?: string;
  name?: string;
  ybNo?: string;
  pyStr?: string;
  procedureCode?: string;
}) {
  const code = String(item.code ?? item.procedureCode ?? '').trim();
  const id = preserveApiIdForRequest(item.id) || code;
  return {
    ...item,
    id,
    code,
    name: String(item.name ?? '').trim(),
    ybNo: String(item.ybNo ?? '').trim(),
    pyStr: String(item.pyStr ?? '').trim(),
  };
}

/**
 * 远程分页检索手术目录（关键字可空，打开即可浏览）。
 *
 * @param params SelectTable 分页与关键字
 */
async function remoteMethod(params: {
  keyword?: string;
  pageNo: number;
  pageSize: number;
}) {
  const keyword = String(params.keyword ?? '').trim();
  const pageNo = params.pageNo || 1;
  const pageSize = params.pageSize || 20;

  // 1）病案编码接口（新版支持空关键字分页）
  try {
    const res = await searchIcd9Cm3Api({
      keyword: keyword || undefined,
      pageNo,
      pageSize,
    });
    // 兼容：新接口 { items, totalCount }；旧接口直接返回数组
    const raw = res as IcdItemDto[] | { items?: IcdItemDto[]; totalCount?: number };
    const list = Array.isArray(raw)
      ? raw
      : Array.isArray(raw?.items)
        ? raw.items
        : [];
    const content = list.map((item) => mapPickRow(item));
    const totalCount = Array.isArray(raw)
      ? content.length
      : (raw?.totalCount ?? content.length);
    if (content.length > 0 || totalCount > 0) {
      return { content, totalCount };
    }
  } catch {
    // 编码接口失败时继续回落医生站术式检索
  }

  // 2）回落：医生站术式分页（空关键字可浏览，与手术开立同目录）
  const fallback = await httpSearchClinicalProcV3({
    keyword: keyword || undefined,
    pageNo,
    pageSize,
  });
  const content = (fallback.items ?? []).map((item) =>
    mapPickRow({
      id: preserveApiIdForRequest(item.id) ?? undefined,
      procedureCode: item.procedureCode,
      name: item.name,
      ybNo: item.ybNo,
      pyStr: item.pyStr,
    }),
  );
  return { content, totalCount: fallback.totalCount ?? content.length };
}

/**
 * 点选行：回填国临码与名称（未对码也可选入，上报侧再阻断）。
 *
 * @param item 下拉命中行
 */
function onBeforeClose(item: Record<string, unknown>) {
  const code = String(item.code ?? '').trim();
  const name = String(item.name ?? '').trim();
  if (!code && !name) {
    return false;
  }
  emit('pick', {
    code,
    name,
    id: item.id != null ? String(item.id) : undefined,
    ybNo: String(item.ybNo ?? '').trim() || undefined,
  });
  emit('update:modelValue', code);
  return true;
}

/**
 * v-model 清空时同步清掉名称。
 *
 * @param value 选中编码
 */
function onModelUpdate(value: string | string[]) {
  const code = Array.isArray(value) ? String(value[0] ?? '') : String(value ?? '');
  if (!code.trim()) {
    emit('clear');
    emit('update:modelValue', '');
  }
}
</script>

<template>
  <SelectTable
    :model-value="props.modelValue"
    :selected-label="props.selectedLabel"
    :disabled="props.disabled"
    :remote-method="remoteMethod"
    :columns="columns"
    value-key="code"
    label-key="name"
    placeholder="输入编码/名称/拼音，或直接点选"
    :clearable="true"
    :page-size="20"
    panel-width="720"
    input-height="28"
    :before-close="onBeforeClose"
    @update:model-value="onModelUpdate"
  />
</template>
