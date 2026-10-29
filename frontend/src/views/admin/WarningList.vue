<template>
  <div class="page-container">
    <!-- 总览统计 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #409eff">{{ summary.totalStudents ?? 0 }}</div>
          <div class="stat-label">学生总数</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="5">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #303133">{{ summary.flaggedStudents ?? 0 }}</div>
          <div class="stat-label">已触发预警</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="5">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" :style="{ color: warningColor('RED') }">{{ summary.redCount ?? 0 }}</div>
          <div class="stat-label">红色（学业红线）</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="5">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" :style="{ color: warningColor('ORANGE') }">{{ summary.orangeCount ?? 0 }}</div>
          <div class="stat-label">橙色（学业预警）</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="5">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" :style="{ color: warningColor('YELLOW') }">{{ summary.yellowCount ?? 0 }}</div>
          <div class="stat-label">黄色（学业关注）</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 预警规则说明 -->
    <el-card shadow="never" class="rule-card">
      <template #header>
        <div class="card-header">
          <span>学业红线预警规则（两条规则取最严）</span>
          <span class="rule-hint">
            已评估 {{ summary.evaluatedStudents ?? 0 }} 人
          </span>
        </div>
      </template>
      <el-table :data="summary.thresholds || []" size="small" border class="rule-table">
        <el-table-column label="等级" width="200">
          <template #default="{ row }">
            <el-tag effect="dark" size="small" :color="warningColor(row.level)" class="level-tag">
              {{ row.levelLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="累计不及格学分" align="center">
          <template #default="{ row }">≥ {{ row.failedCredits }} 学分</template>
        </el-table-column>
        <el-table-column label="单学期最多不及格门数" align="center">
          <template #default="{ row }">≥ {{ row.semesterFailedCount }} 门</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 预警名单 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>学业预警名单</span>
          <div class="header-actions">
            <el-button
              type="primary"
              :loading="sending"
              :disabled="!selectedRows.length"
              @click="handleNotifySelected"
            >
              <el-icon><Message /></el-icon>
              发送邮件通知{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
            </el-button>
            <el-button :loading="sending" @click="handleNotifyFiltered">
              <el-icon><Promotion /></el-icon>一键通知当前筛选
            </el-button>
            <el-button :loading="testing" @click="handleTestMail">
              <el-icon><Promotion /></el-icon>邮件自检
            </el-button>
            <el-button @click="openHistory">
              <el-icon><Tickets /></el-icon>发送历史
            </el-button>
            <el-button type="primary" link :loading="loading" @click="loadList">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="等级">
          <el-select v-model="query.level" placeholder="等级" style="width: 160px" @change="handleSearch">
            <el-option
              v-for="item in levelOptions"
              :key="item.label"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="query.clazzId" placeholder="全部班级" clearable style="width: 160px" @change="handleSearch">
            <el-option v-for="c in clazzs" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="专业">
          <el-select v-model="query.majorId" placeholder="全部专业" clearable style="width: 200px" @change="handleSearch">
            <el-option v-for="m in majors" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="学号或姓名"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <PageTable
        :data="list"
        :loading="loading"
        mode="server"
        :page="query.page"
        :size="query.size"
        :total="total"
        row-key="studentId"
        empty-text="当前筛选条件下没有需要预警的学生"
        @update:page="onPageChange"
        @update:size="onSizeChange"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="46" :selectable="() => true" />
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-wrap">
              <WarningAlert :data="row" show-student show-rules />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="studentNo" label="学号" width="110" />
        <el-table-column prop="studentName" label="姓名" width="100" />
        <el-table-column prop="clazzName" label="班级" width="110" />
        <el-table-column prop="majorName" label="专业" min-width="150" show-overflow-tooltip />
        <el-table-column label="预警等级" width="140" align="center">
          <template #default="{ row }">
            <el-tag effect="dark" size="small" :color="warningColor(row.level)" class="level-tag">
              {{ warningShortLabel(row.level) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号邮箱" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.email">{{ row.email }}</span>
            <el-tag v-else type="danger" size="small" effect="plain">未登记邮箱</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="累计不及格学分" width="130" align="center" sortable :sort-method="(a, b) => a.failedCredits - b.failedCredits">
          <template #default="{ row }">
            <span :class="{ 'text-danger': Number(row.failedCredits) > 0 }">{{ row.failedCredits }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="failedCourseCount" label="不及格门数" width="110" align="center" />
        <el-table-column prop="maxSemesterFailedCount" label="单学期最多" width="110" align="center" />
        <el-table-column prop="scoredCourseCount" label="已评门数" width="100" align="center" />
        <el-table-column prop="passedCredits" label="已获学分" width="100" align="center" />
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPreview(row)">预览</el-button>
            <el-button
              link
              type="primary"
              size="small"
              :disabled="!row.triggered"
              @click="handleNotifyOne(row)"
            >发送</el-button>
          </template>
        </el-table-column>
      </PageTable>
    </el-card>

    <!-- 邮件预览 -->
    <el-dialog v-model="previewVisible" title="邮件内容预览" width="720px">
      <div v-if="preview" class="preview-wrap">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="收件人">
            {{ preview.studentName }}（{{ preview.studentNo }}）
          </el-descriptions-item>
          <el-descriptions-item label="收件邮箱">
            <span v-if="preview.email">{{ preview.email }}</span>
            <el-tag v-else type="danger" size="small">未登记邮箱，无法发送</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="预警等级">
            <el-tag effect="dark" size="small" :color="warningColor(preview.level)">
              {{ preview.levelLabel }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="邮件主题">{{ preview.subject }}</el-descriptions-item>
        </el-descriptions>
        <div class="preview-label">正文（纯文本版本，实际发送同时包含 HTML 排版版本）</div>
        <el-input v-model="preview.content" type="textarea" :rows="16" readonly class="preview-body" />
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :disabled="!preview?.email || !preview?.triggered"
          :loading="sending"
          @click="handleNotifyOne(preview)"
        >发送给该学生</el-button>
      </template>
    </el-dialog>

    <!-- 发送结果 -->
    <el-dialog v-model="resultVisible" title="邮件发送结果" width="720px">
      <el-alert
        :type="result.failed > 0 ? 'warning' : 'success'"
        :closable="false"
        show-icon
        class="result-alert"
        :title="resultTitle"
      />
      <el-table :data="result.results || []" border size="small" max-height="380">
        <el-table-column prop="studentNo" label="学号" width="100" />
        <el-table-column prop="studentName" label="姓名" width="90" />
        <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '—' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small" effect="dark">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="240" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false">知道了</el-button>
      </template>
    </el-dialog>

    <!-- 邮件自检结果 -->
    <el-dialog v-model="mailTestVisible" title="邮件自检结果" width="700px">
      <el-alert
        :type="mailTest.success ? 'success' : 'error'"
        :closable="false"
        show-icon
        class="result-alert"
        :title="mailTest.success ? '发送成功' : '发送失败'"
        :description="mailTest.message"
      />
      <el-alert
        v-if="mailTest.hint"
        type="warning"
        :closable="false"
        show-icon
        class="result-alert"
        title="可能的原因"
        :description="mailTest.hint"
      />
      <el-descriptions :column="1" border size="small" title="当前 SMTP 配置（不含密码）">
        <el-descriptions-item label="发送模式">
          <el-tag :type="mailTest.mailEnabled ? 'success' : 'warning'" size="small" effect="dark">
            {{ mailTest.mailEnabled ? '真实发送' : '模拟发送' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="SMTP 服务器">{{ mailTest.host || '—' }}:{{ mailTest.port || '—' }}</el-descriptions-item>
        <el-descriptions-item label="登录账号">{{ mailTest.username || '（未配置）' }}</el-descriptions-item>
        <el-descriptions-item label="发件人">{{ mailTest.from || '—' }}</el-descriptions-item>
        <el-descriptions-item label="收件人">{{ mailTest.to || '（本人账号未登记邮箱）' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="mailTestVisible = false">知道了</el-button>
      </template>
    </el-dialog>

    <!-- 发送历史 -->
    <el-dialog v-model="historyVisible" title="邮件发送历史" width="860px">
      <el-table v-loading="historyLoading" :data="history.records || []" border size="small" max-height="440">
        <el-table-column prop="sendTime" label="发送时间" width="160" />
        <el-table-column prop="studentNo" label="学号" width="100" />
        <el-table-column prop="studentName" label="姓名" width="90" />
        <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '—' }}</template>
        </el-table-column>
        <el-table-column label="等级" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="dark" :color="warningColor(row.level)">{{ row.levelLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small" effect="dark">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="失败原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errorMessage || '—' }}</template>
        </el-table-column>
      </el-table>
      <div class="history-pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :current-page="historyQuery.page"
          :page-size="historyQuery.size"
          :total="history.total || 0"
          @current-change="onHistoryPageChange"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchNotifications,
  fetchWarningList,
  fetchWarningPreview,
  fetchWarningSummary,
  notifyWarnings,
  sendTestMail
} from '@/api/warning'
import { fetchClazzOptions, fetchMajorOptions } from '@/api/meta'
import PageTable from '@/components/PageTable.vue'
import WarningAlert from '@/components/WarningAlert.vue'
import { WARNING_LEVEL_OPTIONS, warningColor, warningShortLabel } from '@/utils/dict'

const levelOptions = WARNING_LEVEL_OPTIONS
const summary = ref({})
const list = ref({ records: [], total: 0 })
const total = ref(0)
const loading = ref(false)
const sending = ref(false)
const clazzs = ref([])
const majors = ref([])
const selectedRows = ref([])

const testing = ref(false)
const mailTestVisible = ref(false)
const mailTest = ref({})

const preview = ref(null)
const previewVisible = ref(false)
const result = ref({})
const resultVisible = ref(false)
const history = ref({ records: [], total: 0 })
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyQuery = reactive({ page: 1, size: 10 })

const query = reactive({
  level: '',
  clazzId: null,
  majorId: null,
  keyword: '',
  page: 1,
  size: 10
})

const resultTitle = computed(() => {
  const r = result.value || {}
  return `共 ${r.total ?? 0} 人：已发送 ${r.sent ?? 0}，模拟发送 ${r.simulated ?? 0}，失败 ${r.failed ?? 0}，跳过 ${r.skipped ?? 0}`
})

function statusTag(status) {
  return { SENT: 'success', SIMULATED: 'warning', FAILED: 'danger', SKIPPED: 'info' }[status] || 'info'
}

async function loadSummary() {
  try {
    const res = await fetchWarningSummary()
    summary.value = res?.data || {}
  } catch (e) {
    summary.value = {}
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await fetchWarningList(buildParams())
    list.value = res?.data || { records: [], total: 0 }
    total.value = res?.data?.total ?? 0
    selectedRows.value = []
  } catch (e) {
    list.value = { records: [], total: 0 }
    total.value = 0
  } finally {
    loading.value = false
  }
}

function buildParams(extra = {}) {
  return {
    level: query.level || undefined,
    clazzId: query.clazzId || undefined,
    majorId: query.majorId || undefined,
    keyword: query.keyword || undefined,
    page: query.page,
    size: query.size,
    ...extra
  }
}

async function loadOptions() {
  try {
    const [c, m] = await Promise.all([fetchClazzOptions(), fetchMajorOptions()])
    clazzs.value = c?.data || []
    majors.value = m?.data || []
  } catch (e) {
    clazzs.value = []
    majors.value = []
  }
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handleReset() {
  query.level = ''
  query.clazzId = null
  query.majorId = null
  query.keyword = ''
  query.page = 1
  loadList()
}

function onPageChange(next) {
  query.page = next
  loadList()
}

function onSizeChange(next) {
  query.size = next
  query.page = 1
  loadList()
}

function onSelectionChange(rows) {
  selectedRows.value = rows || []
}

/* ---------------------------------------------------------- 邮件自检 */

async function handleTestMail() {
  testing.value = true
  try {
    const res = await sendTestMail()
    mailTest.value = res?.data || {}
    mailTestVisible.value = true
  } catch (e) {
    // 拦截器已提示
  } finally {
    testing.value = false
  }
}

/* ---------------------------------------------------------- 邮件通知 */

async function openPreview(row) {
  try {
    const res = await fetchWarningPreview(row.studentId)
    preview.value = res?.data || null
    previewVisible.value = true
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function doNotify(studentIds) {
  if (!studentIds.length) {
    ElMessage.warning('没有需要发送的学生')
    return
  }
  sending.value = true
  try {
    const res = await notifyWarnings(studentIds)
    result.value = res?.data || {}
    resultVisible.value = true
    previewVisible.value = false
    ElMessage.success(res?.message || '发送完成')
    await loadSummary()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    sending.value = false
  }
}

async function handleNotifySelected() {
  const rows = selectedRows.value
  if (!rows.length) return
  await confirmAndNotify(rows, `确定向选中的 ${rows.length} 名学生发送学业预警邮件通知吗？`)
}

async function handleNotifyOne(row) {
  await confirmAndNotify([row], `确定向 ${row.studentName}（${row.studentNo}）发送学业预警邮件通知吗？`)
}

async function handleNotifyFiltered() {
  loading.value = true
  let rows = []
  try {
    // 取当前筛选条件下的全部预警学生（上限 200）
    const res = await fetchWarningList(buildParams({ page: 1, size: 200 }))
    rows = (res?.data?.records || []).filter((r) => r.triggered)
  } catch (e) {
    rows = []
  } finally {
    loading.value = false
  }
  if (!rows.length) {
    ElMessage.warning('当前筛选条件下没有需要发送的预警学生')
    return
  }
  const withMail = rows.filter((r) => r.email).length
  await confirmAndNotify(
    rows,
    `当前筛选条件下共 ${rows.length} 名预警学生，其中 ${withMail} 人登记了邮箱、${rows.length - withMail} 人无邮箱（将被跳过）。确定发送吗？`
  )
}

async function confirmAndNotify(rows, message) {
  try {
    await ElMessageBox.confirm(message, '发送学业预警邮件通知', {
      confirmButtonText: '确定发送',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  await doNotify(rows.map((r) => r.studentId))
}

/* ---------------------------------------------------------- 发送历史 */

async function openHistory() {
  historyVisible.value = true
  historyQuery.page = 1
  await loadHistory()
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await fetchNotifications({
      page: historyQuery.page,
      size: historyQuery.size
    })
    history.value = res?.data || { records: [], total: 0 }
  } catch (e) {
    history.value = { records: [], total: 0 }
  } finally {
    historyLoading.value = false
  }
}

function onHistoryPageChange(next) {
  historyQuery.page = next
  loadHistory()
}

onMounted(async () => {
  await Promise.all([loadSummary(), loadOptions()])
  await loadList()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.rule-card {
  margin-bottom: 16px;
}

.rule-hint {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}

.rule-table {
  margin-bottom: 4px;
}

.stat-card {
  margin-bottom: 16px;
  text-align: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
}

.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}

.filter-form {
  margin-bottom: 4px;
}

.level-tag {
  border: none;
  color: #fff;
}

.text-danger {
  color: #f56c6c;
  font-weight: 600;
}

.expand-wrap {
  padding: 12px 24px;
  background: #fafafa;
}

.preview-wrap {
  padding-bottom: 4px;
}

.preview-label {
  margin: 14px 0 6px;
  font-size: 13px;
  color: #606266;
}

.preview-body :deep(textarea) {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12.5px;
  line-height: 1.7;
}

.result-alert {
  margin-bottom: 12px;
}

.history-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
