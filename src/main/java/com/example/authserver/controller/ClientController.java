package com.example.authserver.controller;

import com.example.authserver.dto.ClientRegistrationRequest;
import com.example.authserver.service.OAuth2ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    private final OAuth2ClientService clientService;

    @GetMapping
    public String listClients(Model model) {
        List<Map<String, Object>> clients = clientService.findAllClients();
        model.addAttribute("clients", clients);
        model.addAttribute("clientRequest", new ClientRegistrationRequest());
        return "clients";
    }

    @PostMapping("/register")
    public String registerClient(@Valid @ModelAttribute("clientRequest") ClientRegistrationRequest request,
                                 BindingResult result,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (request.getClientSecret() == null || request.getClientSecret().isBlank()) {
            result.rejectValue("clientSecret", "NotBlank", "클라이언트 시크릿은 필수입니다");
        }

        if (result.hasErrors()) {
            List<Map<String, Object>> clients = clientService.findAllClients();
            model.addAttribute("clients", clients);
            return "clients";
        }

        try {
            RegisteredClient registeredClient = clientService.registerClient(request);
            redirectAttributes.addFlashAttribute("successMessage",
                "클라이언트가 등록되었습니다: " + registeredClient.getClientId());
            return "redirect:/clients";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            List<Map<String, Object>> clients = clientService.findAllClients();
            model.addAttribute("clients", clients);
            return "clients";
        }
    }

    @PostMapping("/delete/{clientId}")
    public String deleteClient(@PathVariable String clientId, RedirectAttributes redirectAttributes) {
        try {
            clientService.deleteClient(clientId);
            redirectAttributes.addFlashAttribute("successMessage", "클라이언트가 삭제되었습니다: " + clientId);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/clients";
    }

    @GetMapping("/edit/{clientId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getClientForEdit(@PathVariable String clientId) {
        Map<String, Object> details = clientService.getClientDetails(clientId);
        if (details == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(details);
    }

    @PostMapping("/update/{clientId}")
    public String updateClient(@PathVariable String clientId,
                               @ModelAttribute("clientRequest") ClientRegistrationRequest request,
                               RedirectAttributes redirectAttributes) {
        try {
            clientService.updateClient(clientId, request);
            redirectAttributes.addFlashAttribute("successMessage",
                "클라이언트가 수정되었습니다: " + clientId);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/clients";
    }

    @GetMapping("/test/{clientId}")
    public String testClient(@PathVariable String clientId, Model model) {
        RegisteredClient client = clientService.findByClientId(clientId);
        if (client == null) {
            return "redirect:/clients";
        }
        boolean requirePkce = client.getClientSettings().isRequireProofKey();
        String codeVerifier = requirePkce ? generateCodeVerifier() : null;
        String codeChallenge = requirePkce ? generateCodeChallenge(codeVerifier) : null;

        model.addAttribute("client", client);
        model.addAttribute("requirePkce", requirePkce);
        model.addAttribute("codeVerifier", codeVerifier);
        model.addAttribute("authorizationUrl", buildAuthorizationUrl(client, codeChallenge));
        return "client-test";
    }

    private String buildAuthorizationUrl(RegisteredClient client, String codeChallenge) {
        StringBuilder url = new StringBuilder("http://localhost:9000/oauth2/authorize?");
        url.append("response_type=code");
        url.append("&client_id=").append(java.net.URLEncoder.encode(client.getClientId(), java.nio.charset.StandardCharsets.UTF_8));

        String redirectUri = client.getRedirectUris().stream().findFirst().orElse("");
        url.append("&redirect_uri=").append(java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8));

        String scopes = String.join(" ", client.getScopes());
        url.append("&scope=").append(java.net.URLEncoder.encode(scopes, java.nio.charset.StandardCharsets.UTF_8));

        url.append("&state=").append(java.util.UUID.randomUUID().toString().substring(0, 8));

        if (codeChallenge != null) {
            url.append("&code_challenge=").append(codeChallenge);
            url.append("&code_challenge_method=S256");
        }

        return url.toString();
    }

    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String verifier) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
