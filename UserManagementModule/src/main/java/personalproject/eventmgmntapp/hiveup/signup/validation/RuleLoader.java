package personalproject.eventmgmntapp.hiveup.signup.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import personalproject.eventmgmntapp.hiveup.signup.validation.ValidationRule.FieldRules;
import personalproject.eventmgmntapp.hiveup.signup.validation.ValidationRule.RuleFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Reads field rules from a classpath JSON file. */
public final class RuleLoader {
    /** Default rules resource on the classpath. */
    public static final String DEFAULT_RESOURCE = "/validation-rules.json";

    private RuleLoader() {
    }

    /**
     * Loads the default rules.
     *
     * @return per-field rules
     */
    public static List<FieldRules> loadDefault() {
        return load(DEFAULT_RESOURCE);
    }

    /**
     * Loads rules from a classpath resource.
     *
     * @param resource absolute classpath path, e.g. {@code /validation-rules.json}
     * @return per-field rules
     * @throws IllegalStateException if the resource is missing or unreadable
     */
    public static List<FieldRules> load(String resource) {
        try (InputStream in = RuleLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            return new ObjectMapper().readValue(in, RuleFile.class).fields();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
    }
}
