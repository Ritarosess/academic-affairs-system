/**
 * 认证信息在 sessionStorage 中的读写，供 axios 拦截器与 Pinia store 共用。
 * 单独成文件以避免 request.js 与 stores/auth.js 循环依赖。
 *
 * ── 为什么用 sessionStorage 而不是 localStorage ──────────────────────────────
 * localStorage 在**同一浏览器的所有标签页之间共享**。而 request.js 的请求拦截器
 * 会在**每次请求时**重新读取令牌，因此会出现这样的现象：
 *
 *   标签页 1 以「管理员」登录  → localStorage.token = 管理员令牌
 *   标签页 2 以「学生」登录    → localStorage.token = 学生令牌（覆盖了管理员）
 *   回到标签页 1 继续操作      → 读到的是学生令牌，页面身份被"顶掉"
 *
 * 结果就是"系统不能同时多个用户登录"。后端的 JWT 是无状态的，本身完全支持多用户
 * 并存（见 tools/debug-multi-user.mjs 的并发验证），问题只出在前端令牌的存放位置。
 *
 * sessionStorage 是**按标签页隔离**的：每个标签页各有一份令牌，
 * 因此可以同时用 3 个标签页分别以管理员、教师、学生身份登录与操作。
 *
 * 代价：关闭标签页后登录状态不再保留（刷新不受影响）。
 * 若更希望"关闭浏览器后仍保持登录"，把下面的 storage() 换成 localStorage 即可，
 * 但要接受上面描述的"同一浏览器只能有一个用户"的限制。
 */
const TOKEN_KEY = 'jx_token'
const USER_KEY = 'jx_user'

/** 统一的存储入口；如需改回跨标签共享，只需改这一处 */
function storage() {
  return window.sessionStorage
}

export function getAuthToken() {
  try {
    return storage().getItem(TOKEN_KEY) || ''
  } catch (e) {
    return ''
  }
}

export function setAuthToken(token) {
  try {
    if (token) {
      storage().setItem(TOKEN_KEY, token)
    } else {
      storage().removeItem(TOKEN_KEY)
    }
  } catch (e) {
    /* ignore */
  }
}

export function getAuthUser() {
  try {
    const raw = storage().getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

export function setAuthUser(user) {
  try {
    if (user) {
      storage().setItem(USER_KEY, JSON.stringify(user))
    } else {
      storage().removeItem(USER_KEY)
    }
  } catch (e) {
    /* ignore */
  }
}

export function clearAuthStorage() {
  setAuthToken('')
  setAuthUser(null)
}

/**
 * 清理历史版本遗留的 localStorage 令牌。
 * 早期版本把令牌存在 localStorage，若不清理，旧数据虽然没有被读取，
 * 但会在浏览器里长期残留，且在切回 localStorage 方案时造成困惑。
 */
export function purgeLegacyLocalStorage() {
  try {
    window.localStorage.removeItem(TOKEN_KEY)
    window.localStorage.removeItem(USER_KEY)
  } catch (e) {
    /* ignore */
  }
}
