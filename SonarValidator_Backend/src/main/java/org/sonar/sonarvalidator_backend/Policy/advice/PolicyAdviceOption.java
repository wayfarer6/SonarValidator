package org.sonar.sonarvalidator_backend.Policy.advice;

public record PolicyAdviceOption(String title,
                                 String approach,
                                 String securityImpact,
                                 String operationalCost,
                                 boolean recommended) {

    public boolean isUsable() {
        return title != null && !title.isBlank()
                && approach != null && !approach.isBlank();
    }
}
