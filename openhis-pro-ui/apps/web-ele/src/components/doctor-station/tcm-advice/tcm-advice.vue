<script setup lang="ts">
import type { AdviceDrugItem, AdviceEditForm } from '#/views/outpatient/doctor-station/advice';

import { computed, reactive, ref } from 'vue';

import { ElButton, ElMessage, ElMessageBox, ElTable, ElTableColumn } from 'element-plus';

import { AdviceEditCard } from '#/views/outpatient/doctor-station/advice';
import TcmAdviceCollapsed from './components/tcm-advice-collapsed.vue';
import TcmAdviceDetailForm from './components/tcm-advice-detail-form.vue';

defineOptions({
  name: 'OutpatientDoctorStationTcmAdvice',
});

interface PrescriptionTemplate {
  category: 'agreement' | 'common' | 'history';
  herbs: string[];
  id: string;
  itemCount: number;
  name: string;
}

interface PrescriptionItem {
  days: number;
  dosage: number;
  expanded: boolean;
  frequency: string;
  groupNo: string;
  id: string;
  medicineName: string;
  price: number;
  selectedId: string;
  selectedDrugName: string;
  selectedDrugPrice: number;
  selectedDrugSpec: string;
  /** 与编辑卡 tcmSpecialDecoction 对应。 */
  specialDecoction?: string;
  singleDose: number;
  specification: string;
  totalDose: number;
  usage: string;
}

interface ProductOption {
  availableQuantity?: number | string;
  category: string;
  code: string;
  id: string;
  name: string;
  price?: number;
  spec: string;
}

type PrescriptionStatus = 'draft' | 'issued' | 'revoked' | 'saved';

interface TcmPrescription {
  days: number;
  decoctionMethod: string;
  dosageCount: number;
  groupNo: string;
  id: string;
  items: PrescriptionItem[];
  name: string;
  note: string;
  status: PrescriptionStatus;
  usage: string;
}

interface TcmTableRow {
  id: string;
  isEditor?: boolean;
  medicineName?: string;
  price?: number;
  singleDose?: number;
}

const templateKeyword = ref('');
const activePrescriptionId = ref('rx-1');
const selectedTemplateNodeId = ref('');

interface TemplateTreeNode {
  category?: PrescriptionTemplate['category'];
  children?: TemplateTreeNode[];
  herbSummary?: string;
  id: string;
  itemCount?: number;
  label: string;
  nodeType: 'group' | 'template';
  templateId?: string;
}

const templateList = ref<PrescriptionTemplate[]>([
  {
    id: 'tpl-1',
    category: 'common',
    name: '感冒清肺方',
    itemCount: 8,
    herbs: ['金银花10g', '连翘10g', '薄荷6g', '桔梗10g', '荆芥10g', '甘草6g'],
  },
  {
    id: 'tpl-2',
    category: 'common',
    name: '健脾养胃方',
    itemCount: 6,
    herbs: ['党参10g', '白术10g', '茯苓12g', '炙甘草6g', '陈皮8g', '山药12g'],
  },
  {
    id: 'tpl-3',
    category: 'common',
    name: '安神化痰方',
    itemCount: 7,
    herbs: ['酸枣仁12g', '远志6g', '茯神10g', '半夏10g', '陈皮8g', '竹茹10g'],
  },
  {
    id: 'tpl-4',
    category: 'agreement',
    name: '协定止咳方',
    itemCount: 9,
    herbs: ['麻黄6g', '杏仁10g', '石膏15g', '甘草6g', '前胡10g', '桔梗10g'],
  },
  {
    id: 'tpl-5',
    category: 'agreement',
    name: '协定调经方',
    itemCount: 10,
    herbs: ['当归10g', '川芎8g', '白芍12g', '熟地12g', '香附10g', '丹参12g'],
  },
  {
    id: 'tpl-6',
    category: 'history',
    name: '历史清热方',
    itemCount: 6,
    herbs: ['黄芩10g', '栀子10g', '连翘10g', '竹叶8g', '甘草6g', '芦根12g'],
  },
  {
    id: 'tpl-7',
    category: 'history',
    name: '历史疏肝方',
    itemCount: 7,
    herbs: ['柴胡10g', '香附10g', '白芍12g', '陈皮8g', '枳壳10g', '甘草6g'],
  },
]);

const prescriptions = ref<TcmPrescription[]>([
  {
    id: 'rx-1',
    name: '中药处方1',
    status: 'draft',
    groupNo: 'G001',
    dosageCount: 7,
    usage: '',
    days: 7,
    decoctionMethod: '水煎至200ml',
    note: '饭后温服',
    items: [
      {
        id: 'item-1',
        selectedId: 'tcm-1',
        medicineName: '金银花',
        selectedDrugName: '金银花',
        selectedDrugSpec: '10g/袋',
        selectedDrugPrice: 14,
        specification: '10g/袋',
        dosage: 10,
        singleDose: 10,
        expanded: true,
        usage: '',
        frequency: '',
        days: 7,
        totalDose: 7,
        price: 14,
        groupNo: 'G001',
        specialDecoction: '',
      },
      {
        id: 'item-2',
        selectedId: 'tcm-2',
        medicineName: '连翘',
        selectedDrugName: '连翘',
        selectedDrugSpec: '10g/袋',
        selectedDrugPrice: 12,
        specification: '10g/袋',
        dosage: 10,
        singleDose: 10,
        expanded: false,
        usage: '',
        frequency: '',
        days: 7,
        totalDose: 7,
        price: 12,
        groupNo: 'G001',
        specialDecoction: '',
      },
    ],
  },
  {
    id: 'rx-2',
    name: '中药处方2',
    status: 'saved',
    groupNo: 'G002',
    dosageCount: 5,
    usage: '',
    days: 5,
    decoctionMethod: '常规煎煮',
    note: '早晚服',
    items: [],
  },
  {
    id: 'rx-3',
    name: '中药处方3',
    status: 'issued',
    groupNo: 'G003',
    dosageCount: 10,
    usage: '',
    days: 10,
    decoctionMethod: '文火慢煎',
    note: '遵医嘱',
    items: [],
  },
]);

/** 左侧树分组顺序：固定为协定、常用、历史。 */
const templateGroupMeta: Array<{ category: PrescriptionTemplate['category']; label: string }> = [
  { category: 'agreement', label: '协定处方' },
  { category: 'common', label: '常用处方' },
  { category: 'history', label: '历史处方' },
];

/** 生成左侧树：父节点固定，子节点按关键字过滤处方名。 */
const templateTreeData = computed<TemplateTreeNode[]>(() => {
  const keyword = templateKeyword.value.trim();
  return templateGroupMeta.map((group) => {
    const children = templateList.value
      .filter((item) => {
        if (item.category !== group.category) {
          return false;
        }
        if (!keyword) {
          return true;
        }
        return item.name.includes(keyword);
      })
      .map<TemplateTreeNode>((item) => ({
        id: `template-${item.id}`,
        label: item.name,
        nodeType: 'template',
        templateId: item.id,
        itemCount: item.itemCount,
        category: item.category,
        herbSummary: item.herbs.join('，'),
      }));
    return {
      id: `group-${group.category}`,
      label: group.label,
      nodeType: 'group',
      category: group.category,
      children,
    };
  });
});

/** 当前激活处方：统一作为右侧表单与明细表的数据源。 */
const activePrescription = computed(() => {
  return prescriptions.value.find((item) => item.id === activePrescriptionId.value) ?? null;
});

/** 当前处方金额小计：按药品明细累加金额。 */
const currentPrescriptionAmount = computed(() => {
  return activePrescription.value?.items.reduce((sum, item) => sum + item.price, 0) ?? 0;
});

/** 当前处方头部表单：与 activePrescription 双向同步的中间态。 */
const prescriptionForm = reactive({
  dosageCount: 0,
  usage: '',
  days: 0,
  decoctionMethod: '',
  note: '',
});

const productColumns = [
  { prop: 'code', label: '项目编码', width: 120 },
  { prop: 'name', label: '项目名称', width: 200 },
  { prop: 'category', label: '类别', width: 72 },
  { prop: 'spec', label: '规格', width: 100 },
  { prop: 'availableQuantity', label: '库存数量', width: 88 },
  { prop: 'price', label: '单价', width: 72 },
];

const mockOrderOptions = ref<ProductOption[]>([
  {
    id: 'tcm-1',
    code: 'TCM0001',
    name: '金银花',
    category: '药品',
    spec: '10g/袋',
    price: 14,
    availableQuantity: 200,
  },
  {
    id: 'tcm-2',
    code: 'TCM0002',
    name: '连翘',
    category: '药品',
    spec: '10g/袋',
    price: 12,
    availableQuantity: 180,
  },
  {
    id: 'tcm-3',
    code: 'TCM0003',
    name: '薄荷',
    category: '药品',
    spec: '6g/袋',
    price: 10,
    availableQuantity: 150,
  },
  {
    id: 'tcm-4',
    code: 'TCM0004',
    name: '桔梗',
    category: '药品',
    spec: '10g/袋',
    price: 11,
    availableQuantity: 160,
  },
]);

/** 与西药医嘱页面编辑卡片对齐的表单模型种子。 */
const createTcmEditForm = (): AdviceEditForm => ({
  orderType: '',
  selectedId: '',
  diagnosis: '',
  feeNature: '',
  pharmacy: '',
  singleDose: 0,
  frequency: '',
  usage: '',
  medicationDays: 0,
  totalAmount: 0,
});

const showTcmEditor = ref(false);
const tcmEditorDirty = ref(false);
const tcmEditorForm = ref<AdviceEditForm>(createTcmEditForm());
const tcmEditorInitialGroup = ref<AdviceDrugItem[]>([]);

/** 中医明细表数据：编辑态时将编辑卡片作为首行插入。 */
const tcmTableRows = computed<TcmTableRow[]>(() => {
  const items = activePrescription.value?.items ?? [];
  if (!showTcmEditor.value || !activePrescription.value) {
    return items;
  }
  return [
    {
      id: '__editor__',
      isEditor: true,
    },
    ...items,
  ];
});

/** 当前有未保存的中药编辑卡片时阻断切换等操作。 */
function assertEditorClean(): boolean {
  if (showTcmEditor.value && tcmEditorDirty.value) {
    ElMessage.warning('编辑卡片中有未保存数据，请先保存或关闭');
    return false;
  }
  return true;
}

/** 编辑卡片保存后写回明细，条目组号一律等于当前处方 groupNo。 */
function mapAdviceToPrescriptionItems(
  rx: TcmPrescription,
  drugs: AdviceDrugItem[],
): PrescriptionItem[] {
  const ts = Date.now();
  const groupNo = rx.groupNo;
  return drugs.map((d, idx) => {
    const priceNum = Number(d.selectedDrugPrice || 0);
    const matched = rx.items.find((it) => it.selectedId === d.selectedId) ?? rx.items[idx];
    return {
      id: matched?.id ?? `item-${ts}-${idx}`,
      selectedId: String(d.selectedId || ''),
      medicineName: d.selectedDrugName,
      selectedDrugName: d.selectedDrugName,
      selectedDrugSpec: String(d.selectedDrugSpec || ''),
      selectedDrugPrice: priceNum,
      specification: String(d.selectedDrugSpec || ''),
      singleDose: d.singleDose,
      dosage: d.singleDose,
      expanded: false,
      usage: String(d.usage ?? '').trim(),
      frequency: String(d.frequency ?? '').trim(),
      days: Number.isFinite(d.medicationDays) ? Math.trunc(d.medicationDays) : 0,
      totalDose: Number(d.totalAmount) || 0,
      price: priceNum,
      groupNo,
      specialDecoction: d.tcmSpecialDecoction ?? '',
    };
  });
}

/** 解析模板药品文本（如“金银花10g”）为带入明细字段。 */
function parseTemplateHerb(herbText: string) {
  const text = herbText.trim();
  const normalized = text.toLowerCase();
  const gIndex = normalized.lastIndexOf('g');
  if (gIndex <= 0) {
    return { medicineName: text, dosage: 0 };
  }
  const dosageText = text.slice(0, gIndex).trim();
  const dosageParts = dosageText.split(' ');
  const dosageToken = dosageParts[dosageParts.length - 1] ?? '';
  const dosage = Number(dosageToken);
  if (Number.isNaN(dosage)) {
    return { medicineName: text, dosage: 0 };
  }
  const medicineName = dosageText
    .slice(0, Math.max(0, dosageText.length - dosageToken.length))
    .trim();
  return { medicineName: medicineName || text, dosage };
}

/** 载入当前处方头部字段，避免直接改写源对象。 */
function loadActivePrescriptionForm() {
  if (!activePrescription.value) {
    prescriptionForm.dosageCount = 0;
    prescriptionForm.usage = '';
    prescriptionForm.days = 0;
    prescriptionForm.decoctionMethod = '';
    prescriptionForm.note = '';
    return;
  }
  prescriptionForm.dosageCount = activePrescription.value.dosageCount;
  prescriptionForm.usage = activePrescription.value.usage;
  prescriptionForm.days = activePrescription.value.days;
  prescriptionForm.decoctionMethod = activePrescription.value.decoctionMethod;
  prescriptionForm.note = activePrescription.value.note;
}

/** 项目检索：传给 AdviceEditCard，与医嘱页一致。 */
async function queryProductOptions(params: { pageNo: number; pageSize: number; keyword?: string }) {
  const keyword = (params.keyword || '').trim().toLowerCase();
  const filtered = keyword
    ? mockOrderOptions.value.filter(
        (item) =>
          item.name.toLowerCase().includes(keyword) ||
          item.code.toLowerCase().includes(keyword) ||
          item.category.toLowerCase().includes(keyword),
      )
    : mockOrderOptions.value;
  return {
    content: filtered,
    totalElements: filtered.length,
  };
}

/** 切换处方标签时重载右侧头部表单。 */
function handleSwitchPrescription(id: string) {
  if (!assertEditorClean()) return;
  activePrescriptionId.value = id;
  loadActivePrescriptionForm();
  handleTcmEditorClose();
}

/** 打开与医嘱相同的编辑卡片；新增入口不预填当前处方已有药品。 */
function handleOpenTcmEditor() {
  if (!activePrescription.value) return;
  if (showTcmEditor.value && tcmEditorDirty.value) {
    ElMessage.warning('编辑卡片中有未保存数据，请先保存或关闭');
    return;
  }
  tcmEditorInitialGroup.value = [];
  tcmEditorForm.value = createTcmEditForm();
  showTcmEditor.value = true;
}

/** 关闭中药编辑卡片。 */
function handleTcmEditorClose() {
  showTcmEditor.value = false;
  tcmEditorDirty.value = false;
  tcmEditorInitialGroup.value = [];
}

/** 保存编辑卡片明细到当前处方。 */
function handleTcmSaveGroup(items: AdviceDrugItem[]) {
  if (!activePrescription.value || items.length === 0) return;
  activePrescription.value.items = mapAdviceToPrescriptionItems(activePrescription.value, items);
  handleTcmEditorClose();
  ElMessage.success('已保存药品明细');
}

/** 编辑卡片脏标记，用于切换处方前拦截。 */
function handleTcmEditorDirtyChange(dirty: boolean) {
  tcmEditorDirty.value = dirty;
}

function validateTcmRow(item: AdviceDrugItem) {
  return Boolean(
    item.selectedId && item.singleDose > 0 && String(item.pharmacy || '').trim().length > 0,
  );
}

/** 编辑卡片首行横跨整表，其余列隐藏。 */
function handleTcmTableSpanMethod({ columnIndex, row }: { columnIndex: number; row: TcmTableRow }) {
  if (!row.isEditor) return undefined;
  if (columnIndex === 0) return { rowspan: 1, colspan: 999 };
  return { rowspan: 0, colspan: 0 };
}

/** 为编辑卡片容器列追加专用 class。 */
function getTcmTableCellClassName({ columnIndex, row }: { columnIndex: number; row: TcmTableRow }) {
  if (row.isEditor && columnIndex === 0) return 'tcm-editor-cell';
  return '';
}

/** 点击左侧树子节点：将模板药品带入当前处方明细。 */
function handleTemplateTreeNodeClick(data: TemplateTreeNode) {
  if (!assertEditorClean()) return;
  if (data.nodeType !== 'template' || !data.templateId || !activePrescription.value) {
    return;
  }
  const currentPrescription = activePrescription.value;
  const template = templateList.value.find((item) => item.id === data.templateId);
  if (!template) {
    return;
  }
  const nextItems: PrescriptionItem[] = template.herbs.map((herb, index) => {
    const parsed = parseTemplateHerb(herb);
    return {
      id: `item-${Date.now()}-${index}`,
      selectedId: `template-${template.id}-${index}`,
      medicineName: parsed.medicineName,
      selectedDrugName: parsed.medicineName,
      selectedDrugSpec: `${parsed.dosage}g/袋`,
      selectedDrugPrice: 10,
      specification: `${parsed.dosage}g/袋`,
      dosage: parsed.dosage,
      singleDose: parsed.dosage,
      expanded: index === 0,
      usage: '',
      frequency: '',
      days: prescriptionForm.days || 0,
      totalDose: prescriptionForm.dosageCount || 0,
      price: 10,
      groupNo: currentPrescription.groupNo,
      specialDecoction: '',
    };
  });
  currentPrescription.items = nextItems;
  selectedTemplateNodeId.value = data.id;
  ElMessage.success(`已带入：${template.name}`);
}

/** 创建空白处方标签：用于医生从零开立。 */
function handleAddPrescription() {
  if (!assertEditorClean()) return;
  const nextIndex = prescriptions.value.length + 1;
  const timestamp = Date.now();
  const newRx: TcmPrescription = {
    id: `rx-${timestamp}`,
    name: `中药处方${nextIndex}`,
    status: 'draft',
    groupNo: `G${String(nextIndex).padStart(3, '0')}`,
    dosageCount: 1,
    usage: '口服',
    days: 1,
    decoctionMethod: '',
    note: '',
    items: [],
  };
  prescriptions.value.push(newRx);
  activePrescriptionId.value = newRx.id;
  loadActivePrescriptionForm();
  handleTcmEditorClose();
}

/** 签发当前处方：演示布局阶段仅切换状态。 */
function handleIssuePrescription() {
  if (!activePrescription.value) return;
  activePrescription.value.status = 'issued';
  ElMessage.success('处方已签发');
}

/** 撤回当前处方：演示布局阶段仅切换状态。 */
function handleRevokePrescription() {
  if (!activePrescription.value) return;
  activePrescription.value.status = 'revoked';
  ElMessage.success('处方已撤回');
}

/** 删除处方：支持删除当前页签或传入指定页签。 */
async function handleDeletePrescription(targetPrescriptionId?: string) {
  if (!assertEditorClean()) return;
  const removeId = targetPrescriptionId ?? activePrescriptionId.value;
  if (!removeId) return;
  if (prescriptions.value.length <= 1) {
    ElMessage.warning('至少保留一张处方');
    return;
  }
  await ElMessageBox.confirm('确认删除当前处方？', '删除确认', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消',
  });
  const index = prescriptions.value.findIndex((item) => item.id === removeId);
  if (index !== -1) {
    prescriptions.value.splice(index, 1);
  }
  if (activePrescriptionId.value === removeId) {
    const fallback = prescriptions.value[0];
    if (fallback) {
      activePrescriptionId.value = fallback.id;
      loadActivePrescriptionForm();
    }
  }
}

/** 关闭页签事件：删除对应处方。 */
function handleTabRemove(name: number | string) {
  void handleDeletePrescription(String(name));
}

loadActivePrescriptionForm();
</script>

<template>
  <div class="grid h-full min-h-0 grid-cols-[260px_minmax(0,1fr)]">
    <div class="flex h-full min-h-0 flex-col border-r border-[var(--el-border-color-light)] p-2">
      <ElInput v-model="templateKeyword" placeholder="搜索处方名/拼音" clearable class="mb-2" />

      <ElScrollbar class="min-h-0 flex-1">
        <ElTree
          :data="templateTreeData"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          :current-node-key="selectedTemplateNodeId"
          class="tcm-template-tree"
          @node-click="handleTemplateTreeNodeClick"
        >
          <template #default="{ data }">
            <div class="flex w-full items-center justify-between py-0.5">
              <ElTooltip
                v-if="data.nodeType === 'template'"
                :content="data.herbSummary || '暂无药品'"
                placement="right"
                effect="dark"
                :show-after="400"
                :hide-after="100"
              >
                <span class="truncate text-sm text-[var(--el-text-color-regular)]">
                  {{ data.label }}
                </span>
              </ElTooltip>
              <span v-else class="truncate text-sm font-medium text-[var(--el-text-color-primary)]">
                {{ data.label }}
              </span>
              <span
                v-if="data.nodeType === 'template'"
                class="ml-2 shrink-0 text-xs text-[var(--el-text-color-secondary)]"
              >
                {{ data.itemCount }}味
              </span>
            </div>
          </template>
        </ElTree>
      </ElScrollbar>
    </div>

    <div class="flex h-full min-h-0 flex-col px-3 pb-3 pt-0">
      <div class="mb-2">
        <ElTabs
          v-model="activePrescriptionId"
          class="tcm-rx-tabs"
          closable
          addable
          @tab-change="handleSwitchPrescription"
          @tab-add="handleAddPrescription"
          @tab-remove="handleTabRemove"
        >
          <ElTabPane
            v-for="item in prescriptions"
            :key="item.id"
            :name="item.id"
            :label="item.name"
          />
          <template #add-icon>
            <ElButton type="primary" class="tcm-tab-add-btn">新增处方</ElButton>
          </template>
        </ElTabs>
      </div>

      <div class="mb-2 flex items-center gap-2">
        <ElButton type="primary" @click="handleOpenTcmEditor">新增</ElButton>
        <ElButton type="primary" @click="handleIssuePrescription">签发</ElButton>
        <ElButton type="warning" plain @click="handleRevokePrescription">撤回</ElButton>
        <ElButton type="danger" plain @click="() => void handleDeletePrescription()">删除</ElButton>
        <div class="ml-auto text-sm text-[var(--el-text-color-primary)]">
          处方金额小计：{{ currentPrescriptionAmount.toFixed(2) }} 元
        </div>
      </div>

      <div class="min-h-0 flex-1 overflow-auto">
        <div class="flex h-full min-h-[240px] flex-col">
          <ElTable
            :data="tcmTableRows"
            :span-method="handleTcmTableSpanMethod"
            :cell-class-name="getTcmTableCellClassName"
            border
            stripe
            height="100%"
            class="tcm-advice-preview-table flex-1"
          >
            <ElTableColumn
              prop="editorSlot"
              label=""
              width="1"
              class-name="tcm-editor-hidden-col"
              label-class-name="tcm-editor-hidden-col"
            >
              <template #default="{ row }">
                <AdviceEditCard
                  v-if="row.isEditor && activePrescription"
                  :key="activePrescription.id"
                  v-model="tcmEditorForm"
                  variant="tcm"
                  :tcm-group-no="activePrescription.groupNo"
                  :initial-group="tcmEditorInitialGroup"
                  :product-columns="productColumns"
                  :query-product-options="queryProductOptions"
                  :validate-row="validateTcmRow"
                  @close="handleTcmEditorClose"
                  @editor-dirty-change="handleTcmEditorDirtyChange"
                  @save-group="handleTcmSaveGroup"
                >
                  <template #detail="scope">
                    <TcmAdviceDetailForm :item="scope.item" @patch="scope.patch" />
                  </template>
                  <template #collapsed="scope">
                    <TcmAdviceCollapsed :item="scope.item" />
                  </template>
                </AdviceEditCard>
              </template>
            </ElTableColumn>
            <ElTableColumn type="index" label="序号" width="56" align="center" />
            <ElTableColumn prop="medicineName" label="药品名称" min-width="120" />
            <ElTableColumn prop="singleDose" label="单次剂量(g)" width="110" align="center" />
            <ElTableColumn prop="price" label="金额" width="96" align="right">
              <template #default="{ row }">{{ Number(row.price || 0).toFixed(2) }}</template>
            </ElTableColumn>
          </ElTable>
        </div>
      </div>

      <ElForm :model="prescriptionForm" label-width="62px" class="mt-2">
        <ElRow :gutter="8">
          <ElCol :span="6">
            <ElFormItem label="剂数">
              <ElInputNumber
                v-model="prescriptionForm.dosageCount"
                class="w-full"
                :min="1"
                :max="30"
                :controls="false"
              />
            </ElFormItem>
          </ElCol>
          <ElCol :span="6">
            <ElFormItem label="用法">
              <ElSelect v-model="prescriptionForm.usage" class="w-full">
                <ElOption label="口服" value="口服" />
                <ElOption label="外用" value="外用" />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol :span="6">
            <ElFormItem label="天数">
              <ElInputNumber
                v-model="prescriptionForm.days"
                class="w-full"
                :min="1"
                :max="30"
                :controls="false"
              />
            </ElFormItem>
          </ElCol>
          <ElCol :span="6">
            <ElFormItem label="煎煮法">
              <ElInput v-model="prescriptionForm.decoctionMethod" class="w-full" />
            </ElFormItem>
          </ElCol>
        </ElRow>
      </ElForm>
    </div>
  </div>
</template>

<style scoped>
.tcm-template-tree :deep(.el-tree-node__content) {
  height: 30px;
  border-radius: 4px;
}

.tcm-template-tree :deep(.el-tree-node__content:hover) {
  background-color: var(--el-fill-color-light);
}

.tcm-template-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: var(--el-color-primary-light-9);
}

.tcm-advice-preview-table :deep(.el-table__header-wrapper th.el-table__cell) {
  background: #eaf4ff !important;
}

.tcm-advice-preview-table :deep(.tcm-editor-cell .cell) {
  padding: 8px 10px !important;
  overflow: visible !important;
}

.tcm-advice-preview-table :deep(.tcm-editor-hidden-col) {
  padding: 0 !important;
  width: 0 !important;
  min-width: 0 !important;
  border: 0 !important;
  overflow: visible !important;
}

.tcm-advice-preview-table :deep(.tcm-editor-cell) {
  overflow: visible !important;
}

.tcm-advice-preview-table :deep(.el-table__body-wrapper) {
  overflow: visible !important;
}

.tcm-rx-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.tcm-rx-tabs :deep(.el-tabs__new-tab) {
  width: auto;
  min-width: auto;
  border: 0;
  padding: 0;
}

.tcm-tab-add-btn {
  margin: 0;
  padding: 0 8px;
}
</style>
