package personalproject.eventmgmntapp.hiveup.signup.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import personalproject.eventmgmntapp.hiveup.signup.util.EmailRedactor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Signup request body. Unknown JSON properties are ignored by the mapper.
 *
 * @param firstName     first name
 * @param lastName      last name
 * @param gender        optional gender
 * @param dateOfBirth   date of birth, YYYY-MM-DD
 * @param email         email (also the Cognito username)
 * @param password      password, passed only to Cognito
 * @param profession    optional profession
 * @param contactNumber Indian contact number, +91XXXXXXXXXX
 */
public record SignupRequest(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("gender") String gender,
        @JsonProperty("date_of_birth") String dateOfBirth,
        @JsonProperty("email") String email,
        @JsonProperty("password") String password,
        @JsonProperty("profession") String profession,
        @JsonProperty("contact_number") String contactNumber) {

    /** @return copy with leading/trailing whitespace trimmed from the email only (case kept) */
    public SignupRequest withTrimmedEmail() {
        String trimmed = email == null ? null : email.trim();
        return new SignupRequest(firstName, lastName, gender, dateOfBirth, trimmed, password, profession, contactNumber);
    }

    /** @return field values keyed by JSON field name, for the rule-driven validator */
    public Map<String, String> toFieldMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("first_name", firstName);
        map.put("last_name", lastName);
        map.put("gender", gender);
        map.put("date_of_birth", dateOfBirth);
        map.put("email", email);
        map.put("password", password);
        map.put("profession", profession);
        map.put("contact_number", contactNumber);
        return map;
    }

    /** @return text that excludes the password and redacts the email address */
    @Override
    public String toString() {
        return String.format("SignupRequest{firstName=%s, lastName=%s, gender=%s, dateOfBirth=%s, email=%s, profession=%s, contactNumber=%s}", firstName, lastName, gender, dateOfBirth, EmailRedactor.redact(email), profession, contactNumber);
    }
}
