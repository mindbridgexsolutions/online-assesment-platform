package com.mindbridgex.assessment.util;

import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

import java.util.List;

public final class FormErrors {

    private FormErrors() {
    }

    /** Validation messages for display in a summary alert (de-duplicated, in field order). */
    public static List<String> of(BindingResult result) {
        return result.getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .toList();
    }
}
