package org.jtools.doccomparator.i18n;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Every test speaks English, whatever the program's default: the tests check the texts the program writes, in
 * English. Before a test class (whose static fields may already read documents), and again before each test (one may
 * have changed the language). Found by JUnit through its service file (src/test/resources).
 */
public final class I18nEnglishInTests implements BeforeAllCallback, BeforeEachCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        I18n.use(I18nLanguage.ENGLISH);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        I18n.use(I18nLanguage.ENGLISH);
    }
}
