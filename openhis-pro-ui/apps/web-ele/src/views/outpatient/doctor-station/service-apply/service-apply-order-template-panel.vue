<script setup lang="ts">
import type { ServiceApplyKind } from './service-apply.types';

import type { OrderTemplateHeaderItem } from '#/api/basic/order-template';
import type {
  OrderTemplateBindingScope,
  OrderTemplateTreeNode,
} from '#/views/outpatient/doctor-station/advice/services/order-template.service';

import { computed, ref, watch } from 'vue';

import { ElEmpty, ElInput, ElScrollbar, ElTree } from 'element-plus';

import {
  getOrderTemplateDetailApi,
  ORDER_TEMPLATE_KIND_SERVICE_APPLY,
  ORDER_TEMPLATE_SOURCE_MAINTAINED,
} from '#/api/basic/order-template';
import {
  buildOrderTemplateTreeFromGroups,
  fetchDoctorStationOrderTemplateGroups,
} from '#/views/outpatient/doctor-station/advice/services/order-template.service';

import { SERVICE_APPLY_CATEGORY_BY_KIND } from './service-apply.constants';

defineOptions({ name: 'ServiceApplyOrderTemplatePanel' });

/** 申请单组套不含协定处方 */
const EXCLUDED_BINDING_SCOPES: OrderTemplateBindingScope[] = ['AGREEMENT'];

interface Props {
  /** 当前申请单弹窗类型，只展示同类型组套 */
  applyKind?: ServiceApplyKind;
  active?: boolean;
  disabled?: boolean;
  encounterId?: string;
  patientId?: string;
}

const props = withDefaults(defineProps<Props>(), {
  applyKind: 'lab',
  active: false,
  disabled: false,
  encounterId: '',
  patientId: '',
});

const emit = defineEmits<{
  select: [template: OrderTemplateHeaderItem];
}>();

const searchKeyword = ref('');
const listLoading = ref(false);
const templateGroups = ref<Awaited<ReturnType<typeof fetchDoctorStationOrderTemplateGroups>>>();

/** 个人 / 科室 / 全院 */
const templateTreeData = computed<OrderTemplateTreeNode[]>(() =>
  buildOrderTemplateTreeFromGroups(templateGroups.value, {
    excludeBindingScopes: EXCLUDED_BINDING_SCOPES,
  }),
);

const hasTemplateNodes = computed(() =>
  templateTreeData.value.some((group) => (group.children?.length ?? 0) > 0),
);

let searchTimer: ReturnType<typeof setTimeout> | undefined;

/** 只保留与当前检查/检验/治疗弹窗一致的组套（类别在明细 groupKey） */
async function filterGroupsByApplyKind(
  groups: NonNullable<Awaited<ReturnType<typeof fetchDoctorStationOrderTemplateGroups>>>,
) {
  const categoryCode = SERVICE_APPLY_CATEGORY_BY_KIND[props.applyKind];
  const buckets = [
    groups.personalList,
    groups.departmentList,
    groups.hospitalList,
    groups.agreementList,
  ];
  const headers: OrderTemplateHeaderItem[] = [];
  for (const bucket of buckets) {
    for (const header of bucket ?? []) {
      headers.push(header);
    }
  }
  const matchedIds = new Set<string>();
  await Promise.all(
    headers.map(async (header) => {
      const orderTemplateId = String(header.orderTemplateId ?? '').trim();
      if (!orderTemplateId) {
        return;
      }
      try {
        const detail = await getOrderTemplateDetailApi({ orderTemplateId });
        const lineCategory = String(detail.lines?.[0]?.groupKey ?? '').trim();
        if (lineCategory === categoryCode) {
          matchedIds.add(orderTemplateId);
        }
      } catch {
        // 单条详情失败时不展示，避免把别的类型组套混进当前弹窗
      }
    }),
  );
  const keep = (list: OrderTemplateHeaderItem[] | undefined) =>
    (list ?? []).filter((header) => matchedIds.has(String(header.orderTemplateId ?? '').trim()));
  return {
    personalList: keep(groups.personalList),
    departmentList: keep(groups.departmentList),
    hospitalList: keep(groups.hospitalList),
    agreementList: keep(groups.agreementList),
  };
}

/** 拉取当前就诊、且与本弹窗类型一致的申请单组套 */
async function reloadTemplateList() {
  const encounterId = props.encounterId.trim();
  const patientId = props.patientId.trim();
  if (!encounterId || !patientId) {
    templateGroups.value = undefined;
    return;
  }
  listLoading.value = true;
  try {
    const groups = await fetchDoctorStationOrderTemplateGroups({
      encounterId,
      patientId,
      templateKindEnum: ORDER_TEMPLATE_KIND_SERVICE_APPLY,
      templateSourceEnum: ORDER_TEMPLATE_SOURCE_MAINTAINED,
      searchKey: searchKeyword.value.trim() || undefined,
    });
    templateGroups.value = groups ? await filterGroupsByApplyKind(groups) : undefined;
  } catch {
    templateGroups.value = undefined;
  } finally {
    listLoading.value = false;
  }
}

/** 搜索关键字防抖刷新 */
function scheduleReloadBySearch() {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    void reloadTemplateList();
  }, 300);
}

/** 点选组套，交给申请单弹窗按当前检查/检验/治疗套用 */
function handleTemplateTreeNodeClick(data: OrderTemplateTreeNode) {
  if (props.disabled || data.nodeType !== 'template' || !data.header) {
    return;
  }
  emit('select', data.header);
}

watch(
  () => [props.active, props.encounterId, props.patientId, props.applyKind] as const,
  ([active]) => {
    if (!active) {
      return;
    }
    void reloadTemplateList();
  },
  { immediate: true },
);

watch(searchKeyword, () => {
  if (!props.active) {
    return;
  }
  scheduleReloadBySearch();
});
</script>

<template>
  <section v-loading="listLoading" class="service-apply-template-panel">
    <ElInput v-model="searchKeyword" placeholder="搜索组套名称" clearable :disabled="disabled" />
    <div v-if="!listLoading && !hasTemplateNodes" class="service-apply-template-panel__empty">
      <ElEmpty description="暂无申请单组套" :image-size="72" />
    </div>
    <ElScrollbar v-else class="service-apply-template-panel__tree">
      <ElTree
        :data="templateTreeData"
        node-key="id"
        default-expand-all
        :expand-on-click-node="false"
        @node-click="handleTemplateTreeNodeClick"
      />
    </ElScrollbar>
  </section>
</template>

<style scoped>
.service-apply-template-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  padding: 8px;
}

.service-apply-template-panel__tree {
  flex: 1;
  min-height: 0;
}

.service-apply-template-panel__empty {
  align-items: center;
  display: flex;
  flex: 1;
  justify-content: center;
}
</style>
