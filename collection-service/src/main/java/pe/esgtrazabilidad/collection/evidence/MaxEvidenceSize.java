package pe.esgtrazabilidad.collection.evidence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** The announced photo size must not exceed {@code esg.evidence.max-bytes} (a property, not a constant). */
@Documented
@Constraint(validatedBy = MaxEvidenceSizeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxEvidenceSize {

    String message() default "El tamaño de la foto supera el máximo permitido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
