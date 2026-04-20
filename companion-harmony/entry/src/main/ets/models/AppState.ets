/**
 * 应用状态模型定义
 * 集中式状态管理的核心数据结构
 */

/**
 * 连接状态
 */
export interface ConnectionState {
  isConnected: boolean          // WebSocket是否已连接
  carIpAddress: string          // 车机端IP地址
  localIpAddress: string        // 本机IP地址
  lastConnectionTime: number    // 最后连接时间戳
  isConnecting: boolean         // 是否正在连接中
}

/**
 * 播放状态
 */
export interface PlaybackState {
  uri: string                               // 视频URI
  httpHeaders: Record<string, string>       // HTTP请求头
  position: number                          // 当前播放位置（毫秒）
  duration: number                          // 总时长（毫秒）
  isPlaying: boolean                        // 是否正在播放
  isPrepared: boolean                       // 视频是否已准备完成
  syncTargetPosition: number                // 同步启动的目标进度
}

/**
 * 网络状态
 */
export interface NetworkState {
  hasWifi: boolean                // 是否有WiFi连接
  networkStatus: string           // 网络状态 (unknown, available, lost)
  isScanning: boolean             // 是否正在扫描网络
  scanProgress: string            // 扫描进度描述
}

/**
 * UI状态
 */
export interface UiState {
  isRefreshing: boolean           // 是否正在刷新连接
  scanDetail: string              // 详细扫描进度（显示在卡片中）
  errorMessage: string            // 错误消息
  isDownloading: boolean          // 是否正在下载视频
  downloadProgress: number        // 下载进度百分比
}

/**
 * 应用完整状态
 */
export interface AppState {
  connection: ConnectionState
  playback: PlaybackState
  network: NetworkState
  ui: UiState
}

/**
 * 状态变化监听器
 */
export type StateChangeListener = (newState: AppState) => void

/**
 * 默认连接状态
 */
export function createDefaultConnectionState(): ConnectionState {
  return {
    isConnected: false,
    carIpAddress: '192.168.200.47',
    localIpAddress: '获取中...',
    lastConnectionTime: 0,
    isConnecting: false
  }
}

/**
 * 默认播放状态
 */
export function createDefaultPlaybackState(): PlaybackState {
  return {
    uri: '',
    httpHeaders: {},
    position: 0,
    duration: 0,
    isPlaying: false,
    isPrepared: false,
    syncTargetPosition: 0
  }
}

/**
 * 默认网络状态
 */
export function createDefaultNetworkState(): NetworkState {
  return {
    hasWifi: false,
    networkStatus: 'unknown',
    isScanning: false,
    scanProgress: ''
  }
}

/**
 * 默认UI状态
 */
export function createDefaultUiState(): UiState {
  return {
    isRefreshing: false,
    scanDetail: '',
    errorMessage: '',
    isDownloading: false,
    downloadProgress: 0
  }
}

/**
 * 创建默认应用状态
 */
export function createDefaultAppState(): AppState {
  return {
    connection: createDefaultConnectionState(),
    playback: createDefaultPlaybackState(),
    network: createDefaultNetworkState(),
    ui: createDefaultUiState()
  }
}
