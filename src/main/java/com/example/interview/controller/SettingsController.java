package com.example.interview.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.interview.ai.ApiKeyStore;
import com.example.interview.service.OllamaStatusService;
import com.example.interview.service.PrivacyModeService;

import lombok.RequiredArgsConstructor;

/** "설정" 탭: GPT API 키, 로컬 AI(Ollama) 상태, 개인정보 엄격 보호 모드 */
@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final ApiKeyStore apiKeys;
    private final OllamaStatusService ollama;
    private final PrivacyModeService privacyMode;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("apiKey", apiKeys);
        model.addAttribute("ollama", ollama.status());
        model.addAttribute("strictPrivacy", privacyMode.isStrict());
        return "settings";
    }

    @PostMapping("/privacy")
    public String savePrivacy(@RequestParam(defaultValue = "false") boolean strict, RedirectAttributes ra) {
        privacyMode.setStrict(strict);
        ra.addFlashAttribute("message", strict ? "개인정보 엄격 보호 모드를 켰습니다." : "개인정보 엄격 보호 모드를 껐습니다.");
        return "redirect:/settings";
    }

    @PostMapping("/api-key")
    public String saveApiKey(@RequestParam(defaultValue = "") String apiKey, RedirectAttributes ra) {
        apiKeys.save(apiKey);
        ra.addFlashAttribute("message", "API 키를 저장했습니다.");
        return "redirect:/settings";
    }
}
