package com.bank.transaction.service.impl;

import com.bank.transaction.adapter.ReactorRxAdapter;
import com.bank.transaction.client.AccountClient;
import com.bank.transaction.client.CreditCardClient;
import com.bank.transaction.client.CreditClient;
import com.bank.transaction.config.ReportProperties;
import com.bank.transaction.dto.GeneralReportResponse;
import com.bank.transaction.dto.ProductReportResponse;
import com.bank.transaction.dto.ProductTypeSummary;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.exception.InvalidRequestException;
import com.bank.transaction.mapper.TransactionMapper;
import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.ReportService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion de los reportes. Las agrupaciones y totales se calculan con
 * streams; las consultas son metodos derivados de Spring Data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final AccountClient accountClient;
    private final CreditClient creditClient;
    private final CreditCardClient creditCardClient;
    private final ReportProperties reportProperties;

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<ProductReportResponse> productReport(ProductType productType, String productId, LocalDate from,
                                                       LocalDate to) {
        log.info("Building report of {} id={} from {} to {}", productType, productId, from, to);
        return validateRange(from, to)
                .flatMap(range -> productSnapshot(productType, productId)
                        .flatMap(snapshot -> ReactorRxAdapter.fluxToFlowable(transactionRepository
                                        .findByProductIdAndCreatedAtBetweenOrderByCreatedAtAsc(productId, range[0],
                                                range[1]))
                                .toList()
                                .map(movements -> ProductReportResponse.builder()
                                        .productType(productType)
                                        .productId(productId)
                                        .customerId(snapshot.customerId())
                                        .product(snapshot.product())
                                        .currentBalance(snapshot.currentBalance())
                                        .from(from)
                                        .to(to)
                                        .movementCount(movements.size())
                                        .totalsByType(totalsByType(movements))
                                        .totalFees(totalFees(movements))
                                        .movements(movements.stream().map(transactionMapper::toResponse).toList())
                                        .build())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<GeneralReportResponse> generalReport(LocalDate from, LocalDate to) {
        log.info("Building general report from {} to {}", from, to);
        return validateRange(from, to)
                .flatMap(range -> ReactorRxAdapter.fluxToFlowable(
                                transactionRepository.findByCreatedAtBetween(range[0], range[1]))
                        .toList()
                        .map(movements -> GeneralReportResponse.builder()
                                .from(from)
                                .to(to)
                                .totalMovements(movements.size())
                                .byProductType(summaries(movements))
                                .build()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Solo admite los tipos configurados en
     * {@code bank.reports.last-movements-product-types}.</p>
     */
    @Override
    public Flowable<TransactionResponse> lastMovements(ProductType productType, String productId) {
        if (!reportProperties.getLastMovementsProductTypes().contains(productType)) {
            log.warn("Last movements report requested for unsupported product type {}", productType);
            return Flowable.error(new InvalidRequestException("The last movements report is only available for "
                    + reportProperties.getLastMovementsProductTypes()));
        }
        log.debug("Last movements of {} id={}", productType, productId);
        // Parte III: los movimientos de la tarjeta de debito llegan por eventos y
        // no se consulta por REST al servicio nuevo; para el resto se valida que
        // el producto exista (404 si no).
        Completable productExists = productType == ProductType.DEBIT_CARD
                ? Completable.complete()
                : productSnapshot(productType, productId).ignoreElement();
        return productExists
                .andThen(Flowable.defer(() -> ReactorRxAdapter.fluxToFlowable(transactionRepository
                        .findTop10ByProductTypeAndProductIdOrderByCreatedAtDesc(productType, productId))))
                .map(transactionMapper::toResponse);
    }

    /**
     * Valida el intervalo y lo convierte a fechas y horas inclusive.
     *
     * @param from inicio del intervalo
     * @param to   fin del intervalo
     * @return arreglo {@code [inicio del dia from, fin del dia to]}
     */
    private Single<LocalDateTime[]> validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return Single.error(new InvalidRequestException("'from' must not be after 'to'"));
        }
        return Single.just(new LocalDateTime[] {from.atStartOfDay(), to.plusDays(1).atStartOfDay().minusNanos(1)});
    }

    /**
     * Obtiene los datos y el saldo actual del producto desde su servicio dueno.
     * Si el producto no existe, responde 404.
     *
     * @param productType tipo de producto
     * @param productId   identificador del producto
     * @return datos del producto
     */
    private Single<ProductSnapshot> productSnapshot(ProductType productType, String productId) {
        return switch (productType) {
            case ACCOUNT -> accountClient.findById(productId)
                    .map(account -> new ProductSnapshot(account, account.getBalance(), account.getCustomerId()));
            case CREDIT -> creditClient.findById(productId)
                    .map(credit -> new ProductSnapshot(credit, credit.getOutstandingBalance(),
                            credit.getCustomerId()));
            case CREDIT_CARD -> creditCardClient.findById(productId)
                    .map(card -> new ProductSnapshot(card, card.getAvailableBalance(), card.getCustomerId()));
            case DEBIT_CARD -> Single.error(new InvalidRequestException(
                    "The product report is not available for DEBIT_CARD: use /reports/last-movements"));
        };
    }

    /**
     * Arma un resumen por tipo de producto.
     *
     * @param movements movimientos del intervalo
     * @return resumenes ordenados por tipo de producto
     */
    private List<ProductTypeSummary> summaries(List<Transaction> movements) {
        return movements.stream()
                .collect(Collectors.groupingBy(Transaction::getProductType,
                        () -> new EnumMap<>(ProductType.class), Collectors.toList()))
                .entrySet().stream()
                .map(entry -> ProductTypeSummary.builder()
                        .productType(entry.getKey())
                        .productsWithMovements(entry.getValue().stream()
                                .map(Transaction::getProductId).distinct().count())
                        .movementCount(entry.getValue().size())
                        .totalAmount(sum(entry.getValue()))
                        .totalFees(totalFees(entry.getValue()))
                        .totalsByType(totalsByType(entry.getValue()))
                        .build())
                .sorted(Comparator.comparing(ProductTypeSummary::getProductType))
                .toList();
    }

    /**
     * Importe total por tipo de movimiento.
     *
     * @param movements movimientos
     * @return mapa tipo de movimiento a importe total
     */
    private Map<TransactionType, BigDecimal> totalsByType(List<Transaction> movements) {
        return movements.stream()
                .collect(Collectors.groupingBy(Transaction::getTransactionType,
                        () -> new EnumMap<>(TransactionType.class),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));
    }

    /**
     * Suma de los importes.
     *
     * @param movements movimientos
     * @return importe total
     */
    private BigDecimal sum(List<Transaction> movements) {
        return movements.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Suma de las comisiones cobradas.
     *
     * @param movements movimientos
     * @return comisiones totales
     */
    private BigDecimal totalFees(List<Transaction> movements) {
        return movements.stream()
                .map(transaction -> Optional.ofNullable(transaction.getFeeCharged()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Datos del producto consultados a su servicio dueno.
     *
     * @param product        DTO del producto
     * @param currentBalance saldo actual relevante para el tipo de producto
     * @param customerId     cliente dueno
     */
    private record ProductSnapshot(Object product, BigDecimal currentBalance, String customerId) {
    }
}
