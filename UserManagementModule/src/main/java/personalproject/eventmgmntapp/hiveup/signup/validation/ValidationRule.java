package personalproject.eventmgmntapp.hiveup.signup.validation;

import java.util.List;

/** Data classes mirroring {@code validation-rules.json}. */
public final class ValidationRule {
    private ValidationRule() {
    }

    /**
     * Rules for one request field.
     *
     * @param name      request field name
     * @param required  whether the field must be present
     * @param errorCode catalogue code used when any rule of this field fails
     * @param rules     rules checked in order; the first failure stops checking this field
     */
    public record FieldRules(String name, boolean required, String errorCode, List<RuleSpec> rules) {
    }

    /**
     * One rule.
     *
     * @param type  rule type, e.g. {@code maxLength}
     * @param value rule argument as text (may be null for argument-less rules)
     */
    public record RuleSpec(String type, String value) {
    }

    /**
     * Whole rules file.
     *
     * @param fields per-field rules
     */
    public record RuleFile(List<FieldRules> fields) {
    }
}
