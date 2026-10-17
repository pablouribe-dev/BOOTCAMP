package com.bank.auth.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.auth.config.AdminProperties;
import com.bank.auth.dto.UserResponse;
import com.bank.auth.model.Role;
import com.bank.auth.repository.UserRepository;
import com.bank.auth.service.UserService;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    private AdminProperties credentials(String username, String password) {
        AdminProperties properties = new AdminProperties();
        properties.setUsername(username);
        properties.setPassword(password);
        return properties;
    }

    @Test
    void createsTheInitialAdminWhenNoneExists() {
        when(userRepository.existsByRolesContaining(Role.ADMIN)).thenReturn(Mono.just(false));
        when(userService.createUser("admin", "Admin123!", List.of(Role.ADMIN), null, null))
                .thenReturn(Single.just(UserResponse.builder().username("admin").build()));

        new AdminBootstrap(userRepository, userService, credentials("admin", "Admin123!")).run(null);

        verify(userService).createUser("admin", "Admin123!", List.of(Role.ADMIN), null, null);
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        when(userRepository.existsByRolesContaining(Role.ADMIN)).thenReturn(Mono.just(true));

        new AdminBootstrap(userRepository, userService, credentials("admin", "Admin123!")).run(null);

        verify(userService, never()).createUser(any(), any(), any(), any(), any());
    }

    @Test
    void warnsAndSkipsWhenCredentialsAreNotConfigured() {
        when(userRepository.existsByRolesContaining(Role.ADMIN)).thenReturn(Mono.just(false));

        new AdminBootstrap(userRepository, userService, credentials("", null)).run(null);

        verify(userService, never()).createUser(any(), any(), any(), any(), any());
    }
}
