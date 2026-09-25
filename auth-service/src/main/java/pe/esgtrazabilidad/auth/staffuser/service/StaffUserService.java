package pe.esgtrazabilidad.auth.staffuser.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.in.LoginUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

@Service
class StaffUserService implements LoginUseCase {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    StaffUserService(StaffUserRepository staffUserRepository, PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer) {
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    @Override
    @Transactional
    public String login(String email, String rawPassword) {
        StaffUser staffUser = staffUserRepository
                .findByEmail(email)
                .filter(StaffUser::isActive)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .orElseThrow(() -> new ApplicationException(AuthErrors.INVALID_CREDENTIALS));
        return jwtIssuer.issue(staffUser.getId(), staffUser.getEmail());
    }
}
