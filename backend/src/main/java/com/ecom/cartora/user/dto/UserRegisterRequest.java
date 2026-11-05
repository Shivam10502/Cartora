package com.ecom.cartora.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 30, message = "Name cannot exceed 30 characters")
    private String name;
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email")
    @Size(max = 50, message = "Email cannot exceed 50 characters")
    private String email;
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @jakarta.validation.constraints.Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$",
            message = "Password must contain uppercase, lowercase, a digit and a special character, with no whitespace")
    private String password;

}
