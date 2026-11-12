package personalproject.eventmgmntapp.hiveup.signup.validation;

import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCatalog;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.validation.ValidationRule.FieldRules;
import personalproject.eventmgmntapp.hiveup.signup.validation.ValidationRule.RuleSpec;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Rule-driven validator: applies rules from {@code validation-rules.json} to field values. */
public class SignupValidator {
    private static final DateTimeFormatter ISO_DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final Clock clock;
    private final List<CompiledField> fields;

    /**
     * @param rules per-field rules (see {@link RuleLoader})
     * @param clock source of "today" (UTC date)
     * @throws IllegalStateException if a rule type is unknown or an argument is malformed
     */
    public SignupValidator(List<FieldRules> rules, Clock clock) {
        this.clock = clock;
        this.fields = rules.stream().map(this::compile).toList();
    }

    /**
     * Validates all fields and collects every failure.
     *
     * @param values field name to raw value (missing or null means absent)
     * @return field errors in rule-file order; empty when valid
     */
    public List<FieldError> validate(Map<String, String> values) {
        List<FieldError> errors = new ArrayList<>();
        for (CompiledField field : fields) {
            String value = values.get(field.name);
            if (value == null) {
                if (field.required) {
                    errors.add(error(field.name, ErrorCodes.REQUIRED_FIELD_MISSING));
                }
                continue;
            }
            for (Predicate<String> rule : field.checks) {
                if (!rule.test(value)) {
                    errors.add(error(field.name, field.errorCode));
                    break;
                }
            }
        }
        return errors;
    }

    private static FieldError error(String field, String code) {
        return new FieldError(field, code, ErrorCatalog.get(code).message());
    }

    private CompiledField compile(FieldRules rules) {
        List<Predicate<String>> checks = rules.rules().stream().map(this::compileRule).toList();
        return new CompiledField(rules.name(), rules.required(), rules.errorCode(), checks);
    }

    /** Builds a predicate (true = valid) for one rule. */
    private Predicate<String> compileRule(RuleSpec spec) {
        String type = spec.type();
        String arg = spec.value();
        return switch (type) {
            case "notBlank" -> v -> !v.isBlank();
            case "maxLength" -> {
                int max = Integer.parseInt(arg);
                yield v -> v.length() <= max;
            }
            case "minLength" -> {
                int min = Integer.parseInt(arg);
                yield v -> v.length() >= min;
            }
            case "pattern" -> {
                Pattern pattern = Pattern.compile(arg);
                yield v -> pattern.matcher(v).matches();
            }
            case "enumIgnoreCase" -> {
                Set<String> allowed = Arrays.stream(arg.split(","))
                        .map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
                yield v -> allowed.contains(v.trim().toLowerCase(Locale.ROOT));
            }
            case "dateFormat" -> {
                DateTimeFormatter format = DateTimeFormatter.ofPattern(arg).withResolverStyle(ResolverStyle.STRICT);
                yield v -> {
                    try {
                        LocalDate.parse(v, format);
                        return true;
                    } catch (DateTimeParseException e) {
                        return false;
                    }
                };
            }
            case "pastDate" -> v -> parseDate(v) != null && parseDate(v).isBefore(today());
            case "maxAgeYears" -> {
                int years = Integer.parseInt(arg);
                yield v -> parseDate(v) != null && !parseDate(v).isBefore(today().minusYears(years));
            }
            case "passwordClasses" -> {
                String specials = arg;
                yield v -> hasPasswordClasses(v, specials);
            }
            default -> throw new IllegalStateException("Unknown validation rule type: " + type);
        };
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, ISO_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean hasPasswordClasses(String value, String specials) {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (char c : value.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                upper = true;
            } else if (c >= 'a' && c <= 'z') {
                lower = true;
            } else if (c >= '0' && c <= '9') {
                digit = true;
            } else if (specials.indexOf(c) >= 0) {
                special = true;
            }
        }
        return upper && lower && digit && special;
    }

    private record CompiledField(String name, boolean required, String errorCode, List<Predicate<String>> checks) {
    }
}
