package com.example.digigoods.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.digigoods.dto.LoginRequest;
import com.example.digigoods.dto.LoginResponse;
import com.example.digigoods.model.User;
import com.example.digigoods.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private AuthenticationManager authenticationManager;

  @Mock
  private JwtService jwtService;

  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private AuthService authService;

  private LoginRequest loginRequest;
  private User user;

  @BeforeEach
  void setUp() {
    loginRequest = new LoginRequest("testuser", "password");
    
    user = new User();
    user.setId(1L);
    user.setUsername("testuser");
    user.setPassword("encodedPassword");
  }

  @Test
  @DisplayName("Given valid credentials, when logging in, then return login response")
  void givenValidCredentials_whenLoggingIn_thenReturnLoginResponse() {
    // Arrange
    String expectedToken = "jwt.token.here";
    when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
    when(jwtService.generateToken(1L, "testuser")).thenReturn(expectedToken);

    // Act
    LoginResponse response = authService.login(loginRequest);

    // Assert
    assertEquals(expectedToken, response.getToken());
    assertEquals(1L, response.getUserId());
    assertEquals("testuser", response.getUsername());
    verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    verify(jwtService).generateToken(1L, "testuser");
  }

  @Test
  @DisplayName("Given invalid credentials, when logging in, then throw exception")
  void givenInvalidCredentials_whenLoggingIn_thenThrowException() {
    // Arrange
    when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
        .thenThrow(new BadCredentialsException("Invalid credentials"));

    // Act & Assert
    assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));
  }

  @Test
  @DisplayName("Given user not found after authentication, when logging in, then throw exception")
  void givenUserNotFoundAfterAuth_whenLoggingIn_thenThrowException() {
    // Arrange
    when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

    // Act & Assert
    RuntimeException exception = assertThrows(RuntimeException.class,
        () -> authService.login(loginRequest));
    assertEquals("User not found", exception.getMessage());
  }

  @Test
  @DisplayName("Given authentication succeeds, when logging in, then auth with correct token")
  void givenAuthSucceeds_whenLoggingIn_thenAuthenticateWithCorrectToken() {
    // Arrange
    when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
    when(jwtService.generateToken(1L, "testuser")).thenReturn("token");

    // Act
    authService.login(loginRequest);

    // Assert
    verify(authenticationManager).authenticate(
        new UsernamePasswordAuthenticationToken("testuser", "password"));
  }
}