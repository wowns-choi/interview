package com.example.interview.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.interview.service.BusinessException;
import com.example.interview.service.NotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public Object notFound(NotFoundException e, HttpServletRequest request, HttpServletResponse response, Model model) {
        if (isFetch(request)) {
            return plain(HttpStatus.NOT_FOUND, e.getMessage());
        }
        response.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("errorMessage", e.getMessage());
        return "error-page";
    }

    @ExceptionHandler(BusinessException.class)
    public Object business(BusinessException e, HttpServletRequest request, RedirectAttributes ra) {
        if (isFetch(request)) {
            return plain(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        ra.addFlashAttribute("error", e.getMessage());
        return "redirect:" + backUrl(request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest request, RedirectAttributes ra) {
        ra.addFlashAttribute("error", "파일이 너무 큽니다. 업로드 한도를 확인하세요.");
        return "redirect:" + backUrl(request);
    }

    /** 화면 이동 없이 fetch로 보낸 요청 (답변 자동 저장) */
    private static boolean isFetch(HttpServletRequest request) {
        return "fetch".equals(request.getHeader("X-Requested-With"));
    }

    private static ResponseEntity<String> plain(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .body(message);
    }

    /** 같은 사이트 안의 이전 페이지로만 돌려보낸다. */
    private static String backUrl(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        String base = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        if (referer != null && referer.startsWith(base)) {
            String path = referer.substring(base.length());
            return path.isEmpty() ? "/" : path;
        }
        return "/extract";
    }
}
