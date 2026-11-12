package personalproject.eventmgmntapp.hiveup.signup.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import personalproject.eventmgmntapp.hiveup.signup.validation.ValidationRule.FieldRules;
import personalproject.eventmgmntapp.hiveup.signup.validation.ValidationRule.RuleSpec;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignupValidatorTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T10:00:00Z"), ZoneOffset.UTC);

    private SignupValidator validator;
    private Map<String, String> valid;

    @BeforeEach
    void setUp() {
        validator = new SignupValidator(RuleLoader.loadDefault(), CLOCK);
        valid = new HashMap<>();
        valid.put("first_name", "Asha");
        valid.put("last_name", "Rao");
        valid.put("gender", "Female");
        valid.put("date_of_birth", "1995-04-12");
        valid.put("email", "asha@example.com");
        valid.put("password", "Str0ng#Pass");
        valid.put("profession", "Engineer");
        valid.put("contact_number", "+919876543210");
    }

    private List<FieldError> validateWith(String field, String value) {
        Map<String, String> copy = new HashMap<>(valid);
        if (value == null) {
            copy.remove(field);
        } else {
            copy.put(field, value);
        }
        return validator.validate(copy);
    }

    private void assertInvalid(String field, String value, String expectedCode) {
        List<FieldError> errors = validateWith(field, value);
        assertEquals(1, errors.size(), "expected exactly one error for " + field + "=" + value + " but got " + errors);
        assertEquals(field, errors.get(0).field());
        assertEquals(expectedCode, errors.get(0).code());
        assertTrue(!errors.get(0).message().isBlank());
    }

    private void assertValid(String field, String value) {
        assertTrue(validateWith(field, value).isEmpty(), field + "=" + value);
    }

    @Test
    void fullyValidRequestHasNoErrors() {
        assertTrue(validator.validate(valid).isEmpty());
    }

    @Test
    void optionalFieldsMayBeAbsent() {
        assertValid("gender", null);
        assertValid("profession", null);
    }

    @Test
    void emptyRequestReportsEveryRequiredFieldAtOnce() {
        List<FieldError> errors = validator.validate(Map.of());
        assertEquals(List.of("first_name", "last_name", "date_of_birth", "email", "password", "contact_number"),
                errors.stream().map(FieldError::field).toList());
        errors.forEach(e -> assertEquals("SIGNUP_REQUIRED_FIELD_MISSING", e.code()));
    }

    @Test
    void multipleInvalidFieldsAreAllReported() {
        valid.put("first_name", "");
        valid.put("email", "nope");
        valid.put("contact_number", "123");
        assertEquals(3, validator.validate(valid).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"first_name", "last_name"})
    void nameBoundaries(String field) {
        assertValid(field, "a".repeat(100));
        assertInvalid(field, "a".repeat(101), field.equals("first_name") ? "SIGNUP_FIRST_NAME_INVALID" : "SIGNUP_LAST_NAME_INVALID");
        assertInvalid(field, "", field.equals("first_name") ? "SIGNUP_FIRST_NAME_INVALID" : "SIGNUP_LAST_NAME_INVALID");
        assertInvalid(field, "   ", field.equals("first_name") ? "SIGNUP_FIRST_NAME_INVALID" : "SIGNUP_LAST_NAME_INVALID");
        assertInvalid(field, null, "SIGNUP_REQUIRED_FIELD_MISSING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Male", "Female", "Other", "male", "FEMALE", "oThEr"})
    void genderIsCaseInsensitive(String gender) {
        assertValid("gender", gender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "x", "Non-binary", "Mal"})
    void genderRejectsOtherValues(String gender) {
        assertInvalid("gender", gender, "SIGNUP_GENDER_INVALID");
    }

    @Test
    void professionRules() {
        assertValid("profession", "a".repeat(100));
        assertInvalid("profession", "a".repeat(101), "SIGNUP_PROFESSION_INVALID");
        assertInvalid("profession", "", "SIGNUP_PROFESSION_INVALID");
        assertInvalid("profession", "  ", "SIGNUP_PROFESSION_INVALID");
    }

    @Test
    void dateOfBirthBoundaries() {
        assertValid("date_of_birth", "2026-11-10");                  // yesterday
        assertInvalid("date_of_birth", "2026-11-11", "SIGNUP_DOB_INVALID"); // today
        assertInvalid("date_of_birth", "2026-11-12", "SIGNUP_DOB_INVALID"); // future
        assertValid("date_of_birth", "1906-11-11");                   // exactly 120 years ago
        assertInvalid("date_of_birth", "1906-11-10", "SIGNUP_DOB_INVALID"); // just over 120 years
    }

    @ParameterizedTest
    @ValueSource(strings = {"2020-02-31", "2021-02-29", "1995-13-01", "12-04-1995", "1995-4-12", "1995/04/12", "abc", "", "1995-04-12T00:00"})
    void dateOfBirthRejectsBadFormatsAndImpossibleDates(String dob) {
        assertInvalid("date_of_birth", dob, "SIGNUP_DOB_INVALID");
    }

    @Test
    void leapDayIsAccepted() {
        assertValid("date_of_birth", "2000-02-29");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@example.com", "first.last+tag@sub.example.co.in", "A_B-C%d@Example.COM"})
    void validEmails(String email) {
        assertValid("email", email);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "a@b", "a@@b.com", "@example.com", "a@.com", "a@example..com", "a b@example.com", "a@example.c", "", "   "})
    void invalidEmails(String email) {
        assertInvalid("email", email, "SIGNUP_EMAIL_INVALID");
    }

    @Test
    void emailLengthLimit() {
        String suffix = "@example.com";
        assertValid("email", "a".repeat(254 - suffix.length()) + suffix);
        assertInvalid("email", "a".repeat(255 - suffix.length()) + suffix, "SIGNUP_EMAIL_INVALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abcdef1!", "Abcdefghijklmn1!", "Zz9$Zz9$Zz9$"})
    void validPasswords(String password) {
        assertValid("password", password);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Abcde1!",             // 7 chars
            "Abcdefghijklmno1!",   // 17 chars
            "abcdefg1!",           // no uppercase
            "ABCDEFG1!",           // no lowercase
            "Abcdefgh!",           // no digit
            "Abcdefg12",           // no special
            ""})
    void invalidPasswords(String password) {
        assertInvalid("password", password, "SIGNUP_PASSWORD_INVALID");
    }

    @Test
    void passwordIsNotTrimmed() {
        assertValid("password", "Abc def1!");
        assertInvalid("password", "Abcdef1 ", "SIGNUP_PASSWORD_INVALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"+919876543210", "+910000000000"})
    void validContactNumbers(String number) {
        assertValid("contact_number", number);
    }

    @ParameterizedTest
    @ValueSource(strings = {"+91987654321", "+9198765432100", "919876543210", "+441234567890", "+91 9876543210", "+91abcdefghij", "", "+91"})
    void invalidContactNumbers(String number) {
        assertInvalid("contact_number", number, "SIGNUP_CONTACT_INVALID");
    }

    @Test
    void unknownKeysInValueMapAreIgnored() {
        valid.put("favourite_colour", "blue");
        assertTrue(validator.validate(valid).isEmpty());
    }

    @Test
    void unknownRuleTypeFailsFast() {
        List<FieldRules> bad = List.of(new FieldRules("x", true, "SIGNUP_FIRST_NAME_INVALID", List.of(new RuleSpec("bogus", null))));
        assertThrows(IllegalStateException.class, () -> new SignupValidator(bad, CLOCK));
    }
}
