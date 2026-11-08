package pe.esgtrazabilidad.auth.recycleraccount.service;

import pe.esgtrazabilidad.auth.retention.RetentionConfig;

/**
 * The consent shown to the recycler (Ley N.° 29733), in two parts that are accepted apart: the general one
 * and the location one (R8, R17). The retention periods are not typed here: they are filled in from the
 * configuration, so the text always says what the system really does. Changing the wording is a new
 * consent version: raise {@code retention_config.consent_version} in the same change.
 */
final class ConsentTexts {

    private ConsentTexts() {
    }

    static String general(RetentionConfig config) {
        return "Autorizo a mi asociación y a la plataforma de trazabilidad ESG a tratar mis datos personales "
                + "(mi nombre, los registros de mis recojos, el peso y el tipo de residuo, y las fotos de evidencia) "
                + "con la única finalidad de registrar y verificar mi trabajo de recojo, conforme a la Ley N.° 29733, "
                + "Ley de Protección de Datos Personales. Las fotos de evidencia se conservan "
                + months(config.photoMonths())
                + " y luego se borran. Los registros y sus totales se conservan sin límite de tiempo, sin mis datos "
                + "personales. Puedo ejercer mis derechos de acceso, rectificación, cancelación y oposición con el "
                + "personal de mi asociación.";
    }

    static String location(RetentionConfig config) {
        return "Autorizo el uso de mi ubicación (GPS) durante mi jornada para registrar mi ruta y verificar mis "
                + "visitas. La ubicación exacta se conserva "
                + days(config.gpsDays())
                + "; después solo queda un resumen de la ruta. Puedo rechazar esta autorización y seguir usando el "
                + "resto de la aplicación, pero no podré abrir una jornada con GPS.";
    }

    private static String days(int days) {
        return days + (days == 1 ? " día" : " días");
    }

    private static String months(int months) {
        return months + (months == 1 ? " mes" : " meses");
    }
}
