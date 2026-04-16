/**
 * DLNA HTTP控制服务器
 * 鸿蒙手机端DLNA DMR - HTTP控制协议实现
 * 处理DLNA控制点发送的SOAP命令
 */
import { socket } from '@kit.NetworkKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { hilog } from '@kit.PerformanceAnalysisKit'

const DOMAIN = 0x0000
const TAG = 'DlnaHttpServer'

export class DlnaHttpServer {
  private tcpSocket: socket.TCPSocket | null = null
  private isRunning: boolean = false

  // 播放回调
  private onPlayCallback: ((uri: string, httpHeaders: Record<string, string>) => void) | null = null
  private onPauseCallback: (() => void) | null = null
  private onStopCallback: (() => void) | null = null
  private onSeekCallback: ((position: number) => void) | null = null

  // 当前播放URI
  private currentUri: string = ''

  // 当前HTTP头
  private currentHttpHeaders: Record<string, string> = {}

  /**
   * 设置播放回调
   */
  setPlayCallback(callback: (uri: string, httpHeaders: Record<string, string>) => void): void {
    this.onPlayCallback = callback
  }

  setPauseCallback(callback: () => void): void {
    this.onPauseCallback = callback
  }

  setStopCallback(callback: () => void): void {
    this.onStopCallback = callback
  }

  setSeekCallback(callback: (position: number) => void): void {
    this.onSeekCallback = callback
  }

  /**
   * 启动HTTP服务器
   */
  async start(): Promise<void> {
    if (this.isRunning) {
      hilog.warn(DOMAIN, TAG, 'DlnaHttpServer已经在运行')
      return
    }

    try {
      this.isRunning = true

      // 创建TCP套接字作为HTTP服务器
      this.tcpSocket = socket.constructTCPSocketInstance()

      // 订阅消息事件（接收HTTP请求）
      this.tcpSocket.on('message', (value: socket.SocketMessageInfo) => {
        this.handleHttpRequest(value.message)
      })

      this.tcpSocket.on('error', (err: BusinessError) => {
        hilog.error(DOMAIN, TAG, `HTTP服务器错误: ${err.message}`)
      })

      // 绑定到本地端口
      const localAddress: socket.NetAddress = {
        address: "0.0.0.0",
        port: 49153,
        family: 1
      }

      await this.tcpSocket.bind(localAddress)
      hilog.info(DOMAIN, TAG, 'DLNA HTTP服务器已绑定到端口49153')

      // 开始监听
      // 注意：鸿蒙的TCPSocket使用connect来接受连接
      // 这里我们使用简化的方式，直接监听消息

    } catch (error) {
      hilog.error(DOMAIN, TAG, `启动DLNA HTTP服务器失败: ${(error as BusinessError).message}`)
      this.isRunning = false
    }
  }

  /**
   * 处理HTTP请求
   */
  private handleHttpRequest(data: ArrayBuffer): void {
    try {
      const request = this.arrayBufferToString(data)
      hilog.debug(DOMAIN, TAG, `收到HTTP请求: ${request.substring(0, 100)}...`)

      // 解析HTTP请求
      const lines = request.split('\r\n')
      const requestLine = lines[0]
      const parts = requestLine.split(' ')
      const method = parts[0]
      const url = parts[1]

      if (url === '/control' && method === 'POST') {
        // 处理SOAP命令
        let soapAction = ''
        let body = ''

        for (let i = 0; i < lines.length; i++) {
          const line = lines[i]
          if (line.toLowerCase().startsWith('soapaction:')) {
            soapAction = line.substring(11).trim()
          }
          if (line === '') {
            // 空行后是body
            body = lines.slice(i + 1).join('\r\n')
            break
          }
        }

        this.parseSoapCommand(soapAction, body).then((response) => {
          this.sendHttpResponse(response)
        })
      } else if (url === '/description.xml') {
        // 返回设备描述文件
        this.sendHttpResponse(this.getDescriptionXml())
      } else {
        // 404
        this.sendHttpResponse('HTTP/1.1 404 Not Found\r\n\r\n')
      }

    } catch (error) {
      hilog.error(DOMAIN, TAG, `处理HTTP请求失败: ${(error as BusinessError).message}`)
    }
  }

  /**
   * ArrayBuffer转字符串
   */
  private arrayBufferToString(buffer: ArrayBuffer): string {
    const dataView = new DataView(buffer)
    let str = ''
    for (let i = 0; i < dataView.byteLength; ++i) {
      str += String.fromCharCode(dataView.getUint8(i))
    }
    return str
  }

  /**
   * 发送HTTP响应
   */
  private async sendHttpResponse(body: string): Promise<void> {
    if (!this.tcpSocket) {
      return
    }

    try {
      const response = `HTTP/1.1 200 OK\r\nContent-Type: text/xml; charset=utf-8\r\n\r\n${body}`

      const sendOptions: socket.TCPSendOptions = {
        data: response
      }
      await this.tcpSocket.send(sendOptions)
    } catch (error) {
      hilog.error(DOMAIN, TAG, `发送HTTP响应失败: ${(error as BusinessError).message}`)
    }
  }

  /**
   * 解析SOAP命令
   */
  private async parseSoapCommand(soapAction: string, body: string): Promise<string> {
    if (!soapAction) {
      return this.getErrorResponse("Missing SOAPACTION header")
    }

    hilog.debug(DOMAIN, TAG, `SOAP命令: ${soapAction}`)

    try {
      if (soapAction.includes("#SetAVTransportURI")) {
        // 设置播放URI
        const uri = this.extractValue(body, "CurrentURI")
        const metadata = this.extractValue(body, "CurrentURIMetaData")

        this.currentUri = uri
        hilog.info(DOMAIN, TAG, `设置播放URI: ${uri.substring(0, 100)}...`)

        // 解析元数据中的HTTP头
        const httpHeaders = this.extractHttpHeadersFromMetadata(metadata)
        if (Object.keys(httpHeaders).length > 0) {
          hilog.info(DOMAIN, TAG, `提取到HTTP头: ${JSON.stringify(httpHeaders)}`)
        }

        // 通知主页面开始播放（传递HTTP头）
        this.notifyPlay(uri, httpHeaders)

        return this.getSuccessResponse("SetAVTransportURI")
      } else if (soapAction.includes("#Play")) {
        // 播放命令（不带参数，使用当前URI）
        hilog.info(DOMAIN, TAG, '收到Play命令')
        this.onPlayCallback?.(this.currentUri, this.currentHttpHeaders)
        return this.getSuccessResponse("Play")
      } else if (soapAction.includes("#Pause")) {
        // 暂停命令
        hilog.info(DOMAIN, TAG, '收到Pause命令')
        this.onPauseCallback?.()
        return this.getSuccessResponse("Pause")
      } else if (soapAction.includes("#Stop")) {
        // 停止命令
        hilog.info(DOMAIN, TAG, '收到Stop命令')
        this.onStopCallback?.()
        return this.getSuccessResponse("Stop")
      } else if (soapAction.includes("#Seek")) {
        // Seek命令
        const target = this.extractValue(body, "Target")
        const position = parseInt(target) || 0
        hilog.info(DOMAIN, TAG, `收到Seek命令: ${position}ms`)
        this.onSeekCallback?.(position)
        return this.getSuccessResponse("Seek")
      } else if (soapAction.includes("#GetTransportInfo")) {
        // 获取传输状态
        return `
<u:GetTransportInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
  <CurrentTransportState>PLAYING</CurrentTransportState>
  <CurrentTransportStatus>OK</CurrentTransportStatus>
  <CurrentSpeed>1</CurrentSpeed>
</u:GetTransportInfoResponse>
`
      } else if (soapAction.includes("#GetPositionInfo")) {
        // 获取播放位置
        return `
<u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
  <TrackDuration>00:00:00</TrackDuration>
  <RelTime>00:00:00</RelTime>
  <AbsTime>00:00:00</AbsTime>
</u:GetPositionInfoResponse>
`
      }

      // 未知命令
      hilog.warn(DOMAIN, TAG, `未知SOAP命令: ${soapAction}`)
      return this.getErrorResponse("Unknown action")

    } catch (error) {
      hilog.error(DOMAIN, TAG, `处理SOAP命令失败: ${(error as BusinessError).message}`)
      return this.getErrorResponse("Internal error")
    }
  }

  /**
   * 从XML中提取标签值
   */
  private extractValue(xml: string, tagName: string): string {
    const regex = new RegExp(`<${tagName}>([^<]+)</${tagName}>`)
    const match = xml.match(regex)
    return match ? match[1] : ""
  }

  /**
   * 通知主页面播放
   */
  private notifyPlay(uri: string, httpHeaders: Record<string, string> = {}): void {
    // 保存当前HTTP头
    this.currentHttpHeaders = httpHeaders

    // 通过AppStorage通知主页面
    AppStorage.setOrCreate("dlna_uri", uri)
    AppStorage.setOrCreate("dlna_http_headers", httpHeaders)

    // 调用回调
    this.onPlayCallback?.(uri, httpHeaders)
  }

  /**
   * 成功响应
   */
  private getSuccessResponse(action: string): string {
    return `
<u:${action}Response xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
</u:${action}Response>
`
  }

  /**
   * 错误响应
   */
  private getErrorResponse(error: string): string {
    return `
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
  <s:Body>
    <s:Fault>
      <faultcode>500</faultcode>
      <faultstring>${error}</faultstring>
    </s:Fault>
  </s:Body>
</s:Envelope>
`
  }

  /**
   * 获取设备描述XML
   */
  private getDescriptionXml(): string {
    const deviceUuid = "583f8100-1de2-11db-8981-000c298458a8"
    return `
<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
  <specVersion>
    <major>1</major>
    <minor>0</minor>
  </specVersion>
  <device>
    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
    <friendlyName>FSCast Remote</friendlyName>
    <manufacturer>FSCast</manufacturer>
    <manufacturerURL>https://github.com/yourusername/FloatingScreenCasting</manufacturerURL>
    <modelDescription>FSCast Remote for HarmonyOS</modelDescription>
    <modelName>FSCast Remote</modelName>
    <modelNumber>1.0</modelNumber>
    <modelURL>https://github.com/yourusername/FloatingScreenCasting</modelURL>
    <serialNumber>12345678</serialNumber>
    <UDN>uuid:${deviceUuid}</UDN>
    <serviceList>
      <service>
        <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
        <SCPDURL>/AVTransport-scpd.xml</SCPDURL>
        <controlURL>/control</controlURL>
        <eventSubURL>/event</eventSubURL>
      </service>
      <service>
        <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
        <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
        <SCPDURL>/RenderingControl-scpd.xml</SCPDURL>
        <controlURL>/control</controlURL>
        <eventSubURL>/event</eventSubURL>
      </service>
    </serviceList>
  </device>
</root>
`
  }

  /**
   * 停止HTTP服务器
   */
  async stop(): Promise<void> {
    hilog.info(DOMAIN, TAG, '正在停止DLNA HTTP服务器...')

    this.isRunning = false

    if (this.tcpSocket) {
      try {
        this.tcpSocket.off('message')
        this.tcpSocket.off('connect')
        this.tcpSocket.off('close')
        this.tcpSocket.off('error')
        await this.tcpSocket.close()
      } catch (error) {
        hilog.error(DOMAIN, TAG, `关闭HTTP服务器失败: ${(error as BusinessError).message}`)
      }
      this.tcpSocket = null
    }

    hilog.info(DOMAIN, TAG, 'DLNA HTTP服务器已停止')
  }

  /**
   * 从元数据中提取HTTP头
   */
  private extractHttpHeadersFromMetadata(metadata: string): Record<string, string> {
    const httpHeaders: Record<string, string> = {}

    if (!metadata || metadata.trim().length === 0) {
      return httpHeaders
    }

    try {
      // 查找custom标签
      const customMatch = metadata.match(/<custom[^>]*xmlns="http:\/\/www\.fscast\.com\/headers"[^>]*>(.*?)<\/custom>/s)
      if (customMatch && customMatch[1]) {
        const headersJson = customMatch[1].trim()
        hilog.debug(DOMAIN, TAG, `提取到HTTP头JSON: ${headersJson}`)

        // 解析JSON
        const headersObj: Record<string, string> = JSON.parse(headersJson) as Record<string, string>

        // 手动复制常见的HTTP头
        const commonHeaders = ['user-agent', 'referer', 'cookie', 'accept', 'accept-language']
        for (let i = 0; i < commonHeaders.length; i++) {
          const key = commonHeaders[i]
          if (headersObj[key] !== undefined) {
            httpHeaders[key] = headersObj[key]
          }
        }
      }
    } catch (error) {
      hilog.error(DOMAIN, TAG, `解析HTTP头失败: ${(error as BusinessError).message}`)
    }

    return httpHeaders
  }
}
