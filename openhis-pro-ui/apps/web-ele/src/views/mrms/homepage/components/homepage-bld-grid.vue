<script setup lang="ts">
import type { BldLineDto } from '#/api/mrms/types';
import type { ApiOptionItem } from '#/types/api-response';

import { onMounted, ref } from 'vue';

import { ElButton, ElInput, ElOption, ElSelect, ElTable, ElTableColumn } from 'element-plus';

import { getCodeListApi, getConceptListApi } from '#/api/basic/code';
import { TerminologyCodeSystemName } from '#/constants/domain/terminology-code-system';

import { HOMEPAGE_EMPTY_DISPLAY } from '../homepage.constants';
import { displayHomepageEmpty, newBldRow } from '../homepage-form.util';

defineOptions({ name: 'HomepageBldGrid' });

interface Props {
  readonly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false,
});

const rows = defineModel<BldLineDto[]>('modelValue', { default: () => [] });

const emit = defineEmits<{
  dirty: [];
}>();

const catOptions = ref<ApiOptionItem[]>([]);

/** 加载输血品种字典 bld_cat_code */
onMounted(async () => {
  try {
    const response = await getCodeListApi({
      pageNo: 1,
      pageSize: 50,
      name: TerminologyCodeSystemName.BLD_CAT_CODE,
    });
    const systems = Array.isArray(response)
      ? response
      : ((response as { content?: { id?: string; name?: string }[] })?.content ?? []);
    const system = systems.find((item) => item.name === TerminologyCodeSystemName.BLD_CAT_CODE);
    if (!system?.id) {
      return;
    }
    const conceptResponse = await getConceptListApi({ id: system.id });
    const concepts = Array.isArray(conceptResponse)
      ? conceptResponse
      : ((conceptResponse as { data?: { code?: string; display?: string; isActive?: boolean }[] })
          ?.data ?? []);
    catOptions.value = concepts
      .filter((item) => item.isActive !== false)
      .map((item) => ({
        label: String(item.display ?? item.code ?? '').trim(),
        value: String(item.code ?? '').trim(),
      }))
      .filter((item) => item.value);
  } catch {
    catOptions.value = [];
  }
});

/** 通知父表单：输血子表已脏，需纳入保存 */
function markDirty() {
  emit('dirty');
}

/** 追加一行输血（默认单位 ml） */
function handleAdd() {
  const nextSort = (rows.value?.length ?? 0) + 1;
  const list = [...(rows.value ?? [])];
  list.push(newBldRow(nextSort));
  rows.value = list;
  markDirty();
}

/**
 * 删除/重排后按 1..n 重写 sortNo，供后端全量替换对齐。
 * @param list 当前输血行
 */
function reindex(list: BldLineDto[]) {
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
  <div class="hp-bld-grid">
    <div v-if="!props.readonly" class="hp-bld-grid__toolbar">
      <ElButton type="primary" size="small" @click="handleAdd">新增输血</ElButton>
    </div>
    <ElTable :data="rows" border size="small" empty-text="暂无输血记录">
      <ElTableColumn label="输血品种" min-width="160">
        <template #default="{ row }">
          <ElSelect
            v-if="!props.readonly"
            v-model="row.bldCat"
            clearable
            filterable
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          >
            <ElOption
              v-for="opt in catOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <span v-else>{{ displayHomepageEmpty(row.bldCat) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="输血量" min-width="100">
        <template #default="{ row }">
          <ElInput
            v-if="!props.readonly"
            v-model="row.bldAmt"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
          <span v-else>{{ displayHomepageEmpty(row.bldAmt) }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="计量单位" min-width="100">
        <template #default="{ row }">
          <ElInput
            v-if="!props.readonly"
            v-model="row.bldUnt"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
            @change="markDirty"
          />
          <span v-else>{{ displayHomepageEmpty(row.bldUnt) }}</span>
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
.hp-bld-grid__toolbar {
  margin-bottom: 8px;
}
</style>
