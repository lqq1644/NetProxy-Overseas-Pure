import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { test } from 'node:test'

const root = new URL('../', import.meta.url)
const readJSON = path => JSON.parse(readFileSync(new URL(path, root), 'utf8'))
const config = readJSON('src/module/config/singbox/config.json')
const managed = readJSON('src/module/config/inbound/inbound.json')
const upstream = readJSON('tests/fixtures/singbox-upstream.json')
const resources = readJSON('.github/resources.json').raw
const list = value => value === undefined ? [] : Array.isArray(value) ? value : [value]

test('模块设置只有 module.json 默认文件，字段使用 JSON 原生类型', () => {
  assert.equal(existsSync(new URL('src/module/config/module.conf', root)), false)
  assert.deepEqual(readJSON('src/module/config/module.json'), {
    auto_start: false,
    selection: { group_id: 'default', node_tag: '' },
    wifi: { enabled: false, mode: 'blacklist', blacklist: [], whitelist: [], proxy_on_non_wifi: true },
  })
})

test('默认配置仅保留部署、运行时生成、模式命名、原生 DNS 响应匹配与海外纯净差异', () => {
  const expected = structuredClone(upstream)
  expected.log.output = 'stderr'
  expected.experimental.cache_file.path = '/data/adb/modules/netproxy/config/singbox/cache.db'
  expected.experimental.clash_api.external_controller = '127.0.0.1:9999'
  delete expected.experimental.clash_api.external_ui
  delete expected.experimental.clash_api.external_ui_download_url
  expected.services[0].listen = '127.0.0.1'
  expected.services[0].listen_port = 9090
  expected.services[0].dashboard.path = '/data/adb/modules/netproxy/webroot/sing-box-dashboard'
  expected.inbounds = expected.inbounds.filter(inbound => inbound.type !== 'ebpf')
  expected.inbounds[0].listen = '127.0.0.1'
  delete expected.outbounds
  delete expected.providers
  for (const rule of expected.route.rule_set) {
    rule.path = rule.path.replace('./source/rule_set/', './rules/remote/').replace('./source/', './rules/local/')
  }
  const renameModes = rules => {
    for (const rule of rules) {
      if (rule.clash_mode === 'Global') rule.clash_mode = 'Proxy'
      if (rule.clash_mode === 'AllowAds') rule.clash_mode = 'RuleAllowAds'
      if (rule.rules) renameModes(rule.rules)
    }
  }
  renameModes(expected.dns.rules)
  renameModes(expected.route.rules)
  const matchLocalResponses = rules => {
    for (const rule of rules) {
      if (list(rule.rule_set).some(tag => ['proxy', 'direct', 'block'].includes(tag))) rule.match_response = true
      if (rule.rules) matchLocalResponses(rule.rules)
    }
  }
  matchLocalResponses(expected.dns.rules)
  expected.dns.rules.splice(2, 0, { action: 'evaluate', server: 'dns-proxy' })
  expected.route.rules.push({ clash_mode: 'Rule', action: 'route', outbound: expected.route.final })
  const directIndex = expected.route.rules.findIndex(rule => rule.clash_mode === 'Direct')
  expected.route.rules.unshift(...expected.route.rules.splice(directIndex, 1))
  // 海外纯净差异：默认配置不引用 geosite/geoip 远程规则与大陆 DNS，删除相关服务器与分流规则；
  // DNS 只保留 dns-proxy 组，Direct 模式的 DNS 规则随组删除改指 dns-proxy，去广告只用内置与 AWAvenue 规则集。
  // 出站域名解析不用经节点的 dns-proxy，改用无 detour 的 Cloudflare 直连 dns-direct，避免域名节点解析自环。
  expected.dns.servers = expected.dns.servers.filter(server => !['dns-direct', 'ali', 'tencent'].includes(server.tag))
  expected.dns.servers.push({ tag: 'dns-direct', type: 'https', server: 'cloudflare-dns.com', domain_resolver: 'hosts' })
  const hosts = expected.dns.servers.find(server => server.type === 'hosts')
  delete hosts.predefined['doh.pub']
  delete hosts.predefined['dns.alidns.com']
  const referencesGeo = rule => list(rule.rule_set).some(tag => tag.startsWith('geosite/') || tag.startsWith('geoip/'))
  expected.dns.rules = expected.dns.rules.filter(rule => !referencesGeo(rule))
  expected.route.rules = expected.route.rules.filter(rule => !referencesGeo(rule))
  const stripGeoAds = rules => {
    for (const rule of rules) {
      if (Array.isArray(rule.rule_set)) rule.rule_set = rule.rule_set.filter(tag => tag !== 'geosite/category-ads-all')
      if (rule.rules) stripGeoAds(rule.rules)
    }
  }
  stripGeoAds(expected.dns.rules)
  stripGeoAds(expected.route.rules)
  for (const rule of expected.dns.rules) {
    if (rule.clash_mode === 'Direct') rule.server = 'dns-proxy'
  }
  expected.dns.strategy = 'ipv4_only'
  expected.route.rule_set = expected.route.rule_set.filter(rule => !list(rule.tag).some(tag => tag.startsWith('geosite/') || tag.startsWith('geoip/')))
  const hijackIndex = expected.route.rules.findIndex(rule => rule.action === 'hijack-dns')
  expected.route.rules.splice(hijackIndex + 1, 0,
    { protocol: 'quic', action: 'reject' },
    { network: 'udp', port: 443, action: 'reject' })
  expected.route.default_domain_resolver = 'dns-direct'
  expected.route.final = 'direct'
  expected.route.rules.at(-1).outbound = 'direct'
  assert.deepEqual(config, expected)
})

test('海外纯净默认不引用 geosite/geoip 与大陆 DNS，兜底直连并内置协议防护', () => {
  const ruleSetTags = config.route.rule_set.flatMap(rule => list(rule.tag))
  assert.ok(ruleSetTags.every(tag => !tag.startsWith('geosite/') && !tag.startsWith('geoip/')))
  const collectRuleSets = rules => rules.flatMap(rule => [...list(rule.rule_set), ...collectRuleSets(rule.rules ?? [])])
  for (const tag of [...collectRuleSets(config.route.rules), ...collectRuleSets(config.dns.rules)]) {
    assert.ok(!tag.startsWith('geosite/') && !tag.startsWith('geoip/'), tag)
  }
  const hijackIndex = config.route.rules.findIndex(rule => rule.action === 'hijack-dns')
  assert.deepEqual(config.route.rules.slice(hijackIndex + 1, hijackIndex + 3), [
    { protocol: 'quic', action: 'reject' },
    { network: 'udp', port: 443, action: 'reject' },
  ])
  assert.equal(config.route.final, 'direct')
  assert.equal(config.route.default_domain_resolver, 'dns-direct')
  assert.deepEqual(config.dns.servers.map(server => server.tag), ['dns-proxy', 'hosts', 'cloudflare', 'google', 'dns-direct'])
  assert.equal(config.dns.strategy, 'ipv4_only')
  assert.equal(config.dns.final, 'dns-proxy')
})

test('默认规则显式提供 Rule，保存其他默认模式后仍可切回', () => {
  const rule = config.route.rules.at(-1)
  assert.deepEqual(rule, { clash_mode: 'Rule', action: 'route', outbound: config.route.final })
  assert.equal(config.experimental.clash_api.default_mode, 'Rule')
  const modes = rules => rules.flatMap(rule => [...list(rule.clash_mode), ...modes(rule.rules ?? [])])
  assert.deepEqual([...new Set([...modes(config.route.rules), ...modes(config.dns.rules)])].sort(),
    ['Direct', 'Proxy', 'Rule', 'RuleAllowAds'])
})

test('默认远程规则有对应的内置文件与更新来源', () => {
  for (const rule of config.route.rule_set.filter(rule => rule.type === 'remote')) {
    for (const tag of list(rule.tag)) {
      const path = `src/module/config/singbox/${rule.path.replace(/^\.\//, '').replaceAll('{tag}', tag)}`
      const resource = resources.find(resource => resource.path === path)
      assert.ok(resource, `${tag} 未登记资源更新来源`)
      assert.equal(decodeURI(resource.url), decodeURI(rule.url.replaceAll('{tag}', tag)))
      const content = readFileSync(new URL(path, root))
      assert.ok(content.length > 0, `${tag} 内置规则为空`)
      assert.equal(createHash('sha256').update(content).digest('hex'), resource.currentSha256, tag)
      if (rule.format === 'binary') {
        assert.equal(content.subarray(0, 3).toString(), 'SRS', tag)
      }
    }
  }
})

test('eBPF 默认绕过为空，不引用任何静态规则集', () => {
  assert.deepEqual(managed.ebpf.local.bypass_rule_set, [])
  assert.deepEqual(managed.ebpf.shared.bypass_rule_set, [])
  assert.equal(Object.hasOwn(managed.ebpf, 'bypass_rule_set'), false)
})

test('单一入站默认保留 eBPF 原生语义和禁用共享路径偏好', () => {
  assert.deepEqual(Object.keys(managed), ['backend', 'root_policy', 'app', 'ebpf', 'tun'])
  assert.equal(managed.root_policy, 'exclude')
  assert.equal(managed.backend, 'ebpf')
  assert.deepEqual(managed.app, { enabled: true, mode: 'whitelist', proxy_apps: [], bypass_apps: [] })
  assert.deepEqual(managed.ebpf, {
    type: 'ebpf', tag: 'netproxy-in', network: ['tcp', 'udp'], udp_timeout: '5m', tc_priority: 1,
    local: {
      enabled: true, data_plane: 'cgroup', dns_mode: 'respect_policy', ipv6: false,
      bypass_private_address: true, bypass_rule_set: [],
    },
    shared: {
      enabled: false, data_plane: 'packet_rewrite', dns_mode: 'hijack', interface: ['wlan2'],
      ipv6: true, bypass_private_address: true, bypass_rule_set: [],
    },
  })
})

test('TUN 默认只固定必需接管参数，不覆盖上游可选默认', () => {
  assert.deepEqual(managed.tun, {
    type: 'tun', tag: 'netproxy-in', interface_name: 'netproxy',
    address: ['172.19.0.1/30', 'fdfe:dcba:9876::1/126'],
    auto_route: true, auto_redirect: true, dns_mode: 'hijack',
  })
  assert.equal(config.route.auto_detect_interface, true)
  assert.ok(config.inbounds.every(inbound => !['ebpf', 'tun'].includes(inbound.type) && inbound.tag !== 'netproxy-in'))
})
