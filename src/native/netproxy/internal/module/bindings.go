package module

import (
	"fmt"
	"os"

	"encoding/json/jsontext"
	json "encoding/json/v2"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/inbound"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/paths"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/provider"
)

// bindingRule 是分应用绑定编译出的路由规则，字段顺序由 Deterministic 序列化固定。
type bindingRule struct {
	PackageName []string `json:"package_name"`
	Action      string   `json:"action"`
	Outbound    string   `json:"outbound"`
}

// compileBindings 把持久绑定编译为路由规则。
// sing-box 的 package_name 无法区分 Android 用户，同一包名只生成一条规则，多用户同包名以首条绑定为准；
// 目标出站不在展开节点集合与保留出站内的绑定不生成规则，返回给调用方记录并提示。
func compileBindings(bindings []inbound.AppBinding, nodeTags []string) (rules []bindingRule, skipped []inbound.AppBinding) {
	valid := make(map[string]bool, len(nodeTags)+2)
	valid["Proxy"] = true
	valid["direct"] = true
	for _, tag := range nodeTags {
		valid[tag] = true
	}
	seen := make(map[string]bool, len(bindings))
	for _, binding := range bindings {
		if !valid[binding.Outbound] {
			skipped = append(skipped, binding)
			continue
		}
		ref, err := inbound.ParsePackageRef(binding.App)
		if err != nil {
			// 入站 Parse 已校验引用格式，正常路径不可达；防御性跳过，避免生成无效规则。
			skipped = append(skipped, binding)
			continue
		}
		if seen[ref.Package] {
			continue
		}
		seen[ref.Package] = true
		rules = append(rules, bindingRule{PackageName: []string{ref.Package}, Action: "route", Outbound: binding.Outbound})
	}
	return rules, skipped
}

// writeEffectiveConfig 读取静态主配置，拼入绑定规则后写出运行时有效主配置。
// 静态主配置是用户可编辑的唯一事实源，这里只读取，绝不回写；无绑定时也写出副本，
// 保证核心启动与 check 始终消费同一份组装视图。
func writeEffectiveConfig(options Options, config inbound.Config, nodeTags []string, output string) ([]inbound.AppBinding, error) {
	content, err := os.ReadFile(paths.SingBoxConfig(options.SingBoxDir))
	if err != nil {
		return nil, err
	}
	rules, skipped := compileBindings(config.App.Bindings, nodeTags)
	effective, err := spliceBindingRules(content, rules)
	if err != nil {
		return nil, err
	}
	if err := provider.WriteAtomic(output, effective, 0o600); err != nil {
		return nil, err
	}
	return skipped, nil
}

// spliceBindingRules 只重组 route.rules，其余顶层字段与路由字段保持原始 JSON 文本不做语义改写。
func spliceBindingRules(content []byte, rules []bindingRule) ([]byte, error) {
	var document map[string]jsontext.Value
	if err := json.Unmarshal(content, &document); err != nil {
		return nil, fmt.Errorf("主配置 JSON 无效: %w", err)
	}
	if len(rules) > 0 {
		var route map[string]jsontext.Value
		if raw, exists := document["route"]; exists {
			if err := json.Unmarshal(raw, &route); err != nil {
				return nil, fmt.Errorf("主配置 route 无效: %w", err)
			}
		} else {
			route = make(map[string]jsontext.Value)
		}
		var existing []jsontext.Value
		if raw, exists := route["rules"]; exists {
			if err := json.Unmarshal(raw, &existing); err != nil {
				return nil, fmt.Errorf("主配置 route.rules 无效: %w", err)
			}
		}
		compiled := make([]jsontext.Value, 0, len(rules))
		for _, rule := range rules {
			encoded, err := json.Marshal(rule, json.Deterministic(true))
			if err != nil {
				return nil, err
			}
			compiled = append(compiled, encoded)
		}
		index := bindingInsertIndex(existing)
		merged := make([]jsontext.Value, 0, len(existing)+len(compiled))
		merged = append(merged, existing[:index]...)
		merged = append(merged, compiled...)
		merged = append(merged, existing[index:]...)
		encodedRules, err := json.Marshal(merged, json.Deterministic(true))
		if err != nil {
			return nil, err
		}
		route["rules"] = encodedRules
		encodedRoute, err := json.Marshal(route, json.Deterministic(true))
		if err != nil {
			return nil, err
		}
		document["route"] = encodedRoute
	}
	effective, err := json.Marshal(document, json.Deterministic(true), jsontext.WithIndent("  "))
	if err != nil {
		return nil, err
	}
	return append(effective, '\n'), nil
}

type bindingRuleProbe struct {
	Action    string `json:"action"`
	ClashMode string `json:"clash_mode"`
}

// bindingInsertIndex 计算绑定规则的插入位置：绑定按应用分流，必须排在 DNS 劫持之后，
// 否则应用的 DNS 流量会被直接送往节点而绕过 hijack-dns。
func bindingInsertIndex(rules []jsontext.Value) int {
	sniffIndex := -1
	for index, raw := range rules {
		var probe bindingRuleProbe
		if err := json.Unmarshal(raw, &probe); err != nil {
			continue
		}
		if probe.Action == "hijack-dns" {
			return index + 1
		}
		if probe.Action == "sniff" && sniffIndex < 0 {
			sniffIndex = index
		}
	}
	if sniffIndex >= 0 {
		return sniffIndex + 1
	}
	if len(rules) > 0 {
		var first bindingRuleProbe
		if err := json.Unmarshal(rules[0], &first); err == nil && first.ClashMode == "Direct" {
			return 1
		}
	}
	return 0
}
