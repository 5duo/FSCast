/**
 * 集中式状态管理器
 * 单例模式，订阅/发布机制
 */
import { hilog } from '@kit.PerformanceAnalysisKit'
import {
  AppState,
  ConnectionState,
  PlaybackState,
  NetworkState,
  UiState,
  StateChangeListener,
  createDefaultAppState
} from '../models/AppState'

const TAG = 'StateManager'
const DOMAIN = 0x0000

export class StateManager {
  private static instance: StateManager | null = null
  private state: AppState
  private listeners: Map<string, StateChangeListener> = new Map()
  private updateCount: number = 0

  private constructor() {
    this.state = createDefaultAppState()
    hilog.info(DOMAIN, TAG, 'StateManager initialized')
  }

  /**
   * 获取单例实例
   */
  static getInstance(): StateManager {
    if (!StateManager.instance) {
      StateManager.instance = new StateManager()
    }
    return StateManager.instance
  }

  /**
   * 获取当前完整状态
   */
  getState(): AppState {
    return { ...this.state }
  }

  /**
   * 获取连接状态
   */
  getConnectionState(): ConnectionState {
    return { ...this.state.connection }
  }

  /**
   * 获取播放状态
   */
  getPlaybackState(): PlaybackState {
    return { ...this.state.playback }
  }

  /**
   * 获取网络状态
   */
  getNetworkState(): NetworkState {
    return { ...this.state.network }
  }

  /**
   * 获取UI状态
   */
  getUiState(): UiState {
    return { ...this.state.ui }
  }

  /**
   * 更新连接状态
   */
  updateConnectionState(updates: Partial<ConnectionState>): void {
    this.state.connection = { ...this.state.connection, ...updates }
    this.notifyListeners()
  }

  /**
   * 更新播放状态
   */
  updatePlaybackState(updates: Partial<PlaybackState>): void {
    this.state.playback = { ...this.state.playback, ...updates }
    this.notifyListeners()
  }

  /**
   * 更新网络状态
   */
  updateNetworkState(updates: Partial<NetworkState>): void {
    this.state.network = { ...this.state.network, ...updates }
    this.notifyListeners()
  }

  /**
   * 更新UI状态
   */
  updateUiState(updates: Partial<UiState>): void {
    this.state.ui = { ...this.state.ui, ...updates }
    this.notifyListeners()
  }

  /**
   * 批量更新状态（优化性能，只通知一次）
   */
  updateState(updates: Partial<AppState>): void {
    if (updates.connection) {
      this.state.connection = { ...this.state.connection, ...updates.connection }
    }
    if (updates.playback) {
      this.state.playback = { ...this.state.playback, ...updates.playback }
    }
    if (updates.network) {
      this.state.network = { ...this.state.network, ...updates.network }
    }
    if (updates.ui) {
      this.state.ui = { ...this.state.ui, ...updates.ui }
    }
    this.notifyListeners()
  }

  /**
   * 订阅状态变化
   * @param key 订阅者唯一标识
   * @param listener 状态变化回调
   */
  subscribe(key: string, listener: StateChangeListener): void {
    this.listeners.set(key, listener)
    hilog.debug(DOMAIN, TAG, `Listener subscribed: ${key}, total: ${this.listeners.size}`)
  }

  /**
   * 取消订阅
   * @param key 订阅者唯一标识
   */
  unsubscribe(key: string): void {
    this.listeners.delete(key)
    hilog.debug(DOMAIN, TAG, `Listener unsubscribed: ${key}, total: ${this.listeners.size}`)
  }

  /**
   * 通知所有订阅者
   */
  private notifyListeners(): void {
    this.updateCount++
    const currentState = this.getState()

    hilog.debug(DOMAIN, TAG, `Notifying ${this.listeners.size} listeners (update #${this.updateCount})`)

    for (const [key, listener] of this.listeners.entries()) {
      try {
        listener(currentState)
      } catch (error) {
        hilog.error(DOMAIN, TAG, `Listener error (${key}): ${JSON.stringify(error)}`)
      }
    }
  }

  /**
   * 重置所有状态到默认值
   */
  reset(): void {
    this.state = createDefaultAppState()
    hilog.info(DOMAIN, TAG, 'State reset to default')
    this.notifyListeners()
  }

  /**
   * 重置播放状态（用于停止播放时）
   */
  resetPlaybackState(): void {
    this.state.playback = createDefaultPlaybackState()
    hilog.info(DOMAIN, TAG, 'Playback state reset')
    this.notifyListeners()
  }

  /**
   * 获取状态更新次数（用于调试）
   */
  getUpdateCount(): number {
    return this.updateCount
  }

  /**
   * 获取订阅者数量（用于调试）
   */
  getListenerCount(): number {
    return this.listeners.size
  }

  /**
   * 打印当前状态（用于调试）
   */
  logCurrentState(): void {
    hilog.info(DOMAIN, TAG, `=== Current State ===`)
    hilog.info(DOMAIN, TAG, `Connection: ${JSON.stringify(this.state.connection)}`)
    hilog.info(DOMAIN, TAG, `Playback: uri=${this.state.playback.uri.substring(0, 50)}..., pos=${this.state.playback.position}/${this.state.playback.duration}, playing=${this.state.playback.isPlaying}`)
    hilog.info(DOMAIN, TAG, `Network: wifi=${this.state.network.hasWifi}, status=${this.state.network.networkStatus}`)
    hilog.info(DOMAIN, TAG, `UI: refreshing=${this.state.ui.isRefreshing}, error=${this.state.ui.errorMessage}`)
    hilog.info(DOMAIN, TAG, `====================`)
  }
}
