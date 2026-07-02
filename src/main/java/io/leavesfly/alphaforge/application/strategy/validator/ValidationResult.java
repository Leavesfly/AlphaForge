package io.leavesfly.alphaforge.application.strategy.validator;

import java.util.ArrayList;
import java.util.List;

/**
 * 策略校验结果
 */
public class ValidationResult {

    private boolean valid;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public static ValidationResult success() {
        ValidationResult result = new ValidationResult();
        result.valid = true;
        return result;
    }

    public static ValidationResult failure(List<String> errors) {
        ValidationResult result = new ValidationResult();
        result.valid = false;
        result.errors.addAll(errors);
        return result;
    }

    public void addError(String error) {
        this.valid = false;
        this.errors.add(error);
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public boolean isValid() { return valid; }
    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }

    public String getErrorsJoined() {
        return String.join("; ", errors);
    }
}
