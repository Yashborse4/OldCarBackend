package com.carselling.oldcar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service for internationalization (i18n) support
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternationalizationService {

    private final MessageSource messageSource;

    /**
     * Get localized message for current locale
     */
    public String getMessage(String code) {
        return getMessage(code, null, LocaleContextHolder.getLocale());
    }

    /**
     * Get localized message with parameters for current locale
     */
    public String getMessage(String code, Object[] args) {
        return getMessage(code, args, LocaleContextHolder.getLocale());
    }

    /**
     * Get localized message for specific locale
     */
    public String getMessage(String code, Object[] args, Locale locale) {
        try {
            return messageSource.getMessage(code, args, locale);
        } catch (Exception e) {
            log.warn("Message not found for code: {} and locale: {}", code, locale);
            return code; // Fallback to code if message not found
        }
    }

    /**
     * Get localized message for specific locale string
     */
    public String getMessage(String code, Object[] args, String localeString) {
        Locale locale = parseLocale(localeString);
        return getMessage(code, args, locale);
    }

    /**
     * Get localized validation messages
     */
    public Map<String, String> getValidationMessages(Locale locale) {
        return Map.of(
                "user.email.required", getMessage("validation.user.email.required", null, locale),
                "user.email.invalid", getMessage("validation.user.email.invalid", null, locale),
                "user.password.required", getMessage("validation.user.password.required", null, locale),
                "user.password.minLength", getMessage("validation.user.password.minLength", null, locale),
                "vehicle.make.required", getMessage("validation.vehicle.make.required", null, locale),
                "vehicle.model.required", getMessage("validation.vehicle.model.required", null, locale),
                "vehicle.price.required", getMessage("validation.vehicle.price.required", null, locale),
                "vehicle.price.positive", getMessage("validation.vehicle.price.positive", null, locale));
    }

    /**
     * Get localized error messages
     */
    public Map<String, String> getErrorMessages(Locale locale) {
        return Map.of(
                "user.notFound", getMessage("error.user.notFound", null, locale),
                "user.unauthorized", getMessage("error.user.unauthorized", null, locale),
                "vehicle.notFound", getMessage("error.vehicle.notFound", null, locale),
                "server.internal", getMessage("error.server.internal", null, locale),
                "rate.limit.exceeded", getMessage("error.rate.limit.exceeded", null, locale),
                "file.upload.failed", getMessage("error.file.upload.failed", null, locale));
    }

    /**
     * Get localized email templates
     */
    public Map<String, String> getEmailTemplates(Locale locale) {
        return Map.of(
                "welcome.subject", getMessage("email.welcome.subject", null, locale),
                "welcome.body", getMessage("email.welcome.body", null, locale),
                "verification.subject", getMessage("email.verification.subject", null, locale),
                "verification.body", getMessage("email.verification.body", null, locale),
                "password.reset.subject", getMessage("email.password.reset.subject", null, locale),
                "password.reset.body", getMessage("email.password.reset.body", null, locale));
    }

    /**
     * Get localized notification messages
     */
    public Map<String, String> getNotificationMessages(Locale locale) {
        return Map.of(
                "vehicle.new.inquiry", getMessage("notification.vehicle.new.inquiry", null, locale),
                "vehicle.price.drop", getMessage("notification.vehicle.price.drop", null, locale),
                "chat.new.message", getMessage("notification.chat.new.message", null, locale),
                "system.maintenance", getMessage("notification.system.maintenance", null, locale));
    }

    /**
     * Get all supported locales with their display names
     */
    public Map<String, String> getSupportedLocales() {
        Map<String, String> locales = new HashMap<>();
        locales.put("en-US", "English (United States)");
        locales.put("en-GB", "English (United Kingdom)");
        locales.put("es", "Español");
        locales.put("fr", "Français");
        locales.put("de", "Deutsch");
        locales.put("it", "Italiano");
        locales.put("pt", "Português");
        locales.put("ja", "日本語");
        locales.put("ko", "한국어");
        locales.put("zh", "中文");
        locales.put("ar", "العربية");
        locales.put("hi", "हिन्दी");
        locales.put("ru", "Русский");
        return locales;
    }

    /**
     * Get localized vehicle attributes
     */
    public Map<String, Object> getLocalizedVehicleAttributes(Locale locale) {
        return Map.of(
                "fuelTypes", Map.of(
                        "PETROL", getMessage("vehicle.fuelType.petrol", null, locale),
                        "DIESEL", getMessage("vehicle.fuelType.diesel", null, locale),
                        "ELECTRIC", getMessage("vehicle.fuelType.electric", null, locale),
                        "HYBRID", getMessage("vehicle.fuelType.hybrid", null, locale)),
                "transmissions", Map.of(
                        "MANUAL", getMessage("vehicle.transmission.manual", null, locale),
                        "AUTOMATIC", getMessage("vehicle.transmission.automatic", null, locale),
                        "CVT", getMessage("vehicle.transmission.cvt", null, locale),
                        "SEMI_AUTOMATIC", getMessage("vehicle.transmission.semiAutomatic", null, locale)),
                "conditions", Map.of(
                        "NEW", getMessage("vehicle.condition.new", null, locale),
                        "EXCELLENT", getMessage("vehicle.condition.excellent", null, locale),
                        "GOOD", getMessage("vehicle.condition.good", null, locale),
                        "FAIR", getMessage("vehicle.condition.fair", null, locale),
                        "POOR", getMessage("vehicle.condition.poor", null, locale)));
    }

    /**
     * Get localized API response messages
     */
    public Map<String, String> getApiResponseMessages(Locale locale) {
        return Map.of(
                "success.user.created", getMessage("api.success.user.created", null, locale),
                "success.user.updated", getMessage("api.success.user.updated", null, locale),
                "success.vehicle.created", getMessage("api.success.vehicle.created", null, locale),
                "success.vehicle.updated", getMessage("api.success.vehicle.updated", null, locale),
                "success.message.sent", getMessage("api.success.message.sent", null, locale),
                "success.file.uploaded", getMessage("api.success.file.uploaded", null, locale));
    }

    /**
     * Format currency based on locale
     */
    public String formatCurrency(double amount, Locale locale) {
        return switch (locale.getCountry()) {
            case "US" -> String.format(Locale.US, "$%.2f", amount);
            case "GB" -> String.format(Locale.UK, "£%.2f", amount);
            case "DE" -> String.format(Locale.GERMANY, "%.2f €", amount);
            case "FR" -> String.format(Locale.FRANCE, "%.2f €", amount);
            case "JP" -> String.format(Locale.JAPAN, "¥%.0f", amount);
            default -> String.format(locale, "%.2f", amount);
        };
    }

    /**
     * Get localized date format patterns
     */
    public String getDateFormat(Locale locale) {
        return switch (locale.getCountry()) {
            case "US" -> "MM/dd/yyyy";
            case "GB" -> "dd/MM/yyyy";
            case "DE", "FR", "IT" -> "dd.MM.yyyy";
            case "JP" -> "yyyy/MM/dd";
            default -> "yyyy-MM-dd";
        };
    }

    /**
     * Parse locale string to Locale object
     */
    private Locale parseLocale(String localeString) {
        if (localeString == null || localeString.isEmpty()) {
            return Locale.US;
        }

        String[] parts = localeString.split("[_-]");
        return switch (parts.length) {
            case 1 -> Locale.of(parts[0]);
            case 2 -> Locale.of(parts[0], parts[1]);
            case 3 -> Locale.of(parts[0], parts[1], parts[2]);
            default -> Locale.US;
        };
    }

    /**
     * Get current user locale
     */
    public Locale getCurrentLocale() {
        return LocaleContextHolder.getLocale();
    }

    /**
     * Check if locale is supported
     */
    public boolean isLocaleSupported(String localeString) {
        return getSupportedLocales().containsKey(localeString);
    }

    /**
     * Get best matching locale from Accept-Language header
     */
    public Locale getBestMatchingLocale(List<Locale> acceptedLocales) {
        if (acceptedLocales == null || acceptedLocales.isEmpty()) {
            return Locale.US;
        }

        // Return first supported locale
        for (Locale locale : acceptedLocales) {
            if (isLocaleSupported(locale.toString()) ||
                    isLocaleSupported(locale.getLanguage())) {
                return locale;
            }
        }

        return Locale.US; // Fallback to English
    }
}
