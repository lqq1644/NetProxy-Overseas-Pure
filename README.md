<p align="center">
  <img src="src/module/webroot/favicon.png" alt="NetPure Logo" width="120" />
</p>

<h1 align="center">NetPure（海外纯净分应用定制版）</h1>

<p align="center">
  <strong>为海外用户做的 sing-box 透明代理：国内应用走国内节点，海外应用走海外节点，其余全部直连</strong><br />
  eBPF / TUN · 分应用专属节点 · 代理全局模式 · 海外纯净默认
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Core-sing--box-blueviolet?style=flat-square" alt="sing-box Core" />
  <a href="https://github.com/lqq1644/NetPure/stargazers"><img src="https://img.shields.io/github/stars/lqq1644/NetPure?style=flat-square" alt="GitHub stars" /></a>
</p>

<p align="center">
  <a href="https://github.com/lqq1644/NetPure/releases">下载模块与管理器</a> ·
  <a href="https://github.com/lqq1644/NetPure/issues">问题反馈</a> ·
  <a href="src/android/">管理器源码</a>
</p>

---

## 项目简介

NetPure 是 [Fanju6/NetProxy-Magisk](https://github.com/Fanju6/NetProxy-Magisk) 的 fork（基于 v8.6.1），作者 maracucho ly，许可证仍为 [GPL-3.0](LICENSE)。它保留了 NetProxy 的核心优点——sing-box 内核、eBPF / TUN 透明代理、分应用代理、节点与订阅、测速与诊断——同时把设置做简单，专门给海外用户用。

人在海外，手机里往往两边都要用：国内的政务、银行、购物要走国内网络，海外的工作、社交、AI 要走海外节点，剩下的日常应用又不希望绕路降速。NetPure 就是为这种用法做的：**每个应用单独指定节点，没指定的默认直连**，不用再为一套规则反复横跳。

这个版本是按海外用户的痛点改出来的，改动本身就是优点：

- **首页就能设，不用钻菜单**：以前分应用要走设置 → 入站设置 → 应用代理三步，现在首页一点直达分应用选择；去广告开关也在首页，不用再翻设置。
- **三档出站，一句话讲清**：全局代理 / 应用分流 / 直连，默认应用分流；不用再记 Rule、RuleAllowAds 这类名字。
- **开箱即直连，不吓人**：默认空白名单等于全直连，一个应用一个应用慢慢加，不会一开启就全机走代理。
- **新手默认不再踩坑**：本机网络 IPv6 接管默认关闭，节点不支持 IPv6 时不再因此失败；已装用户原有开关状态不变。
- **海外纯净默认**：没有为国内环境准备的 GFW / geo 分流规则，没有国内 DNS；节点域名解析走无 detour 的 Cloudflare 直连，不经过代理自身（避免解析自环）。
- **防护默认开着**：QUIC 与 UDP 443 拦截（防止浏览器 QUIC 旁路代理）、私网旁路；双栈站点正常打开。
- **整机出海一键切**：出站切到“代理”模式，全机走主节点；切回“规则”，分应用设置原样回来。
- **名字和图标统一**：管理器、模块控制中心、仓库首页都叫 NetPure、都用猫图标，不再新旧混用。

## 出站模式：四种，一句话讲清

- **代理**：真全局。无视白名单/黑名单与所有绑定，全机走主节点。适合整机出海时用。
- **规则**（日常推荐）：按绑定走——绑了节点的应用走指定节点，没绑定的直连；广告拦截默认开启。
- **规则（允许广告）**：跟规则一样，只是不拦截广告。适合某个应用因广告规则误伤打不开时临时绕过。
- **直连**：全机直连，等同软件关闭；白名单与绑定都不起作用。

白名单与绑定只在“规则”与“规则（允许广告）”模式下生效；“代理”与“直连”模式下它们不起作用（直连本来就全直连，代理本来就全走主节点）。

## 海外纯净默认：没有为国内环境准备的东西

- **无 GFW / geo 分流**：默认配置不含 geosite/geoip 规则集与分流规则，没有为翻墙准备的冗余规则，规则链干净。
- **无国内 DNS**：DNS 仅保留 Cloudflare/Google，节点域名解析走无 detour 的 Cloudflare 直连，不经过任何国内解析，也不经过代理自身（避免解析自环）。
- **协议防护默认开启**：QUIC 与 UDP 443 拦截（防止浏览器 QUIC 旁路代理）、私网旁路（局域网流量不进节点）；IPv6 已修复，双栈站点（如 `www.google.com`）正常打开。
- **分应用策略默认白名单（空名单）**：刚装好时等于全直连，一个应用一个应用慢慢加，不会一开启就全机走代理。
- **本机网络 IPv6 接管默认关闭**：新装默认不接管 IPv6，节点不支持 IPv6 时不再因此失败；已装用户原有开关状态不变。

## 分应用绑定：三处都能设

- 管理器：白名单条目内专属节点选择器（跟随主节点 / 指定节点 / 直连）。
- 模块 WebUI 与 CLI：`app bind <用户ID>:<包名> <出站>` / `app unbind`。
- 绑定编译为 `package_name` 路由规则，插在防护规则之后；多用户同包名以首条绑定为准；指向不存在出站的绑定启动时跳过并告警。

## 安装与开始使用

运行环境为 `arm64-v8a` Android 设备与 Magisk、KernelSU 或 APatch，使用 Root 权限接管流量。默认使用 eBPF，可在“设置 → 入站设置”中选择 TUN。

1. 从 [Releases](https://github.com/lqq1644/NetPure/releases) 下载 `NetProxy_<版本>_<构建号>.zip`，内含模块和 Android 管理器。
2. 在 Root 管理器中刷入，按提示选择数据保留方式及是否安装管理器；等待安装完成。
3. 打开 Android 管理器，自动连接模块，在“节点”页导入节点，或在“订阅”页添加订阅。
4. 日常使用选“规则”模式，在白名单里给应用绑节点；整机出海时切“代理”模式并选主节点。

服务默认手动启动，开机自启可在“设置”中开启。

## 与上游的差异（定制版）

- **分应用专属节点绑定**：`inbound/app` 分区新增 `bindings`，每个白名单应用可单独指定出站节点；管理器（白名单条目内专属节点选择器）、模块 WebUI 与 CLI 三端口径一致。「跟随主节点」不写绑定记录，而是直接不保留该应用的绑定。
- **Catalog 单节点顶层展开**：运行时 outbounds 除 `Auto/<组>`、`Select/<组>`、`Proxy` 外，还把各分组的单节点与 endpoints 展开为顶层出站，绑定可直接指向节点 tag；标签冲突按 `NormalizeTags` 规则追加 `_2`、`_3` 消歧。
- **代理全局模式**：出站“代理”模式下，构建运行时配置时关闭分应用策略（入站不做 UID 过滤、运行配置不拼绑定规则），全部流量落到代理规则走主节点；防护规则不受影响；持久化配置只读不写，切回“规则”模式时白名单与绑定完整恢复。
- **海外纯净默认预设**：默认配置不再包含 geosite/geoip 规则集与分流规则，`route.final` 与 Rule 兜底均为直连，DNS 仅保留 Cloudflare/Google 并使用 `ipv4_only`，入站 `root_policy` 默认为 `exclude`，分应用策略默认开启白名单（空名单，等于全直连）。
- **DNS 与 IPv6 修复**：节点域名解析改无 detour 的 Cloudflare 直连（修复经代理自身解析导致的自环）；删除 IPv6 拒绝规则，IPv6 流量与 IPv4 一样走节点（无 IPv6 出口的节点由浏览器自动回落 IPv4）。
- **内置协议防护**：主配置在 hijack-dns 规则后内置 QUIC 拒绝与 UDP 443 拒绝两条规则；分应用绑定规则插入位置位于防护规则之后，避免被绑定应用的 QUIC 流量绕过防护先进入节点。

## 使用文档

本 fork 的行为以本 README 与本仓库的自有文件为准，不以上游文档站为准：

- 行为与模式：以本 README 的出站模式、分应用绑定、海外纯净默认与与上游的差异为准
- 开发流程与验证方法：[贡献指南](CONTRIBUTING.md)
- 架构与跨组件契约：[AGENTS.md](AGENTS.md)
- 管理器构建：[Android 说明](src/android/README.md)
- 变更记录：[变更记录](docs/changelog.md)

## 主要功能

- **透明代理**：eBPF 或 TUN 接管 TCP、UDP 与 DNS，支持热点和 USB 共享网络。
- **节点与订阅**：导入节点链接、节点文本、Clash YAML 和 sing-box JSON；支持订阅筛选、请求头、流量信息与自动更新。
- **节点选择与测速**：手动选择或自动优选，服务停止时也可测速。
- **分应用代理**：黑名单 / 白名单，按 Android 用户分别配置，支持应用分身；管理器首页可直接进入分应用选择，去广告开关也在首页。
- **网络策略**：规则、代理、直连与规则（允许广告）模式，按 Wi-Fi 名称和实际出口自动切换。
- **一键全局**：出站切到“代理”模式，整机流量走主节点；切回“规则”，白名单与绑定完整恢复。
- **海外纯净默认**：无 GFW / geo 分流规则，无国内 DNS；本机网络 IPv6 接管默认关闭。
- **配置与诊断**：编辑 sing-box 配置、本地规则，查看运行时配置与日志，导出脱敏诊断包。

## 鸣谢

| 项目 | 用途 |
|------|------|
| [Fanju6/NetProxy-Magisk](https://github.com/Fanju6/NetProxy-Magisk) | 上游原作者，本 fork 的基础 |
| [reF1nd/sing-box](https://github.com/reF1nd/sing-box) | 当前代理核心 |
| [SagerNet/sing-box](https://github.com/SagerNet/sing-box) | 上游 sing-box 项目 |
| [Proxylink](https://github.com/Fanju6/Proxylink) | NetProxy 内部节点转换能力的原始项目 |
| [AsteriskNG](https://github.com/Asterisk4Magisk/AsteriskNG) | Android eBPF 实现参考 |
| [v2rayNG](https://github.com/2dust/v2rayNG) | 节点解析实现参考 |

同时感谢曾为早期版本提供核心能力或实现参考的 [CHIZI-0618/sing-box](https://github.com/CHIZI-0618/sing-box)、[Xray-core](https://github.com/XTLS/Xray-core)、[AndroidTProxyShell](https://github.com/CHIZI-0618/AndroidTProxyShell)、[IPSET_LKM](https://github.com/TanakaLun/IPSET_LKM) 和 [KsuWebUIStandalone](https://github.com/KOWX712/KsuWebUIStandalone)。

## Star

如果 NetPure 对你有用，欢迎点 Star——下面的数字就是本仓库的实时数据：

<p>
  <a href="https://github.com/lqq1644/NetPure/stargazers"><img src="https://img.shields.io/github/stars/lqq1644/NetPure?style=flat-square" alt="GitHub stars" /></a>
</p>

## 许可证

[GPL-3.0 License](LICENSE)
