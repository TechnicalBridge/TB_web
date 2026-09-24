package com.tbridge.payments.assembler;

import com.tbridge.payments.controller.PaymentController;
import com.tbridge.payments.dto.response.HistoriaResponse;
import com.tbridge.payments.dto.response.PaymentResponse;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.BasicLinkBuilder;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Los enlaces de un pago.
 *
 * <ul>
 *   <li>{@code self}: el pago.</li>
 *   <li>{@code historia}: el libro, paso por paso.</li>
 *   <li>{@code deuda}: la deuda que paga. Vive en ms-debt, pero la direccion
 *       publica es la misma para todos: el gateway reparte por ruta.</li>
 * </ul>
 */
@Component
public class PaymentModelAssembler implements RepresentationModelAssembler<PaymentResponse, EntityModel<PaymentResponse>> {

    @Override
    public EntityModel<PaymentResponse> toModel(PaymentResponse pago) {
        return EntityModel.of(pago,
                linkTo(methodOn(PaymentController.class).one(null, pago.id())).withSelfRel(),
                linkTo(methodOn(PaymentController.class).historia(null, pago.id())).withRel("historia"),
                deuda(pago.debtId()));
    }

    @Override
    public CollectionModel<EntityModel<PaymentResponse>> toCollectionModel(Iterable<? extends PaymentResponse> pagos) {
        return RepresentationModelAssembler.super.toCollectionModel(pagos)
                .add(linkTo(methodOn(PaymentController.class).list(null)).withSelfRel());
    }

    public EntityModel<HistoriaResponse> toModel(Long pagoId, HistoriaResponse historia) {
        return EntityModel.of(historia,
                linkTo(methodOn(PaymentController.class).historia(null, pagoId)).withSelfRel(),
                linkTo(methodOn(PaymentController.class).one(null, pagoId)).withRel("pago"));
    }

    private static Link deuda(Long debtId) {
        return BasicLinkBuilder.linkToCurrentMapping().slash("api").slash("debts").slash(debtId).withRel("deuda");
    }
}
