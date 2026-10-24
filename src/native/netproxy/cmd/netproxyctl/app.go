package main

import (
	"context"
	"errors"
	"os"
	"strings"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/inbound"
	moduleapp "github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/module"
)

func (c *cli) app(ctx context.Context, args []string) error {
	if len(args) == 0 {
		args = []string{"list"}
	}
	options := c.options
	action := args[0]
	positionals := args[1:]
	if action == "list" {
		data, err := moduleapp.LoadAppPolicy(options.InboundConfig)
		if err != nil {
			return err
		}
		writeJSON(os.Stdout, result{Schema: 1, OK: true, Code: "app.list", Message: "分应用代理配置", Data: data})
		return nil
	}
	value := ""
	if len(positionals) > 0 {
		value = positionals[0]
	}
	switch action {
	case "add", "remove", "unbind":
		if _, err := inbound.ParsePackageRef(value); err != nil {
			return &resultError{Code: "app.package_invalid", Message: err.Error()}
		}
	case "bind":
		if len(positionals) != 2 {
			return usageError("用法: netproxyctl app bind <用户ID>:<包名> <出站>")
		}
		if _, err := inbound.ParsePackageRef(value); err != nil {
			return &resultError{Code: "app.package_invalid", Message: err.Error()}
		}
		// 出站存在性不在保存时校验，节点可能来自后续才导入的分组；这里只拦空值。
		if strings.TrimSpace(positionals[1]) == "" {
			return &resultError{Code: "app.binding_invalid", Message: "应用绑定的出站不能为空"}
		}
	case "mode":
		if value != "blacklist" && value != "whitelist" {
			return &resultError{Code: "app.mode_invalid", Message: "应用模式应为 blacklist 或 whitelist"}
		}
	}
	data, err := moduleapp.UpdateApp(ctx, options, action, positionals...)
	if err != nil {
		var validation *inbound.ValidationError
		if errors.As(err, &validation) {
			return err
		}
		return &resultError{Code: "app.update_failed", Message: err.Error()}
	}
	code := "app." + action
	message := "分应用代理设置已更新"
	if action == "mode" {
		message = "分应用模式已更新"
	}
	if action == "bind" {
		message = "应用节点绑定已更新"
	}
	if action == "unbind" {
		message = "应用节点绑定已删除"
	}
	writeJSON(os.Stdout, result{Schema: 1, OK: true, Code: code, Message: message, Data: data})
	return nil
}
