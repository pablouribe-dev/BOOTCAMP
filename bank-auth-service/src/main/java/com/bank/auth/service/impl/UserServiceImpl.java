package com.bank.auth.service.impl;

import com.bank.auth.adapter.ReactorRxAdapter;
import com.bank.auth.dto.UserCreateRequest;
import com.bank.auth.dto.UserResponse;
import com.bank.auth.dto.UserUpdateRequest;
import com.bank.auth.exception.BusinessRuleException;
import com.bank.auth.exception.ResourceNotFoundException;
import com.bank.auth.mapper.UserMapper;
import com.bank.auth.model.Role;
import com.bank.auth.model.User;
import com.bank.auth.repository.UserRepository;
import com.bank.auth.service.UserService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Implementacion de la administracion de usuarios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<UserResponse> create(UserCreateRequest request) {
        return createUser(request.getUsername(), request.getPassword(), request.getRoles(), request.getCustomerId(),
                request.getPhoneNumber())
                .flatMap(created -> Boolean.FALSE.equals(request.getEnabled())
                        ? update(created.getId(), UserUpdateRequest.builder().roles(created.getRoles())
                                .customerId(created.getCustomerId()).phoneNumber(created.getPhoneNumber())
                                .enabled(false).build())
                        : Single.just(created));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Rechaza con 409 un nombre de usuario repetido.</p>
     */
    @Override
    public Single<UserResponse> createUser(String username, String rawPassword, List<Role> roles, String customerId,
                                           String phoneNumber) {
        log.info("Creating user {} with roles {}", username, roles);
        return ReactorRxAdapter.monoToSingle(userRepository.existsByUsername(username))
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.warn("Username {} is already taken", username);
                        return Single.error(new BusinessRuleException("Username " + username + " is already taken"));
                    }
                    User user = User.builder()
                            .username(username)
                            .passwordHash(passwordEncoder.encode(rawPassword))
                            .roles(new ArrayList<>(roles))
                            .customerId(customerId)
                            .phoneNumber(phoneNumber)
                            .enabled(true)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return ReactorRxAdapter.monoToSingle(userRepository.save(user));
                })
                .map(userMapper::toResponse)
                .doOnSuccess(user -> log.info("User {} created with id={}", user.getUsername(), user.getId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flowable<UserResponse> findAll() {
        return ReactorRxAdapter.fluxToFlowable(userRepository.findAll()).map(userMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<UserResponse> findById(String id) {
        return findOrFail(id).map(userMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<UserResponse> update(String id, UserUpdateRequest request) {
        log.info("Updating user id={}", id);
        return findOrFail(id)
                .map(user -> {
                    user.setRoles(new ArrayList<>(request.getRoles()));
                    user.setCustomerId(request.getCustomerId());
                    user.setPhoneNumber(request.getPhoneNumber());
                    Optional.ofNullable(request.getEnabled()).ifPresent(user::setEnabled);
                    Optional.ofNullable(request.getPassword())
                            .filter(password -> !password.isBlank())
                            .ifPresent(password -> user.setPasswordHash(passwordEncoder.encode(password)));
                    return user;
                })
                .flatMap(user -> ReactorRxAdapter.monoToSingle(userRepository.save(user)))
                .map(userMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable delete(String id) {
        log.info("Deleting user id={}", id);
        return findOrFail(id).flatMapCompletable(user -> ReactorRxAdapter.monoToCompletable(
                userRepository.delete(user)));
    }

    /**
     * Recupera un usuario o falla con 404.
     *
     * @param id identificador
     * @return el usuario
     */
    private Single<User> findOrFail(String id) {
        return ReactorRxAdapter.monoToMaybe(userRepository.findById(id))
                .switchIfEmpty(Single.error(() -> new ResourceNotFoundException("User not found with id " + id)));
    }
}
