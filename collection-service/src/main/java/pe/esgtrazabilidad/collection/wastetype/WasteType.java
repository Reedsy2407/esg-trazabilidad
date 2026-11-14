package pe.esgtrazabilidad.collection.wastetype;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A row of the waste-type catalogue (spec A.5b). Read-only for the application: managed as data. */
@Entity
@Table(name = "waste_type")
public class WasteType {

    @Id
    @Column(name = "code", length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected WasteType() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
