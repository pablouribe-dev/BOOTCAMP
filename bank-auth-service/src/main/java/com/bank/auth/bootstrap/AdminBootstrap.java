package com.bank.auth.bootstrap;

import com.bank.auth.adapter.ReactorRxAdapter;
import com.bank.auth.config.AdminProperties;
import com.bank.auth.model.Role;
import com.bank.auth.repository.UserRepository;
import com.bank.auth.service.UserService;
import io.reactivex.rxjava3.core.Completable;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, si no existe ningun {@code ADMIN}, crea uno con las credenciales
 * de {@code ADMIN_USERNAME} y {@code ADMIN_PASSWORD}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final UserService userService;
    private final AdminProperties adminProperties;

    /**
     * Ejecuta la verificacion al terminar el arranque.
     *
     * @param args argumentos de la aplicacion
     */
    @Override
    public void run(ApplicationArguments args) {
        ensureAdminExists().blockingAwait();
    }

    /**
     * Crea el administrador inicial si hace falta.
     *
     * @return completable que termina cuando hay un administrador (o no se pudo crear)
     */
    public Completable ensureAdminExists() {
        return ReactorRxAdapter.monoToSingle(userRepository.existsByRolesContaining(Role.ADMIN))
                .flatMapCompletable(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.info("An ADMIN user already exists");
                        return Completable.complete();
                    }
                    if (isBlank(adminProperties.getUsername()) || isBlank(adminProperties.getPassword())) {
                        log.warn("No ADMIN user exists and ADMIN_USERNAME/ADMIN_PASSWORD are not set");
                        return Completable.complete();
                    }
                    return userService.createUser(adminProperties.getUsername(), adminProperties.getPassword(),
                                    List.of(Role.ADMIN), null, null)
                            .doOnSuccess(admin -> log.info("Initial ADMIN user {} created", admin.getUsername()))
                            .ignoreElement();
                });
    }

    /**
     * Indica si el texto esta vacio.
     *
     * @param value texto
     * @return {@code true} si es nulo o en blanco
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
