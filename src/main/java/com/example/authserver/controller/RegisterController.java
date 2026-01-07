package com.example.authserver.controller;

import com.example.authserver.dto.RegisterRequest;
import com.example.authserver.service.OAuth2ClientService;
import com.example.authserver.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
public class RegisterController {

    private final UserService userService;
    private final OAuth2ClientService oAuth2ClientService;

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute RegisterRequest request,
                               BindingResult result,
                               Model model) {
        if (result.hasErrors()) {
            return "register";
        }

        try {
            userService.registerUser(request);
            return "redirect:/login?registered";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    /**
     * OAuth 클라이언트 기반 회원가입 페이지
     */
    @GetMapping("/oauth/register")
    public String showOAuthRegistrationForm(
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "scope", defaultValue = "openid profile email") String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "response_type", defaultValue = "code") String responseType,
            Model model) {

        // 클라이언트 검증
        RegisteredClient client = oAuth2ClientService.findByClientId(clientId);
        if (client == null) {
            model.addAttribute("error", "유효하지 않은 클라이언트입니다.");
            return "oauth-register";
        }

        // redirect_uri 검증
        if (!client.getRedirectUris().contains(redirectUri)) {
            model.addAttribute("error", "유효하지 않은 리다이렉트 URI입니다.");
            return "oauth-register";
        }

        // 클라이언트 정보 전달
        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", client.getClientName());
        model.addAttribute("redirectUri", redirectUri);
        model.addAttribute("scope", scope);
        model.addAttribute("state", state);
        model.addAttribute("responseType", responseType);
        model.addAttribute("registerRequest", new RegisterRequest());

        return "oauth-register";
    }

    /**
     * OAuth 클라이언트 기반 회원가입 처리
     */
    @PostMapping("/oauth/register")
    public String registerOAuthUser(
            @Valid @ModelAttribute RegisterRequest request,
            BindingResult result,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "scope", defaultValue = "openid profile email") String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "response_type", defaultValue = "code") String responseType,
            Model model) {

        // 클라이언트 검증
        RegisteredClient client = oAuth2ClientService.findByClientId(clientId);
        if (client == null) {
            model.addAttribute("error", "유효하지 않은 클라이언트입니다.");
            return "oauth-register";
        }

        // 폼 파라미터 유지
        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", client.getClientName());
        model.addAttribute("redirectUri", redirectUri);
        model.addAttribute("scope", scope);
        model.addAttribute("state", state);
        model.addAttribute("responseType", responseType);

        if (result.hasErrors()) {
            return "oauth-register";
        }

        try {
            userService.registerUser(request);

            // 회원가입 성공 후 OAuth 인증 흐름으로 리다이렉트
            String authorizeUrl = UriComponentsBuilder.fromPath("/oauth2/authorize")
                    .queryParam("response_type", responseType)
                    .queryParam("client_id", clientId)
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("scope", scope)
                    .queryParamIfPresent("state", java.util.Optional.ofNullable(state))
                    .build()
                    .toUriString();

            return "redirect:" + authorizeUrl;
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "oauth-register";
        }
    }
}
