package com.example.interview.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 소개 화면: 로고(Interviewer)를 누르면 온다 (사용자 2026-10-28) */
@Controller
public class AboutController {

    @GetMapping("/about")
    public String about() {
        return "about";
    }
}
