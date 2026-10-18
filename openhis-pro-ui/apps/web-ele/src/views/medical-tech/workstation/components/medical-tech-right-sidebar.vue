<script setup lang="ts">
import type { MedicalTechProcedureRecordDto } from '#/api/medical-tech/medical-tech-execution.types';
import type { SupplyItem } from '#/components/outpatient/supply-item-card.vue';

import { computed, inject } from 'vue';

import { ElButton, ElEmpty, ElMessage, ElTag } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import SupplyItemCard from '#/components/outpatient/supply-item-card.vue';

import { medicalTechWorkstationContextKey } from '../workbench-tab.config';

defineOptions({
  name: 'MedicalTechRightSidebar',
});

interface Props {
  canPrint?: boolean;
  loading?: boolean;
  recentRecords?: MedicalTechProcedureRecordDto[];
  selectedSupplyIds?: string[];
  supplyItems: SupplyItem[];
}

const props = withDefaults(defineProps<Props>(), {
  canPrint: false,
  loading: false,
  recentRecords: () => [],
  selectedSupplyIds: () => [],
});

const emit = defineEmits<{
  cancelSupply: [id: string];
  showAllRecords: [];
  supplyChange: [id: string, checked: boolean];
}>();

const ctx = inject(medicalTechWorkstationContextKey, null);

/** 住院医技站不展示耗材明细（耗材走医技计费）；仅门诊展示并默认全选 */
const showSupplySection = computed(() => ctx?.encounterScope.value !== 'INPATIENT');

/** 门诊空态提示 */
const supplyEmptyDescription = '暂无耗材明细';

/** 耗材勾选变更 */
function onSupplyChange(id: string, checked: boolean) {
  emit('supplyChange', id, checked);
}

/** 单卡取消执行 */
function onCancelSupply(id: string) {
  emit('cancelSupply', id);
}

/** 打印执行记录：场景码未配置时提示 */
function handlePrintProcedureRecords() {
  if (!props.canPrint) {
    ElMessage.warning('请先选择患者');
    return;
  }
  ElMessage.warning('执行记录打印场景待配置');
}

/** 状态 Tag 类型 */
function resolveStatusTagType(
  statusEnum: null | string | undefined,
): 'info' | 'success' | 'warning' {
  const code = String(statusEnum ?? '')
    .trim()
    .toUpperCase();
  if (code === 'COMPLETED') {
    return 'success';
  }
  if (code.includes('CANCEL')) {
    return 'info';
  }
  return 'warning';
}
</script>

<template>
  <div v-loading="props.loading" class="flex h-full min-h-0 flex-col">
    <template v-if="showSupplySection">
      <div class="bg-background px-2 py-1.5">
        <SectionTitle title="耗材明细" />
        <p class="mt-0.5 text-[11px] leading-snug text-[var(--el-text-color-regular)]">
          勾选「待发放」随批执行；已计费可点「取消执行」回退。
        </p>
      </div>
      <div class="max-h-[42%] min-h-0 flex-1 overflow-hidden px-2 pb-2">
        <div
          v-if="props.supplyItems.length === 0"
          class="flex h-full min-h-0 items-center justify-center"
        >
          <ElEmpty :description="supplyEmptyDescription" :image-size="64" />
        </div>
        <div
          v-else
          class="flex h-full min-h-0 flex-col gap-2 overflow-y-auto overflow-x-hidden pr-0.5"
        >
          <SupplyItemCard
            v-for="item in props.supplyItems"
            :key="item.id"
            :item="item"
            selectable
            :selected="props.selectedSupplyIds.includes(item.id)"
            @change="onSupplyChange"
            @cancel="onCancelSupply"
          />
        </div>
      </div>
    </template>

    <div class="bg-background px-2 py-1.5">
      <SectionTitle title="打印" />
    </div>
    <div class="mt-print-grid bg-background min-w-0 px-2 py-1.5">
      <ElButton
        type="primary"
        size="small"
        :disabled="!canPrint"
        @click="handlePrintProcedureRecords"
      >
        打印执行记录
      </ElButton>
    </div>

    <div class="bg-background flex min-h-0 flex-1 flex-col px-2 py-1.5">
      <div class="mb-1.5 flex shrink-0 items-center justify-between gap-2">
        <SectionTitle title="最近执行记录" class="min-w-0 flex-1" />
        <ElButton
          link
          type="primary"
          size="small"
          :disabled="!canPrint"
          @click="emit('showAllRecords')"
        >
          查看全部
        </ElButton>
      </div>
      <div class="record-mini min-h-0 flex-1 space-y-1 overflow-y-auto pr-0.5">
        <div
          v-for="record in props.recentRecords"
          :key="record.procedureId"
          class="rounded-md bg-[var(--el-fill-color-light)] px-2 py-1.5 text-[11px] text-[var(--el-text-color-regular)]"
        >
          <div
            class="flex items-center justify-between gap-1 text-[var(--el-text-color-secondary)]"
          >
            <span class="truncate">{{ record.recordedTime || '—' }}</span>
            <ElTag size="small" :type="resolveStatusTagType(record.statusEnum)">
              {{ record.actionDisplayText || '—' }}
            </ElTag>
          </div>
          <div class="mt-0.5 truncate font-medium text-[var(--el-text-color-primary)]">
            {{ record.orderItemDisplayName || '—' }}
          </div>
          <div
            v-if="record.operatorDisplayName"
            class="truncate text-[var(--el-text-color-secondary)]"
          >
            执行人：{{ record.operatorDisplayName }}
          </div>
        </div>
        <div
          v-if="props.recentRecords.length === 0"
          class="py-6 text-center text-xs text-[var(--el-text-color-secondary)]"
        >
          暂无记录
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mt-print-grid {
  display: grid;
  gap: 6px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.mt-print-grid :deep(.el-button) {
  margin: 0;
  width: 100%;
}
</style>
