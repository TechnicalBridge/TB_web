package com.tbridge.auth.assembler;

import com.tbridge.auth.controller.AuthController;
import com.tbridge.auth.dto.response.MeResponse;
import com.tbridge.auth.dto.response.SesionResponse;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Los enlaces de una sesion: a donde ir con ella.
 *
 * <ul>
 *   <li>{@code yo}: quien es el dueno del JWT.</li>
 *   <li>{@code renovar}: el JWT siguiente, con la cookie de renovacion.</li>
 *   <li>{@code cerrar-sesion}: revoca la sesion en el servidor.</li>
 * </ul>
 *
 * <p>Los enlaces salen con la direccion publica (el gateway o nginx), no con la
 * del contenedor: el servicio respeta las cabeceras X-Forwarded-* que le pasa
 * el gateway ({@code server.forward-headers-strategy=framework}).
 */
@Component
public class SesionModelAssembler implements RepresentationModelAssembler<SesionResponse, EntityModel<SesionResponse>> {

    @Override
    public EntityModel<SesionResponse> toModel(SesionResponse sesion) {
        return EntityModel.of(sesion, yo(), renovar(), cerrarSesion());
    }

    public EntityModel<MeResponse> toModel(MeResponse yo) {
        return EntityModel.of(yo, yo().withSelfRel(), renovar(), cerrarSesion());
    }

    private static Link yo() {
        return linkTo(methodOn(AuthController.class).me(null)).withRel("yo");
    }

    private static Link renovar() {
        return linkTo(methodOn(AuthController.class).refresh(null, null)).withRel("renovar");
    }

    private static Link cerrarSesion() {
        return linkTo(methodOn(AuthController.class).logout(null)).withRel("cerrar-sesion");
    }
}
