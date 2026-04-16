import { AbilityConstant, ConfigurationConstant, UIAbility, Want, abilityAccessCtrl, Permissions, common } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { window } from '@kit.ArkUI';
import { BusinessError } from '@kit.BasicServicesKit';

const DOMAIN = 0x0000;
const TAG = 'EntryAbility';

// 需要申请的权限列表
const PERMISSIONS: Permissions[] = [
  'ohos.permission.INTERNET',
  'ohos.permission.GET_NETWORK_INFO',
  'ohos.permission.KEEP_BACKGROUND_RUNNING',
  'ohos.permission.ACCESS_BLUETOOTH',
  'ohos.permission.USE_BLUETOOTH',
  'ohos.permission.DISCOVER_BLUETOOTH'
];

export default class EntryAbility extends UIAbility {
  private windowStage: window.WindowStage | null = null

  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    try {
      this.context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET);
    } catch (err) {
      hilog.error(DOMAIN, 'testTag', 'Failed to set colorMode. Cause: %{public}s', JSON.stringify(err));
    }
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onCreate');
  }

  onDestroy(): void {
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onDestroy');
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    // Main window is created, set main page for this ability
    hilog.info(DOMAIN, TAG, '%{public}s', 'Ability onWindowStageCreate');

    // 保存 windowStage 引用
    this.windowStage = windowStage

    // 设置全屏沉浸式布局
    try {
      let windowClass = windowStage.getMainWindowSync();
      windowClass.setWindowLayoutFullScreen(true);
      windowClass.setWindowBackgroundColor('#0F172A');
      // 设置屏幕常亮（防止锁屏）
      windowClass.setWindowKeepScreenOn(true);
      hilog.info(DOMAIN, TAG, 'Set keep screen on: true');
    } catch (err) {
      hilog.error(DOMAIN, TAG, 'Failed to set fullscreen. Cause: %{public}s', JSON.stringify(err));
    }

    windowStage.loadContent('pages/Index', (err) => {
      if (err.code) {
        hilog.error(DOMAIN, TAG, 'Failed to load the content. Cause: %{public}s', JSON.stringify(err));
        return;
      }
      hilog.info(DOMAIN, TAG, 'Succeeded in loading the content.');

      // 页面加载完成后请求权限
      this.requestPermissions();
    });
  }

  /**
   * 请求运行时权限
   */
  private requestPermissions(): void {
    try {
      const atManager: abilityAccessCtrl.AtManager = abilityAccessCtrl.createAtManager();

      hilog.info(DOMAIN, TAG, 'Requesting permissions from user...');

      atManager.requestPermissionsFromUser(this.context, PERMISSIONS)
        .then((data) => {
          const grantStatus: number[] = data.authResults;
          const length: number = grantStatus.length;

          for (let i = 0; i < length; i++) {
            if (grantStatus[i] === 0) {
              // 用户授权
              hilog.info(DOMAIN, TAG, `${PERMISSIONS[i]} is granted by user.`);
            } else {
              // 用户拒绝授权
              hilog.warn(DOMAIN, TAG, `${PERMISSIONS[i]} is denied by user.`);
            }
          }

          hilog.info(DOMAIN, TAG, 'Permission request completed.');
        })
        .catch((err: BusinessError) => {
          hilog.error(DOMAIN, TAG, `Failed to request permissions. code: ${err.code}, message: ${err.message}`);
        });
    } catch (err) {
      hilog.error(DOMAIN, TAG, `Request permissions error: ${JSON.stringify(err)}`);
    }
  }

  onWindowStageDestroy(): void {
    // Main window is destroyed, release UI related resources
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onWindowStageDestroy');
    this.windowStage = null
  }

  onForeground(): void {
    // Ability has brought to foreground
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onForeground');

    // 重新设置屏幕常亮（确保生效）
    if (this.windowStage) {
      try {
        let windowClass = this.windowStage.getMainWindowSync();
        windowClass.setWindowKeepScreenOn(true);
        hilog.info(DOMAIN, 'testTag', 'Reset keep screen on: true');
      } catch (err) {
        hilog.error(DOMAIN, 'testTag', 'Failed to reset keep screen on. Cause: %{public}s', JSON.stringify(err));
      }
    }
  }

  onBackground(): void {
    // Ability has back to background
    hilog.info(DOMAIN, 'testTag', '%{public}s', 'Ability onBackground');
  }
}
