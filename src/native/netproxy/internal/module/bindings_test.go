package module

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"encoding/json/jsontext"
	json "encoding/json/v2"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/catalog"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/inbound"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/paths"
)

const testBindingStaticConfig = `{
  "log": {"disabled": true},
  "dns": {"servers": [{"type": "local", "tag": "dns-local"}], "final": "dns-local"},
  "experimental": {"clash_api": {"default_mode": "Rule", "external_controller": "127.0.0.1:9999", "secret": "singbox"}},
  "services": [{"type": "api", "listen": "127.0.0.1", "listen_port": 9090, "secret": "singbox"}],
  "route": {
    "final": "Proxy",
    "rules": [
      {"clash_mode": "Direct", "action": "route", "outbound": "direct"},
      {"action": "sniff"},
      {"action": "hijack-dns"},
      {"ip_is_private": true, "action": "route", "outbound": "direct"}
    ]
  }
}`

func bindingFixture(t *testing.T) Options {
	t.Helper()
	options := newTestOptions(t.TempDir())
	if err := os.MkdirAll(filepath.Dir(options.ModuleConfig), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(options.ModuleConfig, []byte(`{"selection":{"group_id":"default","node_tag":""}}`), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := catalog.InitializeGroup(context.Background(), catalog.GroupOptions{
		Root: options.CatalogRoot, GroupID: "default", Name: "本地配置", Type: "local",
	}); err != nil {
		t.Fatal(err)
	}
	for _, tag := range []string{"China", "laboy"} {
		if _, err := catalog.AppendNode(context.Background(), catalog.MutationOptions{
			GroupDir: filepath.Join(options.CatalogRoot, "default"), GroupID: "default", Name: "本地配置", Type: "local",
			Input: "socks://127.0.0.1:1080#" + tag,
		}); err != nil {
			t.Fatal(err)
		}
	}
	inboundContent := strings.Replace(testInboundConfig,
		`"app": {"enabled": false, "mode": "blacklist", "proxy_apps": [], "bypass_apps": []}`,
		`"app": {"enabled": false, "mode": "whitelist", "proxy_apps": [], "bypass_apps": [], "bindings": [
			{"app": "0:com.example.one", "outbound": "China"},
			{"app": "10:com.example.one", "outbound": "China"},
			{"app": "0:com.example.two", "outbound": "missing-node"},
			{"app": "0:com.example.three", "outbound": "direct"},
			{"app": "0:com.example.four", "outbound": "Proxy"}
		]}`, 1)
	if inboundContent == testInboundConfig {
		t.Fatal("入站夹具替换失败")
	}
	if err := os.MkdirAll(filepath.Dir(options.InboundConfig), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(options.InboundConfig, []byte(inboundContent), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(options.SingBoxDir, 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(paths.SingBoxConfig(options.SingBoxDir), []byte(testBindingStaticConfig), 0o600); err != nil {
		t.Fatal(err)
	}
	return options
}

func TestPrepareCompilesBindingsIntoEffectiveConfig(t *testing.T) {
	options := bindingFixture(t)
	staticBefore, err := os.ReadFile(paths.SingBoxConfig(options.SingBoxDir))
	if err != nil {
		t.Fatal(err)
	}
	prepared, err := Prepare(context.Background(), options, false)
	if err != nil {
		t.Fatal(err)
	}
	wantConfig := filepath.Join(options.RuntimeDir, "config.json")
	if prepared.Config != wantConfig {
		t.Fatalf("有效主配置路径不一致: %s, want %s", prepared.Config, wantConfig)
	}
	info, err := os.Stat(prepared.Config)
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0o600 {
		t.Fatalf("有效主配置权限为 %v, want 0600", info.Mode().Perm())
	}
	if len(prepared.SkippedBindings) != 1 || prepared.SkippedBindings[0] != (inbound.AppBinding{App: "0:com.example.two", Outbound: "missing-node"}) {
		t.Fatalf("失效绑定未被收集: %#v", prepared.SkippedBindings)
	}
	staticAfter, err := os.ReadFile(paths.SingBoxConfig(options.SingBoxDir))
	if err != nil {
		t.Fatal(err)
	}
	if string(staticBefore) != string(staticAfter) {
		t.Fatal("Prepare 回写了静态主配置")
	}

	content, err := os.ReadFile(prepared.Config)
	if err != nil {
		t.Fatal(err)
	}
	var document map[string]jsontext.Value
	if err := json.Unmarshal(content, &document); err != nil {
		t.Fatal(err)
	}
	var route struct {
		Final string             `json:"final"`
		Rules []jsontext.Value   `json:"rules"`
		Probe []bindingRuleProbe `json:"-"`
	}
	if err := json.Unmarshal(document["route"], &route); err != nil {
		t.Fatal(err)
	}
	if route.Final != "Proxy" || len(route.Rules) != 7 {
		t.Fatalf("有效配置路由结构不一致: final=%s rules=%d", route.Final, len(route.Rules))
	}
	type compiledRule struct {
		PackageName []string `json:"package_name"`
		Action      string   `json:"action"`
		Outbound    string   `json:"outbound"`
	}
	wantBindings := []compiledRule{
		{PackageName: []string{"com.example.one"}, Action: "route", Outbound: "China"},
		{PackageName: []string{"com.example.three"}, Action: "route", Outbound: "direct"},
		{PackageName: []string{"com.example.four"}, Action: "route", Outbound: "Proxy"},
	}
	// 前三条保持静态顺序（clash_mode Direct、sniff、hijack-dns），绑定规则紧随 hijack-dns。
	var probes []bindingRuleProbe
	for _, raw := range route.Rules {
		var probe bindingRuleProbe
		if err := json.Unmarshal(raw, &probe); err != nil {
			t.Fatal(err)
		}
		probes = append(probes, probe)
	}
	if probes[0].ClashMode != "Direct" || probes[1].Action != "sniff" || probes[2].Action != "hijack-dns" {
		t.Fatalf("静态规则前缀被改动: %+v", probes[:3])
	}
	for index, want := range wantBindings {
		var got compiledRule
		if err := json.Unmarshal(route.Rules[3+index], &got); err != nil {
			t.Fatal(err)
		}
		if len(got.PackageName) != 1 || got.PackageName[0] != want.PackageName[0] || got.Action != want.Action || got.Outbound != want.Outbound {
			t.Fatalf("绑定规则 %d 不一致: %+v, want %+v", index, got, want)
		}
	}
	var last map[string]jsontext.Value
	if err := json.Unmarshal(route.Rules[6], &last); err != nil {
		t.Fatal(err)
	}
	if _, exists := last["ip_is_private"]; !exists {
		t.Fatalf("绑定位之后的静态规则丢失: %s", route.Rules[6])
	}
}

func TestBindingInsertIndexFallbacks(t *testing.T) {
	rules := func(items ...string) []jsontext.Value {
		values := make([]jsontext.Value, 0, len(items))
		for _, item := range items {
			values = append(values, jsontext.Value(item))
		}
		return values
	}
	tests := []struct {
		name  string
		rules []jsontext.Value
		want  int
	}{
		{"hijack-dns 优先", rules(`{"action":"sniff"}`, `{"action":"hijack-dns"}`, `{"action":"route"}`), 2},
		{"跳过 hijack-dns 后的防护规则", rules(
			`{"action":"sniff"}`,
			`{"action":"hijack-dns"}`,
			`{"ip_cidr":["::/0"],"action":"reject"}`,
			`{"protocol":"quic","action":"reject"}`,
			`{"network":"udp","port":443,"action":"reject"}`,
			`{"action":"route"}`,
		), 5},
		{"普通 reject 不算防护规则", rules(
			`{"action":"hijack-dns"}`,
			`{"type":"logical","mode":"and","rules":[],"action":"reject"}`,
			`{"action":"route"}`,
		), 1},
		{"无 hijack-dns 退到 sniff", rules(`{"action":"route"}`, `{"action":"sniff"}`), 2},
		{"无两者时不越过 Direct 首条", rules(`{"clash_mode":"Direct","action":"route"}`, `{"action":"route"}`), 1},
		{"无两者且首条非 Direct", rules(`{"action":"route"}`), 0},
		{"空规则", nil, 0},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := bindingInsertIndex(test.rules); got != test.want {
				t.Fatalf("插入位置为 %d, want %d", got, test.want)
			}
		})
	}
}

func TestUpdateAppBindUnbindAndRemoveClearsBinding(t *testing.T) {
	fakeAndroidPackages(t)
	options, _, _, _ := inboundApplyFixture(t, "ebpf", false)
	ctx := t.Context()
	bindingsOf := func(policy AppPolicy) []inbound.AppBinding { return policy.Bindings }

	policy, err := UpdateApp(ctx, options, "bind", "0:com.example.app", "China")
	if err != nil {
		t.Fatal(err)
	}
	if got := bindingsOf(policy); len(got) != 1 || got[0] != (inbound.AppBinding{App: "0:com.example.app", Outbound: "China"}) {
		t.Fatalf("绑定未写入: %+v", got)
	}
	// 重复绑定同一应用以新出站覆盖，不产生重复条目。
	policy, err = UpdateApp(ctx, options, "bind", "0:com.example.app", "JP")
	if err != nil {
		t.Fatal(err)
	}
	if got := bindingsOf(policy); len(got) != 1 || got[0].Outbound != "JP" {
		t.Fatalf("重复绑定未覆盖: %+v", got)
	}
	// 落盘后可由 LoadAppPolicy 读回同样的绑定。
	loaded, err := LoadAppPolicy(options.InboundConfig)
	if err != nil {
		t.Fatal(err)
	}
	if got := bindingsOf(loaded); len(got) != 1 || got[0].Outbound != "JP" {
		t.Fatalf("落盘绑定不一致: %+v", got)
	}
	// bind Proxy 表示跟随主节点，删除绑定而不是写入 Proxy 绑定。
	policy, err = UpdateApp(ctx, options, "bind", "0:com.example.app", "Proxy")
	if err != nil {
		t.Fatal(err)
	}
	if len(bindingsOf(policy)) != 0 {
		t.Fatalf("跟随主节点应删除绑定: %+v", bindingsOf(policy))
	}
	// direct 是普通保留出站，照常写入；unbind 删除。
	if _, err := UpdateApp(ctx, options, "bind", "0:com.example.app", "direct"); err != nil {
		t.Fatal(err)
	}
	policy, err = UpdateApp(ctx, options, "unbind", "0:com.example.app")
	if err != nil {
		t.Fatal(err)
	}
	if len(bindingsOf(policy)) != 0 {
		t.Fatalf("unbind 未删除绑定: %+v", bindingsOf(policy))
	}
	// 应用被移出名单时连带清除绑定。
	if _, err := UpdateApp(ctx, options, "bind", "0:com.example.app", "China"); err != nil {
		t.Fatal(err)
	}
	policy, err = UpdateApp(ctx, options, "remove", "0:com.example.app")
	if err != nil {
		t.Fatal(err)
	}
	if len(bindingsOf(policy)) != 0 {
		t.Fatalf("remove 未清除绑定: %+v", bindingsOf(policy))
	}
	// 空出站被拒绝，且不修改已有策略。
	if _, err := UpdateApp(ctx, options, "bind", "0:com.example.app", " "); err == nil {
		t.Fatal("空出站应被拒绝")
	}
	// 无绑定的策略输出 bindings 为空数组而不是 null。
	raw, err := json.Marshal(policy)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(raw), `"bindings":[]`) {
		t.Fatalf("bindings 应输出空数组: %s", raw)
	}
}
