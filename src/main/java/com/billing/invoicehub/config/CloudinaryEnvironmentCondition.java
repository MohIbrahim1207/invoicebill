package com.billing.invoicehub.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Enables Cloudinary beans only when the required Railway environment variables are present.
 */
public class CloudinaryEnvironmentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isPresent(System.getenv("CLOUDINARY_CLOUD_NAME"))
                && isPresent(System.getenv("CLOUDINARY_API_KEY"))
                && isPresent(System.getenv("CLOUDINARY_API_SECRET"));
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}

