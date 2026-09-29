<script setup lang="ts">
import type { MedicalTechProcedureRecordDto } from '#/api/medical-tech/medical-tech-execution.types';

import { ElButton, ElDrawer, ElEmpty, ElPagination, ElTag } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';

defineOptions({
  name: 'MedicalTechProcedureRecordDrawer',
});

interface Props {
  loading?: boolean;
  pageNo?: number;
  pageSize?: number;
  records: MedicalTechProcedureRecordDto[];
  total?: number;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  pageNo: 1,
  pageSize: 20,
  total: 0,
});

const emit = defineEmits<{
  pageChange: [page: number];
}>();

const open = defineModel<boolean>('open', { required: true });

/** 抽屉内翻页 */
function handlePageChange(page: number) {
  emit('pageChange', page);
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
  <ElDrawer v-model="open" title="执行记录" size="42%">
    <div v-loading="props.loading" class="flex h-full min-h-0 flex-col">
      <SectionTitle title="记录明细" class="mb-2 shrink-0" />
      <div
        class="bg-muted/60 text-muted-foreground grid grid-cols-[140px_minmax(0,1fr)_72px_80px] gap-2 rounded-md px-2 py-2 text-xs"
      >
        <div>时间</div>
        <div>项目</div>
        <div>操作</div>
        <div>操作人</div>
      </div>
      <div class="min-h-0 flex-1 overflow-y-auto">
        <template v-if="props.records.length === 0">
          <ElEmpty description="暂无执行记录" :image-size="72" class="py-10" />
        </template>
        <div
          v-for="record in props.records"
          :key="record.procedureId"
          class="grid grid-cols-[140px_minmax(0,1fr)_72px_80px] gap-2 border-b border-[var(--el-border-color-lighter)] px-2 py-2 text-xs"
        >
          <div class="truncate">{{ record.recordedTime || '—' }}</div>
          <div class="truncate" :title="record.orderItemDisplayName || ''">
            {{ record.orderItemDisplayName || '—' }}
          </div>
          <div>
            <ElTag size="small" :type="resolveStatusTagType(record.statusEnum)">
              {{ record.actionDisplayText || record.statusEnum || '—' }}
            </ElTag>
          </div>
          <div class="truncate">{{ record.operatorDisplayName || '—' }}</div>
        </div>
      </div>
      <div
        v-if="props.total > 0"
        class="flex shrink-0 justify-end border-t border-[var(--el-border-color-lighter)] pt-2"
      >
        <ElPagination
          :current-page="props.pageNo"
          :page-size="props.pageSize"
          :total="props.total"
          layout="total, prev, pager, next"
          small
          background
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </ElDrawer>
</template>
