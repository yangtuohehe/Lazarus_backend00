package com.example.lazarus_backend00;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class testcontroller {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("message", "欢迎使用 Lazarus 后端系统");
        return "index"; // 这对应 templates/index.html
    }

    @GetMapping("/home")
    public String home() {
        return "index"; // 也可以配置其他路径
    }
}