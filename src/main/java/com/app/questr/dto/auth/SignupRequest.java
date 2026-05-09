package com.app.questr.dto.auth;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record SignupRequest(
    @NotBlank(message="Username is required") @Size(min=3,max=50,message="Username must be 3-50 characters")
    @Pattern(regexp="^[a-zA-Z0-9_]+$",message="Username may only contain letters, digits and underscores") String username,
    @NotBlank(message="Email is required") @Email(message="Must be a valid email address") String email,
    @NotBlank(message="Password is required") @Size(min=8,max=100,message="Password must be 8-100 characters") String password
) {}
