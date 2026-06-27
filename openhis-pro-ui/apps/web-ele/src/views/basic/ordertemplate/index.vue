<script lang="ts" setup>
import type { OrderTemplateMaintenanceTreeNode } from './order-template-maintenance-tree';

import type {
  OrderTemplateHeaderItem,
  OrderTemplateInitResponse,
  PageMaintainedOrderTemplateParams,
} from '#/api/basic/order-template';

import { computed, nextTick, onActivated, onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElSpace,
  ElTabPane,
  ElTabs,
  ElTag,
  ElTree,
} from 'element-plus';

import {
  changeOrderTemplateLifecycleApi,
  initOrderTemplateMaintenanceApi,
  ORDER_TEMPLATE_BINDING_AGREEMENT,
  ORDER_TEMPLATE_BINDING_DEPARTMENT,
  ORDER_TEMPLATE_BINDING_HOSPITAL,
  ORDER_TEMPLATE_BINDING_PERSONAL,
  ORDER_TEMPLATE_KIND_GENERAL,
  ORDER_TEMPLATE_KIND_PRICING,
  ORDER_TEMPLATE_KIND_TCM,
  ORDER_TEMPLATE_LIFECYCLE_DELETE,
  ORDER_TEMPLATE_LIFECYCLE_PUBLISH,
  ORDER_TEMPLATE_LIFECYCLE_RETIRE,
  pageMaintainedOrderTemplatesApi,
} from '#/api/basic/order-template';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import EditPanel from './edit-panel.vue';
import {
  buildOrderTemplateMaintenanceTree,
  findOrderTemplateHeaderInTree,
} from './order-template-maintenance-tree';

defineOptions({ name: 'BasicOrderTemplate' });

const activeKind = ref<string>(ORDER_TEMPLATE_KIND_GENERAL);
const treeKeyword = ref('');
const templateHeaders = ref<OrderTemplateHeaderItem[]>([]);
const initOptions = ref<OrderTemplateInitResponse>({});
const selectedTemplateId = ref('');
const panelFormData = ref<Partial<OrderTemplateHeaderItem>>({
  templateKindEnum: ORDER_TEMPLATE_KIND_GENERAL,
});
const panelVersion = ref(0);
const treeRef = ref<InstanceType<typeof ElTree>>();
const editPanelRef = ref<InstanceType<typeof EditPanel> | null>(null);

const queryParams = ref<PageMaintainedOrderTemplateParams>({
  pageNo: 1,
  pageSize: 500,
  templateKindEnum: ORDER_TEMPLATE_KIND_GENERAL,
});

const kindTabOptions = computed(() => {
  if (initOptions.value.templateKindOptions?.length) {
    return initOptions.value.templateKindOptions;
  }
  return [
    { value: ORDER_TEMPLATE_KIND_GENERAL, label: '通用组套' },
    { value: ORDER_TEMPLATE_KIND_TCM, label: '中药组套' },
    { value: ORDER_TEMPLATE_KIND_PRICING, label: '划价组套' },
  ];
});

const isGeneralKind = computed(() => activeKind.value === ORDER_TEMPLATE_KIND_GENERAL);

/** 通用组套不展示协定处方绑定维度 */
const maintenanceBindingOptions = computed(() => {
  const options = initOptions.value.bindingOptions ?? [];
  if (!isGeneralKind.value) {
    return options;
  }
  return options.filter(
    (item) => String(item.value ?? '').trim() !== ORDER_TEMPLATE_BINDING_AGREEMENT,
  );
});

const treeData = computed(() =>
  buildOrderTemplateMaintenanceTree({
    headers: templateHeaders.value,
    bindingOptions: maintenanceBindingOptions.value,
    searchKeyword: treeKeyword.value,
  }),
);

const selectedHeader = computed(() =>
  findOrderTemplateHeaderInTree(treeData.value, selectedTemplateId.value),
);

const panelTitle = computed(() => {
  const name = String(selectedHeader.value?.name ?? panelFormData.value.name ?? '').trim();
  if (name) {
    return name;
  }
  return '组套维护';
});

/** 状态标签颜色 */
function statusTagType(statusEnum: string | undefined): 'info' | 'success' | 'warning' {
  if (statusEnum === 'ACTIVE') {
    return 'success';
  }
  if (statusEnum === 'DRAFT') {
    return 'warning';
  }
  return 'info';
}

const { run: runInit, loading: initLoading } = useRequest(initOrderTemplateMaintenanceApi, {
  manual: true,
  onSuccess: (data) => {
    initOptions.value = data ?? {};
  },
});

const { run: runPage, loading: pageLoading } = useRequest(pageMaintainedOrderTemplatesApi, {
  manual: true,
  onSuccess: (data) => {
    templateHeaders.value = data.content ?? [];
    syncSelectionAfterReload();
  },
});

const loading = computed(() => initLoading.value || pageLoading.value);

/** 重置右侧编辑区为空白 */
function resetEditorPanel() {
  selectedTemplateId.value = '';
  panelFormData.value = { templateKindEnum: activeKind.value };
  panelVersion.value += 1;
  void nextTick(() => {
    treeRef.value?.setCurrentKey?.(undefined);
  });
}

/** 加载当前种类下的组套头列表 */
function loadTemplateHeaders() {
  queryParams.value.templateKindEnum = activeKind.value;
  runPage(queryParams.value);
}

/** 刷新后保持选中项；不存在则回到空白编辑态 */
function syncSelectionAfterReload() {
  const currentId = preserveApiIdForRequest(selectedTemplateId.value);
  if (!currentId) {
    return;
  }
  const hit = findOrderTemplateHeaderInTree(treeData.value, currentId);
  if (!hit) {
    resetEditorPanel();
    return;
  }
  panelFormData.value = {
    ...hit,
    orderTemplateId: preserveApiIdForRequest(hit.orderTemplateId),
  };
  void nextTick(() => {
    treeRef.value?.setCurrentKey?.(`template-${currentId}`);
  });
}

/** 切换组套种类 Tab */
function handleKindChange(kind: number | string) {
  activeKind.value = String(kind);
  resetEditorPanel();
  loadTemplateHeaders();
}

/** 选中左侧树节点，加载到右侧编辑区 */
function handleTreeNodeClick(node: OrderTemplateMaintenanceTreeNode) {
  if (node.nodeType === 'group') {
    return;
  }
  const templateId = preserveApiIdForRequest(node.header?.orderTemplateId);
  if (!templateId) {
    return;
  }
  selectedTemplateId.value = templateId;
  panelFormData.value = {
    ...node.header,
    orderTemplateId: templateId,
  };
}

/** 按绑定类型保存当前组套内容 */
function handleSaveAsBinding(bindingEnum: string) {
  void editPanelRef.value?.submit?.(bindingEnum);
}

/** 清空右侧编辑区 */
function handleClearPanel() {
  resetEditorPanel();
}

/** 保存成功后刷新树并保持选中 */
function handlePanelSuccess(savedId?: string) {
  const nextId =
    preserveApiIdForRequest(savedId) || preserveApiIdForRequest(selectedTemplateId.value);
  if (nextId) {
    selectedTemplateId.value = nextId;
  }
  loadTemplateHeaders();
}

/** 变更组套生命周期 */
async function handleLifecycle(lifecycleActionCode: string, confirmMessage?: string) {
  const orderTemplateId = preserveApiIdForRequest(selectedTemplateId.value);
  if (!orderTemplateId) {
    ElMessage.warning('请先从左侧选择组套');
    return;
  }
  if (confirmMessage) {
    try {
      await ElMessageBox.confirm(confirmMessage, '确认操作', { type: 'warning' });
    } catch {
      return;
    }
  }
  try {
    await changeOrderTemplateLifecycleApi({ orderTemplateId, lifecycleActionCode });
    const msgMap: Record<string, string> = {
      [ORDER_TEMPLATE_LIFECYCLE_PUBLISH]: '发布成功',
      [ORDER_TEMPLATE_LIFECYCLE_RETIRE]: '已停用',
      [ORDER_TEMPLATE_LIFECYCLE_DELETE]: '删除成功',
    };
    ElMessage.success(msgMap[lifecycleActionCode] ?? '操作成功');
    if (lifecycleActionCode === ORDER_TEMPLATE_LIFECYCLE_DELETE) {
      resetEditorPanel();
    }
    loadTemplateHeaders();
  } catch {
    ElMessage.error('操作失败，请稍后重试');
  }
}

watch(treeKeyword, () => {
  void nextTick(() => {
    treeRef.value?.setCurrentKey?.(
      selectedTemplateId.value ? `template-${selectedTemplateId.value}` : undefined,
    );
  });
});

onMounted(async () => {
  await runInit();
  loadTemplateHeaders();
});

onActivated(() => {
  loadTemplateHeaders();
});
</script>

<template>
  <Page auto-content-height>
    <div class="order-template-page flex h-full min-h-0 flex-col gap-2">
      <ElTabs :model-value="activeKind" class="shrink-0" @tab-change="handleKindChange">
        <ElTabPane
          v-for="item in kindTabOptions"
          :key="item.value"
          :label="item.label"
          :name="item.value"
        />
      </ElTabs>

      <div class="order-template-page__body flex min-h-0 flex-1 gap-3">
        <aside
          class="order-template-page__aside flex w-72 min-w-[240px] shrink-0 flex-col gap-2 border-r border-[var(--el-border-color-light)] pr-2"
        >
          <ElInput v-model="treeKeyword" clearable placeholder="搜索组套名称" class="w-full" />

          <div v-loading="loading" class="min-h-0 flex-1 overflow-auto">
            <ElTree
              ref="treeRef"
              :data="treeData"
              node-key="id"
              :props="{ label: 'label', children: 'children' }"
              highlight-current
              default-expand-all
              :expand-on-click-node="false"
              @node-click="handleTreeNodeClick"
            />
          </div>
        </aside>

        <section class="order-template-page__main flex min-w-0 flex-1 flex-col gap-2">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex flex-wrap items-center gap-2">
              <span class="font-semibold text-[var(--el-text-color-primary)]">
                {{ panelTitle }}
              </span>
              <ElTag
                v-if="selectedHeader?.statusEnum"
                :type="statusTagType(selectedHeader.statusEnum)"
              >
                {{ selectedHeader?.statusEnumText || selectedHeader?.statusEnum }}
              </ElTag>
            </div>
            <ElSpace wrap>
              <ElButton
                type="primary"
                @click="handleSaveAsBinding(ORDER_TEMPLATE_BINDING_DEPARTMENT)"
              >
                存科室
              </ElButton>
              <ElButton
                type="primary"
                @click="handleSaveAsBinding(ORDER_TEMPLATE_BINDING_PERSONAL)"
              >
                存个人
              </ElButton>
              <ElButton
                type="primary"
                @click="handleSaveAsBinding(ORDER_TEMPLATE_BINDING_HOSPITAL)"
              >
                存全院
              </ElButton>
              <ElButton
                v-if="!isGeneralKind"
                type="primary"
                @click="handleSaveAsBinding(ORDER_TEMPLATE_BINDING_AGREEMENT)"
              >
                存协方
              </ElButton>
              <ElButton @click="handleClearPanel">清空</ElButton>
              <ElButton
                v-if="selectedTemplateId && selectedHeader?.statusEnum === 'DRAFT'"
                type="primary"
                plain
                @click="handleLifecycle(ORDER_TEMPLATE_LIFECYCLE_PUBLISH)"
              >
                发布
              </ElButton>
              <ElButton
                v-if="selectedTemplateId && selectedHeader?.statusEnum === 'ACTIVE'"
                type="warning"
                plain
                @click="
                  handleLifecycle(
                    ORDER_TEMPLATE_LIFECYCLE_RETIRE,
                    '停用后不可再被调用，确定停用吗？',
                  )
                "
              >
                停用
              </ElButton>
              <ElButton
                v-if="selectedTemplateId"
                type="danger"
                plain
                @click="handleLifecycle(ORDER_TEMPLATE_LIFECYCLE_DELETE, '确定删除该组套吗？')"
              >
                删除
              </ElButton>
            </ElSpace>
          </div>

          <EditPanel
            ref="editPanelRef"
            :key="`${activeKind}-${panelVersion}`"
            class="min-h-0 flex-1"
            :form-data="panelFormData"
            :template-kind-enum="activeKind"
            :init-options="initOptions"
            @success="handlePanelSuccess"
          />
        </section>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.order-template-page {
  height: 100%;
}

.order-template-page__body {
  min-height: 0;
}

.order-template-page__aside :deep(.el-tree-node__content) {
  height: 32px;
}

.order-template-page__main {
  min-height: 0;
}
</style>
