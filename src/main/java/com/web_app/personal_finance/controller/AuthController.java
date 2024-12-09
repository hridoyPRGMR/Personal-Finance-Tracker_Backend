package com.web_app.personal_finance.controller;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.web_app.personal_finance.dto.LoginRequest;
import com.web_app.personal_finance.dto.SignUpRequest;
import com.web_app.personal_finance.model.User;
import com.web_app.personal_finance.model.UserRole;
import com.web_app.personal_finance.repository.UserRepository;
import com.web_app.personal_finance.response.ApiResponse;
import com.web_app.personal_finance.repository.RoleRepository;
import com.web_app.personal_finance.security.JwtUtil;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    private record Token(String token) {}

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<?>> signUp(@Valid @RequestBody SignUpRequest request) {
        
    	
    	System.out.println("Email: " + request.getEmail());
    	
    	if (userRepository.findByUsername(request.getEmail()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.getEmail());
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        
        // Fetch the default "USER" role from the database
        UserRole userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("Default role USER not found"));
        
        user.setRoles(Collections.singleton(userRole));
        user.setEnabled(true);
        
        userRepository.save(user);
        
        return ResponseEntity.ok(new ApiResponse<>(true,"User registered successfully",user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest request) {
    	
        User user = userRepository.findByUsername(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found" + request.getEmail()));

        if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            List<String> roles = user.getRoles().stream()
                                     .map(UserRole::getName)
                                     .toList();
            
            Token token = new Token(jwtUtil.generateToken(user.getUsername(),Long.toString(user.getId()), roles));
            
            return ResponseEntity.ok(new ApiResponse<>(true,"User login successfully",token));
        
        } else {
        	return ResponseEntity.status(401).body(new ApiResponse<>(false,"Invalid credintials",null));
        }
    }

    @PostMapping("/refresh-token")
    public String refreshToken(@RequestHeader("Authorization") String refreshToken) {
        // Strip "Bearer " prefix if present
        String token = refreshToken.startsWith("Bearer ") ? refreshToken.substring(7) : refreshToken;

        Claims claims = jwtUtil.validateToken(token);
        
        
        String newToken = jwtUtil.generateToken(claims.getSubject(),claims.get("userId",String.class), claims.get("roles", List.class));
        
        return newToken;
    }
}

