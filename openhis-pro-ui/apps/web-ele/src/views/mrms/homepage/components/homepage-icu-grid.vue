<script setup lang="ts">
import type { IcuLineDto } from '#/api/mrms/types';
import type { ApiOptionItem } from '#/types/api-response';

import { onMounted, ref } from 'vue';

import { ElButton, ElDatePicker, ElInput, ElOption, ElSelect, ElTable, ElTableColumn } from 'element-plus';

import { getCodeListApi, getConceptListApi } from '#/api/basic/code';
import { TerminologyCodeSystemName } from '#/constants/domain/terminology-code-system';

import { HOMEPAGE_EMPTY_DISPLAY } from '../homepage.constants';
import { displayHomepageEmpty, newIcuRow } from '../homepage-form.util';

defineOptions({ name: 'HomepageIcuGrid' });

interface Props {
  readonly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false,
});

const rows = defineModel<IcuLineDto[]>('modelValue', { default: () => [] });

const emit = defineEmits<{
  dirty: [];
}>();

/** 老系统硬编码兜底（字典未种子时仍可录） */
const FALLBACK_ICU_OPTIONS: ApiOptionItem[] = [
  { label: 'CCU', value: '1' },
  { label: 'NICU', value: '2' },
  { label: 'ECU', value: '3' },
  { label: 'SICU', value: '4' },
  { label: 'PICU', value: '5' },
  { label: 'RICU', value: '6' },
  { label: 'ICU', value: '7' },
  { label: '其他', value: '9' },
];

const wardTypeOptions = ref<ApiOptionItem[]>([...FALLBACK_ICU_OPTIONS]);

/** 加载 ICU_TYPE 字典；失败则保留硬编码 */
onMounted(async () => {
  try {
    const response = await getCodeListApi({
      pageNo: 1,
      pageSize: 50,
      name: TerminologyCodeSystemName.ICU_TYPE,
    });
    const systems = Array.isArray(response)
      ? response
      : ((response as { content?: { id?: string; name?: string }[] })?.content ?? []);
    const system = systems.find((item) => item.name === TerminologyCodeSystemName.ICU_TYPE);
    if (!system?.id) {
      return;
    }
    const conceptResponse = await getConceptListApi({ id: system.id });
    const concepts = Array.isArray(conceptResponse)
      ? conceptResponse
      : ((conceptResponse as { data?: { code?: string; display?: string; isActive?: boolean }[] })
          ?.data ?? []);
    const mapped = concepts
      .filter((item) => item.isActive !== false)
      .map((item) => ({
        label: String(item.display ?? item.code ?? '').trim(),
        value: String(item.code ?? '').trim(),
      }))
      .filter((item) => item.value);
    if (mapped.length > 0) {
      wardTypeOptions.value = mapped;
    }
  } catch {
    // 保留 FALLBACK
  }
});

/** 通知父表单：ICU 子表已脏，需纳入保存 */
function markDirty() {
  emit('dirty');
}

/** 追加一行 ICU（默认 CCU + 时长 01/00/00） */
function handleAdd() {
  const nextSort = (rows.value?.length ?? 0) + 1;
  const list = [...(rows.value ?? [])];
  list.push(newIcuRow(nextSort));
  rows.value = list;
  markDirty();
}

/**
 * 删除/重排后按 1..n 重写 sortNo，供后端全量替换对齐。
 * @param list 当前 ICU 行
 */
function reindex(list: IcuLineDto[]) {
  list.forEach((row, i) => {
    row.sortNo = i + 1;
  });
}

/** 删除指定行并重排序号 */
function handleRemove(index: number) {
  const list = [...(rows.value ?? [])];
  list.splice(index, 1);
  reindex(list);
  rows.value = list;
  markDirty();
}
</script>

<template>
  <div class="hp-icu-grid">
    <div v-if="!props.readonly" class="hp-icu-grid__toolbar">
      <ElButton type="primary" size="small" @click="handleAdd">新增重症</ElButton>
    </div>
    <ElTable :data="rows" border size="small" empty-text="暂无重症监护记录">
      <ElTableColumn label="病房类型" min-width="120">
        <template #default="{ row }">
          <ElSelect
            v-if="!props.readonly"
            v-model="row.scsCutdWardType"
            clearable
            filterable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in wardTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <span v-else>{{ displayHomepageEmpty(row.scsCutdWardType) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="进入时间" min-width="180">
        <template #default="{ row }">
          <ElDatePicker
            v-if="!props.readonly"
            v-model="row.scsCutdInpoolTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
          <span v-else>{{ displayHomepageEmpty(row.scsCutdInpoolTime) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="退出时间" min-width="180">
        <template #default="{ row }">
          <ElDatePicker
            v-if="!props.readonly"
            v-model="row.scsCutdExitTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
          <span v-else>{{ displayHomepageEmpty(row.scsCutdExitTime) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="合计时长(天/时/分)" min-width="140">
        <template #default="{ row }">
          <ElInput
            v-if="!props.readonly"
            v-model="row.scsCutdSumDura"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
          <span v-else>{{ displayHomepageEmpty(row.scsCutdSumDura) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="!props.readonly" label="操作" width="80" fixed="right">
        <template #default="{ $index }">
          <ElButton link type="danger" size="small" @click="handleRemove($index)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>

<style scoped>
.hp-icu-grid__toolbar {
  margin-bottom: 8px;
}
</style>
