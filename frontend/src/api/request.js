import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { clearAuthStorage, getAuthToken, getAuthUser } from '@/utils/authStorage'

const service = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' }
})

/** 跳转登录页（避免重复跳转；公开页不跳转） */
function redirectToLogin() {
  clearAuthStorage()
  const current = router.currentRoute.value
  // 注册页等公开页无需登录，收到 401 时留在原地
  if (current.meta?.public) {
    return
  }
  if (current.path !== '/login') {
    router.replace('/login')
  }
}

/** 请求拦截器：附加 Bearer 令牌 */
service.interceptors.request.use(
  (config) => {
    const token = getAuthToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

/** 响应拦截器：只依据业务 code 判定成败 */
service.interceptors.response.use(
  (response) => {
    const res = response.data

    // 非统一响应体（例如文件流）直接透传
    if (res == null || typeof res !== 'object' || !('code' in res)) {
      return res
    }

    if (res.code === 200) {
      return res
    }

    if (res.code === 401) {
      ElMessage.error(res.message || '登录已过期，请重新登录')
      redirectToLogin()
      return Promise.reject(new Error(res.message || '未登录或令牌已过期'))
    }

    if (res.code === 403) {
      ElMessage.error(res.message || '无权访问该资源')
      return Promise.reject(new Error(res.message || '无权访问该资源'))
    }

    if (res.code === 500) {
      ElMessage.error(res.message || '系统繁忙，请稍后重试')
      return Promise.reject(new Error(res.message || '系统繁忙，请稍后重试'))
    }

    ElMessage.error(res.message || '操作失败')
    return Promise.reject(new Error(res.message || '操作失败'))
  },
  (error) => {
    const status = error?.response?.status
    const data = error?.response?.data

    if (status === 401 || data?.code === 401) {
      ElMessage.error(data?.message || '登录已过期，请重新登录')
      redirectToLogin()
      return Promise.reject(error)
    }

    if (status === 403 || data?.code === 403) {
      ElMessage.error(data?.message || '无权访问该资源')
      return Promise.reject(error)
    }

    let message = data?.message
    if (!message) {
      if (error.code === 'ECONNABORTED') {
        message = '请求超时，请稍后重试'
      } else if (status === 404) {
        message = '请求的资源不存在'
      } else if (status >= 500) {
        message = '系统繁忙，请稍后重试'
      } else if (!error.response) {
        message = '网络异常，无法连接服务器'
      } else {
        message = '操作失败'
      }
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export { getAuthToken, getAuthUser }
export default service
