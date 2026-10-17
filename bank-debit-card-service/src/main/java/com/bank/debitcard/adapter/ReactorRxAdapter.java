package com.bank.debitcard.adapter;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Patron Adapter: unico punto de contacto entre los tipos de Reactor
 * ({@link Mono}, {@link Flux}) que devuelven los repositorios y WebClient, y
 * los tipos de RxJava 3 que usan los servicios y controladores.
 *
 * <p>Si cambia la libreria de conversion, solo se toca esta clase.</p>
 */
public final class ReactorRxAdapter {

    /**
     * Clase de utilidad: no se instancia.
     */
    private ReactorRxAdapter() {
    }

    /**
     * Convierte un {@link Mono} que siempre emite un valor en un {@link Single}.
     *
     * @param source mono de origen
     * @param <T>    tipo del valor
     * @return single equivalente
     */
    public static <T> Single<T> monoToSingle(Mono<T> source) {
        return RxJava3Adapter.monoToSingle(source);
    }

    /**
     * Convierte un {@link Mono} que puede venir vacio en un {@link Maybe}.
     *
     * @param source mono de origen
     * @param <T>    tipo del valor
     * @return maybe equivalente
     */
    public static <T> Maybe<T> monoToMaybe(Mono<T> source) {
        return RxJava3Adapter.monoToMaybe(source);
    }

    /**
     * Convierte un {@link Flux} en un {@link Flowable}.
     *
     * @param source flux de origen
     * @param <T>    tipo de los elementos
     * @return flowable equivalente
     */
    public static <T> Flowable<T> fluxToFlowable(Flux<T> source) {
        return RxJava3Adapter.fluxToFlowable(source);
    }

    /**
     * Convierte un {@link Mono} sin valor util en un {@link Completable}.
     *
     * @param source mono de origen
     * @return completable que termina cuando termina el mono
     */
    public static Completable monoToCompletable(Mono<?> source) {
        return RxJava3Adapter.monoToCompletable(source);
    }
}
