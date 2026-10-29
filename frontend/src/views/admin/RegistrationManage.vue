<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group v-model="status" @change="handleSearch">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="PENDING">待审核</el-radio-button>
          <el-radio-button value="APPROVED">已通过</el-radio-button>
          <el-radio-button value="REJECTED">已拒绝</el-radio-button>
        </el-radio-group>
        <div class="toolbar-spacer" />
        <el-button @click="loadList">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>

      <PageTable
        v-model:page="page"
        v-model:size="size"
        :data="listData"
        :loading="loading"
        :total="total"
        mode="server"
        row-key="id"
        @change="loadList"
      >
        <el-table-column type="index" label="#" width="56" align="center" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column prop="roleLabel" label="类型" width="80" align="center" />
        <el-table-column prop="studentNo" label="学号" min-width="110" />
        <el-table-column prop="teacherNo" label="工号" min-width="110" />
        <el-table-column prop="clazzName" label="班级" min-width="110" />
        <el-table-column prop="department" label="院系" min-width="130" />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" min-width="165" />
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button type="success" link @click="handleApprove(row)">通过</el-button>
              <el-button type="danger" link @click="handleReject(row)">拒绝</el-button>
            </template>
          </template>
        </el-table-column>
      </PageTable>
    </el-card>

    <el-dialog v-model="detailVisible" title="注册申请详情" width="560px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户名">{{ current.username }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ current.realName }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ current.roleLabel }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ current.gender }}</el-descriptions-item>
        <el-descriptions-item v-if="current.role === 'STUDENT'" label="学号">{{ current.studentNo }}</el-descriptions-item>
        <el-descriptions-item v-if="current.role === 'STUDENT'" label="班级">{{ current.clazzName }}</el-descriptions-item>
        <el-descriptions-item v-if="current.role === 'TEACHER'" label="工号">{{ current.teacherNo }}</el-descriptions-item>
        <el-descriptions-item v-if="current.role === 'TEACHER'" label="职称">{{ current.title }}</el-descriptions-item>
        <el-descriptions-item v-if="current.role === 'TEACHER'" label="院系" :span="2">{{ current.department }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ current.phone }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ current.email }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ current.statusLabel }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ current.createTime }}</el-descriptions-item>
        <el-descriptions-item v-if="current.reviewRemark" label="审核意见" :span="2">{{ current.reviewRemark }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <template v-if="current.status === 'PENDING'">
          <el-button type="danger" @click="handleReject(current)">拒绝</el-button>
          <el-button type="success" @click="handleApprove(current)">通过</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageTable from '@/components/PageTable.vue'
import { approveRegistration, fetchRegistrations, rejectRegistration } from '@/api/registration'

const status = ref('PENDING')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const listData = ref({ total: 0, page: 1, size: 10, records: [] })
const loading = ref(false)
const detailVisible = ref(false)
const current = reactive({})

async function loadList() {
  loading.value = true
  try {
    const res = await fetchRegistrations({
      status: status.value || undefined,
      page: page.value,
      size: size.value
    })
    listData.value = res.data
    total.value = res.data?.total || 0
  } catch (e) {
    listData.value = { total: 0, page: 1, size: size.value, records: [] }
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadList()
}

function statusTagType(value) {
  if (value === 'APPROVED') return 'success'
  if (value === 'REJECTED') return 'danger'
  return 'warning'
}

function openDetail(row) {
  Object.assign(current, row)
  detailVisible.value = true
}

async function handleApprove(row) {
  try {
    const { value } = await ElMessageBox.prompt('可通过填写审核意见（可选）', '通过注册申请', {
      confirmButtonText: '确定通过',
      cancelButtonText: '取消',
      inputPlaceholder: '审核意见（可选）',
      inputValidator: (v) => !v || v.length <= 255 || '审核意见不能超过 255 个字符'
    })
    await approveRegistration(row.id, { remark: value || '' })
    ElMessage.success('已通过该注册申请')
    detailVisible.value = false
    loadList()
  } catch (e) {
    // 取消或请求失败，无需额外处理
  }
}

async function handleReject(row) {
  try {
    const { value } = await ElMessageBox.prompt('请填写拒绝原因', '拒绝注册申请', {
      confirmButtonText: '确定拒绝',
      cancelButtonText: '取消',
      inputPlaceholder: '拒绝原因（可选）',
      inputValidator: (v) => !v || v.length <= 255 || '审核意见不能超过 255 个字符'
    })
    await rejectRegistration(row.id, { remark: value || '' })
    ElMessage.success('已拒绝该注册申请')
    detailVisible.value = false
    loadList()
  } catch (e) {
    // 取消或请求失败，无需额外处理
  }
}

onMounted(loadList)
</script>
