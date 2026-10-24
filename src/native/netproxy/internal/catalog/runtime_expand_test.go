package catalog

import (
	"context"
	"os"
	"path/filepath"
	"slices"
	"testing"

	"encoding/json/jsontext"
	json "encoding/json/v2"
	moduleconfig "github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/config"
)

func writeGroupDocument(t *testing.T, root, id, name, groupType, document string, nodeCount int) {
	t.Helper()
	directory := filepath.Join(root, id)
	if err := os.MkdirAll(directory, 0o700); err != nil {
		t.Fatal(err)
	}
	metadata, err := json.Marshal(map[string]any{
		"id": id, "name": name, "type": groupType, "revision": 1,
		"node_count": nodeCount, "update_interval": 86400, "update_via_proxy": "auto",
	}, json.Deterministic(true))
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(directory, "meta.json"), metadata, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(directory, "provider.json"), []byte(document), 0o600); err != nil {
		t.Fatal(err)
	}
}

func runtimeEntryTags(t *testing.T, entries []map[string]jsontext.Value) []string {
	t.Helper()
	tags := make([]string, 0, len(entries))
	for _, entry := range entries {
		var tag string
		if err := json.Unmarshal(entry["tag"], &tag); err != nil {
			t.Fatal(err)
		}
		tags = append(tags, tag)
	}
	return tags
}

func TestBuildRuntimeExpandsGroupNodes(t *testing.T) {
	root := t.TempDir()
	writeGroupDocument(t, root, "alpha", "分组A", "local", `{"outbounds":[
		{"type":"socks","tag":"HK","server":"a.example.com","server_port":1080},
		{"type":"socks","tag":"shared","server":"a.example.com","server_port":1081}
	]}`, 2)
	writeGroupDocument(t, root, "beta", "分组B", "subscription", `{"outbounds":[
		{"type":"socks","tag":"HK","server":"b.example.com","server_port":1080},
		{"type":"socks","tag":"shared","server":"b.example.com","server_port":1081}
	],"endpoints":[
		{"type":"wireguard","tag":"WG","address":["10.0.0.2/32"],"private_key":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","peers":[{"address":"b.example.com","port":51820,"public_key":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","allowed_ips":["0.0.0.0/0"]}]}
	]}`, 3)

	providersPath := filepath.Join(root, "runtime", "providers.json")
	outboundsPath := filepath.Join(root, "runtime", "outbounds.json")
	result, err := BuildRuntime(context.Background(), RuntimeOptions{
		Root: root, ProvidersOutput: providersPath, OutboundsOutput: outboundsPath,
		Selection: moduleconfig.Selection{ActiveGroupID: "alpha"},
	})
	if err != nil {
		t.Fatal(err)
	}
	wantTags := []string{"HK", "HK_2", "WG", "shared", "shared_2"}
	if !slices.Equal(result.NodeTags, wantTags) {
		t.Fatalf("展开节点集合不一致: %v, want %v", result.NodeTags, wantTags)
	}

	var document struct {
		Outbounds []map[string]jsontext.Value `json:"outbounds"`
		Endpoints []map[string]jsontext.Value `json:"endpoints"`
	}
	if err := json.Unmarshal([]byte(readFile(t, outboundsPath)), &document); err != nil {
		t.Fatal(err)
	}
	outboundTags := runtimeEntryTags(t, document.Outbounds)
	seen := make(map[string]int, len(outboundTags))
	for _, tag := range outboundTags {
		seen[tag]++
	}
	for _, expected := range []string{"direct", "block", "Proxy", "Auto/分组A", "Select/分组A", "Auto/分组B", "Select/分组B", "HK", "shared", "HK_2", "shared_2"} {
		if seen[expected] != 1 {
			t.Fatalf("出站 tag %s 出现 %d 次, want 1: %v", expected, seen[expected], outboundTags)
		}
	}
	if len(outboundTags) != len(seen) {
		t.Fatalf("出站 tag 存在重复: %v", outboundTags)
	}
	endpointTags := runtimeEntryTags(t, document.Endpoints)
	if !slices.Equal(endpointTags, []string{"WG"}) {
		t.Fatalf("展开端点不一致: %v", endpointTags)
	}
	for _, outbound := range document.Outbounds {
		var tag string
		if err := json.Unmarshal(outbound["tag"], &tag); err != nil {
			t.Fatal(err)
		}
		if tag == "Proxy" {
			var fallback string
			if err := json.Unmarshal(outbound["default"], &fallback); err != nil || fallback != "Auto/分组A" {
				t.Fatalf("Proxy 默认出站被展开逻辑改变: %s %v", fallback, err)
			}
		}
		if tag == "HK" && string(outbound["server"]) != `"a.example.com"` {
			t.Fatalf("展开节点丢失原始字段: %v", outbound)
		}
		if tag == "HK_2" && string(outbound["server"]) != `"b.example.com"` {
			t.Fatalf("跨组同名节点消歧后内容错位: %v", outbound)
		}
	}
}

func TestBuildRuntimeExpansionSkipsCorruptGroup(t *testing.T) {
	root := t.TempDir()
	writeGroupDocument(t, root, "alpha", "分组A", "local", `{"outbounds":[
		{"type":"socks","tag":"GOOD","server":"a.example.com","server_port":1080}
	]}`, 1)
	writeGroupDocument(t, root, "beta", "分组B", "subscription", `{"outbounds":[`, 1)

	outboundsPath := filepath.Join(root, "runtime", "outbounds.json")
	result, err := BuildRuntime(context.Background(), RuntimeOptions{
		Root: root, ProvidersOutput: filepath.Join(root, "runtime", "providers.json"), OutboundsOutput: outboundsPath,
		Selection: moduleconfig.Selection{ActiveGroupID: "alpha"},
	})
	if err != nil {
		t.Fatal(err)
	}
	if !slices.Equal(result.NodeTags, []string{"GOOD"}) {
		t.Fatalf("损坏分组未被跳过: %v", result.NodeTags)
	}
	if result.GroupCount != 2 || result.NodeCount != 2 {
		t.Fatalf("损坏分组改变了元数据汇总: %#v", result)
	}
}
