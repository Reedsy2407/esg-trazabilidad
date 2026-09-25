package pe.esgtrazabilidad.auth.staffuser.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.in.CreateStaffUserCommand;
import pe.esgtrazabilidad.auth.staffuser.port.in.CreateStaffUserUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.in.GetCurrentStaffUserUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.in.LoginUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

@Service
class StaffUserService implements LoginUseCase, CreateStaffUserUseCase, GetCurrentStaffUserUseCase {

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
                .findByEmail(email.toLowerCase())
                .filter(StaffUser::isActive)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
                .orElseThrow(() -> new ApplicationException(AuthErrors.INVALID_CREDENTIALS));
        return jwtIssuer.issue(staffUser.getId(), staffUser.getEmail());
    }

    @Override
    @Transactional
    public StaffUser createStaffUser(CreateStaffUserCommand command) {
        staffUserRepository.findByEmail(command.email().toLowerCase()).ifPresent(existing -> {
            throw new ApplicationException(AuthErrors.DUPLICATE_STAFF_EMAIL);
        });
        StaffUser staffUser =
                StaffUser.create(command.email(), passwordEncoder.encode(command.rawPassword()), command.fullName());
        return staffUserRepository.save(staffUser);
    }

    @Override
    public StaffUser getCurrent(UUID staffUserId) {
        return staffUserRepository
                .findById(staffUserId)
                .orElseThrow(() -> new ApplicationException(AuthErrors.STAFF_USER_NOT_FOUND));
    }
}
