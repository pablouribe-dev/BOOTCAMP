package com.bank.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.auth.dto.UserCreateRequest;
import com.bank.auth.dto.UserUpdateRequest;
import com.bank.auth.exception.BusinessRuleException;
import com.bank.auth.exception.ResourceNotFoundException;
import com.bank.auth.mapper.UserMapper;
import com.bank.auth.model.Role;
import com.bank.auth.model.User;
import com.bank.auth.repository.UserRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, new UserMapper(), passwordEncoder);
    }

    private void saveReturnsArgument() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId("u1");
            }
            return Mono.just(user);
        });
    }

    @Test
    void createStoresABcryptHashAndNeverReturnsIt() {
        when(userRepository.existsByUsername("ana")).thenReturn(Mono.just(false));
        saveReturnsArgument();

        service.create(UserCreateRequest.builder().username("ana").password("Secret123").roles(List.of(Role.ADMIN))
                        .build())
                .test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(user -> "u1".equals(user.getId()) && user.getRoles().equals(List.of(Role.ADMIN))
                        && user.isEnabled());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches("Secret123", captor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void createDisabledUser() {
        when(userRepository.existsByUsername("off")).thenReturn(Mono.just(false));
        saveReturnsArgument();
        when(userRepository.findById("u1")).thenAnswer(invocation -> Mono.just(User.builder().id("u1")
                .username("off").roles(List.of(Role.USER)).enabled(true).build()));

        service.create(UserCreateRequest.builder().username("off").password("Secret123").roles(List.of(Role.USER))
                        .enabled(false).build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertValue(user -> !user.isEnabled());
    }

    @Test
    void duplicatedUsernameIs409() {
        when(userRepository.existsByUsername("ana")).thenReturn(Mono.just(true));

        service.createUser("ana", "Secret123", List.of(Role.USER), null, null)
                .test().awaitDone(1, TimeUnit.SECONDS).assertError(BusinessRuleException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void findAllFindByIdAndMissingUser() {
        User user = User.builder().id("u1").username("ana").roles(List.of(Role.USER)).build();
        when(userRepository.findAll()).thenReturn(Flux.just(user));
        when(userRepository.findById("u1")).thenReturn(Mono.just(user));
        when(userRepository.findById("x")).thenReturn(Mono.empty());

        service.findAll().test().awaitDone(1, TimeUnit.SECONDS).assertValueCount(1);
        service.findById("u1").test().awaitDone(1, TimeUnit.SECONDS).assertValue(found -> "ana".equals(
                found.getUsername()));
        service.findById("x").test().awaitDone(1, TimeUnit.SECONDS).assertError(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesRolesAndPasswordOnlyWhenSent() {
        User user = User.builder().id("u1").username("ana").passwordHash("old").roles(List.of(Role.USER)).build();
        when(userRepository.findById("u1")).thenReturn(Mono.just(user));
        saveReturnsArgument();

        service.update("u1", UserUpdateRequest.builder().roles(List.of(Role.ADMIN)).phoneNumber("999").build())
                .test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(updated -> updated.getRoles().equals(List.of(Role.ADMIN))
                        && "999".equals(updated.getPhoneNumber()));
        assertThat(user.getPasswordHash()).isEqualTo("old");

        service.update("u1", UserUpdateRequest.builder().roles(List.of(Role.ADMIN)).password("NewSecret1")
                        .enabled(false).build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertValue(updated -> !updated.isEnabled());
        assertThat(passwordEncoder.matches("NewSecret1", user.getPasswordHash())).isTrue();
    }

    @Test
    void deleteRemovesTheUser() {
        User user = User.builder().id("u1").username("ana").build();
        when(userRepository.findById("u1")).thenReturn(Mono.just(user));
        when(userRepository.delete(user)).thenReturn(Mono.empty());

        service.delete("u1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        verify(userRepository).delete(user);
    }
}
