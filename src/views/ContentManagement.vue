<template>
  <div class="page">
    <div class="page-title-row">
      <h2 class="page-title">知识内容</h2>
      <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">新增内容</el-button>
    </div>

    <div class="content-stats">
      <SectionCard v-for="item in statCards" :key="item.label">
        <div class="content-stat">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </SectionCard>
    </div>

    <SectionCard title="内容列表">
      <div class="filters">
        <el-input v-model="filters.keyword" placeholder="标题或正文关键词" clearable @keyup.enter="search" />
        <el-select v-model="filters.contentType" placeholder="全部类型" clearable>
          <el-option v-for="item in contentTypes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-if="canManage" v-model="filters.status" placeholder="全部状态" clearable>
          <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="items" stripe @row-dblclick="openDetail">
        <el-table-column prop="title" label="标题" min-width="260" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ typeLabel[row.contentType] }}</template>
        </el-table-column>
        <el-table-column prop="ownerOrganizationName" label="所属组织" min-width="140" />
        <el-table-column prop="creatorName" label="创建人" width="110" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType[row.status]">{{ statusLabel[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">查看</el-button>
            <el-button v-if="canManage" type="primary" link @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        class="pagination"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[10, 20, 50]"
        @current-change="load"
        @size-change="search"
      />
    </SectionCard>

    <el-dialog v-model="detailVisible" title="内容详情" width="720px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ typeLabel[detail.contentType] }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel[detail.status] }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.creatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属组织">{{ detail.ownerOrganizationName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="正文" :span="2">
          <pre class="content-body">{{ detail.contentText || prettyJson(detail.contentJson) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑内容' : '新增内容'" width="720px">
      <el-form label-width="92px">
        <el-form-item label="标题" required><el-input v-model="form.title" maxlength="255" /></el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.contentType">
            <el-option v-for="item in contentTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容格式">
          <el-radio-group v-model="form.bodyFormat">
            <el-radio-button value="TEXT">正文</el-radio-button>
            <el-radio-button value="JSON">结构化 JSON</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="form.body" type="textarea" :rows="12" maxlength="200000" show-word-limit />
        </el-form-item>
        <el-form-item label="所属组织ID">
          <el-input-number v-model="form.ownerOrganizationId" :min="1" controls-position="right" />
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
import { hasPermission } from '../auth/session'
import {
  createKnowledge,
  getKnowledge,
  getKnowledgeStatistics,
  listKnowledge,
  updateKnowledge
} from '../api/workspace'

const canManage = hasPermission('CONTENT_MANAGE')
const contentTypes = [
  { value: 'KNOWLEDGE', label: '知识' },
  { value: 'RULE', label: '规则' },
  { value: 'CHECKLIST', label: '检查清单' }
]
const statuses = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'REVIEWING', label: '审核中' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'ARCHIVED', label: '已归档' }
]
const typeLabel = Object.fromEntries(contentTypes.map((item) => [item.value, item.label]))
const statusLabel = Object.fromEntries(statuses.map((item) => [item.value, item.label]))
const statusType = { DRAFT: 'info', REVIEWING: 'warning', PUBLISHED: 'success', REJECTED: 'danger', ARCHIVED: 'info' }
const filters = reactive({ keyword: '', contentType: '', status: '' })
const items = ref([])
const statistics = ref({ total: 0, published: 0, reviewing: 0, draft: 0 })
const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const detailVisible = ref(false)
const editorVisible = ref(false)
const detail = ref(null)
const editingId = ref(null)
const form = reactive({ title: '', contentType: 'KNOWLEDGE', bodyFormat: 'TEXT', body: '', ownerOrganizationId: null })
const statCards = computed(() => [
  { label: '可见内容', value: statistics.value.total },
  { label: '已发布', value: statistics.value.published },
  { label: '审核中', value: statistics.value.reviewing },
  { label: '草稿', value: statistics.value.draft }
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [result, stats] = await Promise.all([
      listKnowledge({ ...filters, page: page.value, size: size.value }),
      getKnowledgeStatistics()
    ])
    items.value = result.items
    total.value = result.totalItems
    statistics.value = stats
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

async function openDetail(row) {
  try {
    detail.value = await getKnowledge(row.contentId)
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { title: '', contentType: 'KNOWLEDGE', bodyFormat: 'TEXT', body: '', ownerOrganizationId: null })
  editorVisible.value = true
}

async function openEdit(row) {
  try {
    const value = await getKnowledge(row.contentId)
    editingId.value = value.contentId
    Object.assign(form, {
      title: value.title,
      contentType: value.contentType,
      bodyFormat: value.contentText ? 'TEXT' : 'JSON',
      body: value.contentText || prettyJson(value.contentJson),
      ownerOrganizationId: value.ownerOrganizationId
    })
    editorVisible.value = true
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function submit() {
  if (!form.title.trim() || !form.body.trim()) return ElMessage.warning('请填写标题和内容')
  let contentJson = null
  if (form.bodyFormat === 'JSON') {
    try {
      contentJson = JSON.parse(form.body)
    } catch {
      return ElMessage.warning('结构化内容必须是有效 JSON')
    }
    if (contentJson === null || typeof contentJson !== 'object') return ElMessage.warning('JSON 必须是对象或数组')
  }
  const payload = {
    title: form.title,
    contentType: form.contentType,
    contentText: form.bodyFormat === 'TEXT' ? form.body : null,
    contentJson,
    ownerOrganizationId: form.ownerOrganizationId
  }
  saving.value = true
  try {
    if (editingId.value) await updateKnowledge(editingId.value, payload)
    else await createKnowledge(payload)
    editorVisible.value = false
    ElMessage.success('保存成功')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

function prettyJson(value) {
  return value ? JSON.stringify(value, null, 2) : ''
}

function formatTime(value) {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '-'
}
</script>

<style scoped>
.content-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.content-stat { display: grid; gap: 8px; }
.content-stat span { color: #64748b; }
.content-stat strong { color: var(--primary-deep); font-size: 30px; }
.filters { display: grid; grid-template-columns: minmax(220px, 1fr) 150px 150px auto auto; gap: 10px; margin-bottom: 16px; }
.pagination { justify-content: flex-end; margin-top: 16px; }
.content-body { max-height: 420px; margin: 0; overflow: auto; white-space: pre-wrap; word-break: break-word; }
@media (max-width: 900px) {
  .content-stats { grid-template-columns: repeat(2, 1fr); }
  .filters { grid-template-columns: 1fr 1fr; }
}
</style>
