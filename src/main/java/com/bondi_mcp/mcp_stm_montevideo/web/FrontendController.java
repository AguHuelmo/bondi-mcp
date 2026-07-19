package com.bondi_mcp.mcp_stm_montevideo.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Las rutas del frontend React, cuando viaja compilado dentro del jar (deploy con Docker).
 *
 * <p>El frontend es una SPA con rutas propias: entrar directo a {@code /viaje} tiene que servir
 * el {@code index.html} y dejar que React Router resuelva. Sin esto, un link compartido por
 * WhatsApp daría 404. Son las rutas exactas de {@code App.tsx}, sin comodines: {@code /api},
 * {@code /mcp} y {@code /webhook} no pasan por acá ni de casualidad.
 *
 * <p>En desarrollo no molesta: el frontend corre aparte con Vite, y sin {@code index.html} en el
 * jar esto solo convierte un 404 en otro 404.
 */
@Controller
public class FrontendController {

    @GetMapping({"/paradas", "/viaje", "/lineas/{linea}"})
    public String frontend() {
        return "forward:/index.html";
    }
}
