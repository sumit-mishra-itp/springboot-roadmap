package dev.roadmap.rest.book;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IsbnValidator implements ConstraintValidator<Isbn, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // convention: null is @NotNull's job, so constraints compose
        }
        String digits = value.replace("-", "");
        if (!digits.matches("\\d{13}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            int weight = i % 2 == 0 ? 1 : 3;
            sum += (digits.charAt(i) - '0') * weight;
        }
        return sum % 10 == 0;
    }
}
