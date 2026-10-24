package inbound

import (
	"bytes"
	"strings"
	"testing"
)

func parseAppPolicy(t *testing.T, app string) (Config, error) {
	t.Helper()
	return Parse([]byte(`{"backend":"ebpf","root_policy":"default","app":` + app + `,"ebpf":` + testEBPF + `,"tun":` + testTUN + `}`))
}

func TestParseAppBindings(t *testing.T) {
	config, err := parseAppPolicy(t, `{"enabled":true,"mode":"whitelist","proxy_apps":["0:com.example.app"],"bypass_apps":[],"bindings":[{"app":"0:com.example.app","outbound":"China"},{"app":"10:com.example.app","outbound":"laboy"}]}`)
	if err != nil {
		t.Fatal(err)
	}
	if len(config.App.Bindings) != 2 || config.App.Bindings[0] != (AppBinding{App: "0:com.example.app", Outbound: "China"}) {
		t.Fatalf("绑定解析不一致: %#v", config.App.Bindings)
	}
	content := encodeConfig(t, config)
	reloaded, err := Parse(content)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(content, encodeConfig(t, reloaded)) {
		t.Fatal("带绑定的配置往返改变了内容")
	}
}

func TestParseAppBindingsOmittedByDefault(t *testing.T) {
	config := fixture(t, "ebpf", "", "")
	content := encodeConfig(t, config)
	if strings.Contains(string(content), "bindings") {
		t.Fatalf("未设置绑定时不应写入 bindings 字段: %s", content)
	}
}

func TestParseAppBindingsInvalid(t *testing.T) {
	base := `"enabled":true,"mode":"whitelist","proxy_apps":[],"bypass_apps":[]`
	tests := map[string]string{
		"应用引用非法":   `{` + base + `,"bindings":[{"app":"com.example.app","outbound":"China"}]}`,
		"出站为空":     `{` + base + `,"bindings":[{"app":"0:com.example.app","outbound":""}]}`,
		"出站只有空白":   `{` + base + `,"bindings":[{"app":"0:com.example.app","outbound":"  "}]}`,
		"同一应用重复绑定": `{` + base + `,"bindings":[{"app":"0:com.example.app","outbound":"China"},{"app":"0:com.example.app","outbound":"laboy"}]}`,
		"同一引用重复绑定": `{` + base + `,"bindings":[{"app":"0:com.example.app","outbound":"China"},{"app":"00:com.example.app","outbound":"laboy"}]}`,
		"绑定含未知字段":  `{` + base + `,"bindings":[{"app":"0:com.example.app","outbound":"China","extra":1}]}`,
	}
	for name, app := range tests {
		t.Run(name, func(t *testing.T) {
			if _, err := parseAppPolicy(t, app); err == nil {
				t.Fatalf("非法绑定被接受: %s", app)
			}
		})
	}
}
