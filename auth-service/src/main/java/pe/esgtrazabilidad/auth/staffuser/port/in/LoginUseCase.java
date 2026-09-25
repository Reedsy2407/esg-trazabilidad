package pe.esgtrazabilidad.auth.staffuser.port.in;

public interface LoginUseCase {

    String login(String email, String rawPassword);
}
