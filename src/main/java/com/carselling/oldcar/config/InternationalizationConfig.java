package com.carselling.oldcar.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.Arrays;
import java.util.Locale;

/**
 * Configuration for internationalization (i18n) support
 */
@Configuration
public class InternationalizationConfig implements WebMvcConfigurer {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
        localeResolver.setSupportedLocales(Arrays.asList(
            Locale.US,          // English (US)
            Locale.UK,          // English (UK)
            new Locale("es"),   // Spanish
            new Locale("fr"),   // French
            new Locale("de"),   // German
            new Locale("it"),   // Italian
            new Locale("pt"),   // Portuguese   
            new Locale("ja"),   // Japanese
            new Locale("ko"),   // Korean
            new Locale("zh"),   // Chinese
            new Locale("ar"),   // Arabic
            new Locale("hi"),   // Hindi
            new Locale("ru")    // Russian
        ));
        localeResolver.setDefaultLocale(Locale.US);
        return localeResolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames(
            "messages/messages",           // General messages
            "messages/validation",         // Validation messages
            "messages/email",             // Email templates
            "messages/notifications",      // Notification messages
            "messages/errors"             // Error messages
        );
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setUseCodeAsDefaultMessage(true);
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
