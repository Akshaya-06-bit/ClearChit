package com.clearchit.controller;

import com.clearchit.dto.AuthResponse;
import com.clearchit.dto.LoginRequest;
import com.clearchit.dto.RegisterRequest;
import com.clearchit.model.UserAccount;
import com.clearchit.security.JwtService;
import com.clearchit.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(
            AuthService authService,
            JwtService jwtService) {

        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request) {

        UserAccount userAccount =
                authService.register(request);

        String token =
                jwtService.generateToken(userAccount);

        return new AuthResponse(
                userAccount.getId(),
                userAccount.getMember().getId(),
                userAccount.getEmail(),
                userAccount.getRole().name(),
                token
        );
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request) {

        UserAccount userAccount =
                authService.login(request);

        String token =
                jwtService.generateToken(userAccount);

        return new AuthResponse(
                userAccount.getId(),
                userAccount.getMember().getId(),
                userAccount.getEmail(),
                userAccount.getRole().name(),
                token
        );
    }
}