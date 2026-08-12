<template>
  <div class="page">
    <div class="page-title-row">
      <h2 class="page-title">待办任务</h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增任务</el-button>
    </div>

    <div class="pending-stats">
      <SectionCard v-for="item in statCards" :key="item.label">
        <div class="pending-stat" :style="{ '--accent': item.color }">
          <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
        </div>
      </SectionCard>
    </div>

    <SectionCard title="任务列表">
      <div class="filters">
        <el-input v-model="filters.keyword" placeholder="任务关键词" clearable @keyup.enter="search" />
        <el-select v-model="filters.status" placeholder="全部状态" clearable>
          <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.priority" placeholder="全部优先级" clearable>
          <el-option v-for="item in priorities" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-checkbox v-model="filters.mine">只看我的</el-checkbox>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="items" stripe @row-dblclick="openDetail">
        <el-table-column prop="title" label="任务名称" min-width="220" />
        <el-table-column prop="subjectTitle" label="关联对象" min-width="180" />
        <el-table-column prop="taskType" label="任务类型" width="120" />
        <el-table-column prop="assigneeName" label="负责人" width="100" />
        <el-table-column label="截止时间" width="180">
          <template #default="{ row }"><span :class="{ overdue: row.overdue }">{{ formatTime(row.dueAt) }}</span></template>
        </el-table-column>
        <el-table-column label="优先级" width="100">
          <template #default="{ row }"><el-tag :type="priorityType[row.priority]">{{ priorityLabel[row.priority] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><el-tag :type="statusType[row.taskStatus]">{{ statusLabel[row.taskStatus] }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">查看</el-button>
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page" v-model:page-size="size" class="pagination"
        layout="total, sizes, prev, pager, next" :total="total" :page-sizes="[10, 20, 50]"
        @current-change="load" @size-change="search"
      />
    </SectionCard>

    <el-dialog v-model="detailVisible" title="任务详情" width="680px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="任务名称" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="关联对象">{{ detail.subjectTitle }}</el-descriptions-item>
        <el-descriptions-item label="任务类型">{{ detail.taskType }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel[detail.taskStatus] }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ priorityLabel[detail.priority] }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ detail.assigneeName }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ formatTime(detail.dueAt) }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑任务' : '新增任务'" width="680px">
      <el-form label-width="90px">
        <el-form-item label="关联类型" required>
          <el-radio-group v-model="form.subjectType" @change="changeSubjectType">
            <el-radio-button value="CASE">案件</el-radio-button>
            <el-radio-button value="KNOWLEDGE">知识内容</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.subjectType === 'CASE' ? '关联案件' : '关联内容'" required>
          <el-select
            v-model="form.subjectId"
            filterable
            remote
            :remote-method="searchSubjects"
            :loading="subjectLoading"
            placeholder="输入名称搜索"
            style="width: 100%"
          >
            <el-option
              v-for="item in subjectOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务类型" required><el-input v-model="form.taskType" maxlength="50" /></el-form-item>
        <el-form-item label="任务名称" required><el-input v-model="form.title" maxlength="255" /></el-form-item>
        <el-form-item label="任务说明"><el-input v-model="form.description" type="textarea" :rows="5" maxlength="10000" /></el-form-item>
        <el-form-item label="优先级" required>
          <el-select v-model="form.priority">
            <el-option v-for="item in priorities" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="form.dueAt" type="datetime" placeholder="选择截止时间" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import { listCases } from '../api/cases'
import { listKnowledge } from '../api/workspace'
import { createWorkTask, getDashboard, getWorkTask, listWorkTasks, updateWorkTask } from '../api/workspace'

const statuses = [
  { value: 'PENDING', label: '待处理' }, { value: 'PROCESSING', label: '处理中' },
  { value: 'WAITING_CONFIRMATION', label: '待确认' }, { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
]
const priorities = [
  { value: 'LOW', label: '低' }, { value: 'MEDIUM', label: '中' },
  { value: 'HIGH', label: '高' }, { value: 'URGENT', label: '紧急' }
]
const statusLabel = Object.fromEntries(statuses.map((item) => [item.value, item.label]))
const priorityLabel = Object.fromEntries(priorities.map((item) => [item.value, item.label]))
const statusType = { PENDING: 'warning', PROCESSING: 'primary', WAITING_CONFIRMATION: 'info', COMPLETED: 'success', CANCELLED: 'info' }
const priorityType = { LOW: 'info', MEDIUM: 'warning', HIGH: 'danger', URGENT: 'danger' }
const filters = reactive({ keyword: '', status: '', priority: '', mine: true })
const metrics = ref({ active: 0, dueToday: 0, overdue: 0, waitingConfirmation: 0 })
const items = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const detailVisible = ref(false)
const editorVisible = ref(false)
const detail = ref(null)
const editingId = ref(null)
const subjectLoading = ref(false)
const subjectOptions = ref([])
const form = reactive({ subjectType: 'CASE', subjectId: null, taskType: '', title: '', description: '', priority: 'MEDIUM', dueAt: null })
const statCards = computed(() => [
  { label: '进行中任务', value: metrics.value.active, color: '#2f72f6' },
  { label: '今日到期', value: metrics.value.dueToday, color: '#ff8a00' },
  { label: '已超期', value: metrics.value.overdue, color: '#f04f5f' },
  { label: '待确认', value: metrics.value.waitingConfirmation, color: '#14b8a6' }
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [result, dashboard] = await Promise.all([
      listWorkTasks({ ...filters, page: page.value, size: size.value }),
      getDashboard()
    ])
    items.value = result.items
    total.value = result.totalItems
    metrics.value = dashboard.tasks
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; load() }

async function openDetail(row) {
  try { detail.value = await getWorkTask(row.taskId); detailVisible.value = true }
  catch (error) { ElMessage.error(error.message) }
}

async function openCreate() {
  editingId.value = null
  Object.assign(form, { subjectType: 'CASE', subjectId: null, taskType: '', title: '', description: '', priority: 'MEDIUM', dueAt: null })
  await loadSubjects()
  editorVisible.value = true
}

async function openEdit(row) {
  try {
    const value = await getWorkTask(row.taskId)
    editingId.value = value.taskId
    Object.assign(form, {
      subjectType: value.caseId ? 'CASE' : 'KNOWLEDGE',
      subjectId: value.caseId || value.contentId,
      taskType: value.taskType,
      title: value.title,
      description: value.description || '',
      priority: value.priority,
      dueAt: value.dueAt ? new Date(value.dueAt) : null
    })
    await loadSubjects(value.subjectTitle)
    editorVisible.value = true
  } catch (error) { ElMessage.error(error.message) }
}

async function changeSubjectType() {
  form.subjectId = null
  await loadSubjects()
}

function searchSubjects(keyword) {
  loadSubjects(keyword)
}

async function loadSubjects(keyword = '') {
  subjectLoading.value = true
  try {
    if (form.subjectType === 'CASE') {
      const result = await listCases({ page: 1, size: 100, keyword })
      subjectOptions.value = result.items.map((item) => ({
        value: item.caseId,
        label: `${item.caseName}${item.caseNo ? `（${item.caseNo}）` : ''}`
      }))
    } else {
      const result = await listKnowledge({ page: 1, size: 100, keyword })
      subjectOptions.value = result.items.map((item) => ({ value: item.contentId, label: item.title }))
    }
  } catch (error) {
    subjectOptions.value = []
    ElMessage.error(error.message)
  } finally {
    subjectLoading.value = false
  }
}

async function submit() {
  if (!form.subjectId || !form.taskType.trim() || !form.title.trim()) return ElMessage.warning('请填写关联对象、任务类型和任务名称')
  const payload = {
    caseId: form.subjectType === 'CASE' ? form.subjectId : null,
    contentId: form.subjectType === 'KNOWLEDGE' ? form.subjectId : null,
    taskType: form.taskType,
    title: form.title,
    description: form.description || null,
    priority: form.priority,
    dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : null
  }
  saving.value = true
  try {
    if (editingId.value) await updateWorkTask(editingId.value, payload)
    else await createWorkTask(payload)
    editorVisible.value = false
    ElMessage.success('保存成功')
    await load()
  } catch (error) { ElMessage.error(error.message) }
  finally { saving.value = false }
}

function formatTime(value) {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '-'
}
</script>

<style scoped>
.pending-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.pending-stat { position: relative; display: grid; gap: 8px; padding-left: 12px; }
.pending-stat::before { position: absolute; inset: 4px auto 4px 0; width: 4px; background: var(--accent); border-radius: 4px; content: ''; }
.pending-stat span { color: #64748b; }
.pending-stat strong { color: var(--accent); font-size: 30px; }
.filters { display: grid; grid-template-columns: minmax(200px, 1fr) 150px 150px auto auto auto; align-items: center; gap: 10px; margin-bottom: 16px; }
.pagination { justify-content: flex-end; margin-top: 16px; }
.overdue { color: #d9363e; font-weight: 700; }
@media (max-width: 900px) {
  .pending-stats { grid-template-columns: repeat(2, 1fr); }
  .filters { grid-template-columns: 1fr 1fr; }
}
</style>
