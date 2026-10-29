package com.cloudforgeci.api.core.rules;

import com.cloudforgeci.api.core.SystemContext;

/**
 * Reads a boolean setting for a compliance rule, with the value to use when the setting is absent
 * or cannot be read. Rule classes share these two readers instead of each carrying a copy.
 */
final class RuleSettings {

    private RuleSettings() {
    }

    /**
     * The value under {@code key} in the raw deployment context map, parsed as a boolean. This sees
     * any key the user put in the map, whether or not the typed context has an accessor for it.
     */
    static boolean contextValue(SystemContext ctx, String key, boolean defaultValue) {
        try {
            String value = ctx.cfc.getContextValue(key, String.valueOf(defaultValue));
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * The value of the {@code Boolean} accessor on the deployment context named {@code key}. A key
     * with no such accessor always yields {@code defaultValue}, because the lookup fails and the
     * default is returned; {@code RuleSettingsTest} lists the keys that do this.
     */
    static boolean accessorValue(SystemContext ctx, String key, boolean defaultValue) {
        try {
            var method = ctx.cfc.getClass().getMethod(key);
            Boolean value = (Boolean) method.invoke(ctx.cfc);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
