package pe.esgtrazabilidad.auth.staffuser.port.in;

public record CreateStaffUserCommand(String email, String rawPassword, String fullName) {
}
