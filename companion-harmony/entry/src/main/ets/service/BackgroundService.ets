/**
 * 后台服务管理器
 * 使用 ContinuousTask 保持音频播放
 */
import { backgroundTaskManager } from '@kit.BackgroundTasksKit'
import { wantAgent, WantAgent } from '@kit.AbilityKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { hilog } from '@kit.PerformanceAnalysisKit'

const TAG = 'BackgroundService'
const DOMAIN = 0x0000
const BACKGROUND_TASK_ID = 'audio_playback_task'

export class BackgroundService {
  private context: Context | null = null
  private isRunning: boolean = false
  private delayTimeInfo: backgroundTaskManager.DelaySuspendInfo | null = null

  setContext(context: Context): void {
    this.context = context
  }

  /**
   * 启动持续任务 + 通知栏
   */
  async start(): Promise<void> {
    if (this.isRunning || !this.context) {
      hilog.info(DOMAIN, TAG, `Already running or no context, isRunning=${this.isRunning}`)
      return
    }

    try {
      hilog.info(DOMAIN, TAG, 'Starting background service...')

      // 申请持续后台任务（支持长时间运行）
      let wantAgentInfo: wantAgent.WantAgentInfo = {
        wants: [
          {
            bundleName: 'com.example.fscastremote',
            abilityName: 'EntryAbility'
          }
        ],
        actionType: wantAgent.OperationType.START_ABILITY,
        requestCode: 0
      }

      const wantAgentObj: WantAgent = await wantAgent.getWantAgent(wantAgentInfo)
      await backgroundTaskManager.startBackgroundRunning(
        this.context,
        backgroundTaskManager.BackgroundMode.AUDIO_PLAYBACK,
        wantAgentObj
      )

      // 3. 申请短时延期（作为补充，避免频繁被挂起）
      this.requestSuspendDelay()

      this.isRunning = true
      hilog.info(DOMAIN, TAG, 'Background service started successfully')
    } catch (error) {
      hilog.error(DOMAIN, TAG, 'Start failed: ' + (error as BusinessError).message)
      // 即使失败也标记为运行，依靠延期机制保持后台
      this.isRunning = true
    }
  }

  /**
   * 申请短时延期
   */
  private requestSuspendDelay(): void {
    if (!this.context) return

    try {
      // 申请延期，超时回调后再次申请（保持后台运行）
      this.delayTimeInfo = backgroundTaskManager.requestSuspendDelay('AudioPlayback', () => {
        hilog.info(DOMAIN, TAG, 'Suspend delay expired, requesting again')
        this.requestSuspendDelay()
      })
      hilog.info(DOMAIN, TAG, `Suspend delay requested`)
    } catch (error) {
      hilog.error(DOMAIN, TAG, 'Request suspend delay failed: ' + (error as BusinessError).message)
    }
  }

  /**
   * 取消延期
   */
  private cancelSuspendDelay(): void {
    if (this.delayTimeInfo) {
      try {
        backgroundTaskManager.cancelSuspendDelay(this.delayTimeInfo.requestId)
        this.delayTimeInfo = null
        hilog.info(DOMAIN, TAG, 'Suspend delay cancelled')
      } catch (error) {
        hilog.error(DOMAIN, TAG, 'Cancel suspend delay failed: ' + (error as BusinessError).message)
      }
    }
  }

  /**
   * 停止持续任务
   */
  async stop(): Promise<void> {
    if (!this.isRunning || !this.context) return

    try {
      // 取消延期
      this.cancelSuspendDelay()

      // 停止后台任务
      try {
        await backgroundTaskManager.stopBackgroundRunning(this.context)
        hilog.info(DOMAIN, TAG, 'Background task stopped')
      } catch (e) {
        hilog.warn(DOMAIN, TAG, 'Stop background task failed: ' + (e as Error).message)
      }

      this.isRunning = false
      hilog.info(DOMAIN, TAG, 'Background service stopped')
    } catch (error) {
      hilog.error(DOMAIN, TAG, 'Stop failed: ' + (error as BusinessError).message)
    }
  }

  getIsRunning(): boolean {
    return this.isRunning
  }
}
