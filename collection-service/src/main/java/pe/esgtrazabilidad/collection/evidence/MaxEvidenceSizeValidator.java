package pe.esgtrazabilidad.collection.evidence;

import org.springframework.beans.factory.annotation.Value;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Spring builds validators through its own factory, so the limit is injected from configuration. */
class MaxEvidenceSizeValidator implements ConstraintValidator<MaxEvidenceSize, Long> {

    private final long maxBytes;

    MaxEvidenceSizeValidator(@Value("${esg.evidence.max-bytes}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public boolean isValid(Long sizeBytes, ConstraintValidatorContext context) {
        return sizeBytes == null || sizeBytes <= maxBytes;
    }
}
