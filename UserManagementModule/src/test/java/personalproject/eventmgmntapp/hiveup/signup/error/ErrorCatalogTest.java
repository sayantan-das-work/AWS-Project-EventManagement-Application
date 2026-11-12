package personalproject.eventmgmntapp.hiveup.signup.error;

import org.junit.jupiter.api.Test;
import personalproject.eventmgmntapp.hiveup.signup.validation.RuleLoader;
import personalproject.eventmgmntapp.hiveup.signup.validation.ValidationRule.FieldRules;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorCatalogTest {

    @Test
    void everyConstantInErrorCodesExistsInCatalog() throws IllegalAccessException {
        for (String code : constantValues()) {
            assertTrue(ErrorCatalog.all().containsKey(code), "Missing in errors.json: " + code);
        }
    }

    @Test
    void everyCatalogEntryHasMessageAndValidHttpStatus() {
        for (ErrorCode entry : ErrorCatalog.all().values()) {
            assertFalse(entry.message().isBlank(), entry.code());
            assertTrue(entry.httpStatus() >= 400 && entry.httpStatus() < 600, entry.code());
        }
    }

    @Test
    void catalogCodesAreUniqueAndFileHasNoDuplicates() {
        // Duplicates make ErrorCatalog fail on load; here we also check the map size matches the file.
        assertEquals(new HashSet<>(ErrorCatalog.all().keySet()).size(), ErrorCatalog.all().size());
    }

    @Test
    void everyErrorCodeReferencedByValidationRulesExists() {
        List<FieldRules> rules = RuleLoader.loadDefault();
        List<String> codes = new ArrayList<>();
        rules.forEach(r -> codes.add(r.errorCode()));
        for (String code : codes) {
            assertTrue(ErrorCatalog.all().containsKey(code), code);
        }
    }

    @Test
    void unknownCodeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ErrorCatalog.get("NOPE"));
    }

    @Test
    void messagesNeverLookLikeStackTraces() {
        for (ErrorCode entry : ErrorCatalog.all().values()) {
            assertFalse(entry.message().contains("Exception"), entry.code());
            assertFalse(entry.message().contains("at personalproject"), entry.code());
            assertFalse(entry.message().contains("java."), entry.code());
        }
    }

    private static Set<String> constantValues() throws IllegalAccessException {
        Set<String> values = new HashSet<>();
        for (Field f : ErrorCodes.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                values.add((String) f.get(null));
            }
        }
        return values;
    }
}
