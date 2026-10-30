package com.billing.invoicehub;

import com.billing.invoicehub.exception.InvalidPasswordException;
import com.billing.invoicehub.service.EmailService;
import com.billing.invoicehub.service.PasswordStrengthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.profiles.active=dev",
        "app.seed.enabled=false",
        "rate-limiting.enabled=false",
        "app.security.hibp.enabled=false" // disable external network call in tests
})
public class PasswordStrengthServiceTest {

    @MockBean
    private EmailService emailService;

    @Autowired
    private PasswordStrengthService passwordStrengthService;

    @Test
    @DisplayName("Should accept valid compliant password meeting all 5 requirements")
    void testValidPassword() {
        assertDoesNotThrow(() ->
                passwordStrengthService.validatePassword("Str0ng#Invoice2026", "john_doe", "john.doe@example.com")
        );
    }

    @Test
    @DisplayName("Should reject password shorter than 12 characters")
    void testShortPassword() {
        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class, () ->
                passwordStrengthService.validatePassword("Str0ng!2026", "john_doe", "john@example.com")
        );
        assertTrue(ex.getErrorCodes().contains("validation.password.length"),
                "Expected validation.password.length error code");
    }

    @Test
    @DisplayName("Should reject password missing uppercase letter")
    void testMissingUppercase() {
        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class, () ->
                passwordStrengthService.validatePassword("str0ng#invoice2026", "john_doe", "john@example.com")
        );
        assertTrue(ex.getErrorCodes().contains("validation.password.uppercase"),
                "Expected validation.password.uppercase error code");
    }

    @Test
    @DisplayName("Should reject password missing lowercase letter")
    void testMissingLowercase() {
        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class, () ->
                passwordStrengthService.validatePassword("STR0NG#INVOICE2026", "john_doe", "john@example.com")
        );
        assertTrue(ex.getErrorCodes().contains("validation.password.lowercase"),
                "Expected validation.password.lowercase error code");
    }

    @Test
    @DisplayName("Should reject password missing number")
    void testMissingDigit() {
        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class, () ->
                passwordStrengthService.validatePassword("Strong#InvoiceSecure", "john_doe", "john@example.com")
        );
        assertTrue(ex.getErrorCodes().contains("validation.password.digit"),
                "Expected validation.password.digit error code");
    }

    @Test
    @DisplayName("Should reject password missing symbol")
    void testMissingSymbol() {
        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class, () ->
                passwordStrengthService.validatePassword("Str0ngInvoice2026", "john_doe", "john@example.com")
        );
        assertTrue(ex.getErrorCodes().contains("validation.password.symbol"),
                "Expected validation.password.symbol error code");
    }

    @Test
    @DisplayName("Should reject password containing username")
    void testPasswordContainingUsername() {
        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class, () ->
                passwordStrengthService.validatePassword("AdminUser#2026Pass", "AdminUser", "other@example.com")
        );
        assertTrue(ex.getErrorCodes().contains("validation.password.contains_user"),
                "Expected validation.password.contains_user error code");
    }

    @Test
    @DisplayName("Should reject password containing email or email local part")
    void testPasswordContainingEmail() {
        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class, () ->
                passwordStrengthService.validatePassword("Secret#finance2026", "user123", "finance@example.com")
        );
        assertTrue(ex.getErrorCodes().contains("validation.password.contains_user"),
                "Expected validation.password.contains_user error code when containing email username");
    }

    @Test
    @DisplayName("Should reject common or easily guessable passwords")
    void testCommonPasswordRejection() {
        InvalidPasswordException ex = assertThrows(InvalidPasswordException.class, () ->
                passwordStrengthService.validatePassword("Password12345!", "someone", "someone@example.com")
        );
        assertTrue(ex.getErrorCodes().contains("validation.password.common"),
                "Expected validation.password.common error code");
    }

    @Test
    @DisplayName("isCommonPassword should identify default passwords like admin123 and vendor123")
    void testIsCommonPassword() {
        assertTrue(passwordStrengthService.isCommonPassword("admin123"));
        assertTrue(passwordStrengthService.isCommonPassword("ADMIN123"));
        assertTrue(passwordStrengthService.isCommonPassword("vendor123"));
        assertTrue(passwordStrengthService.isCommonPassword("password123"));
        assertFalse(passwordStrengthService.isCommonPassword("Xy9#qL2$mK8!vP4"));
    }
}
