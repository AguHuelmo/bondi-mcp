package com.bondi_mcp.mcp_stm_montevideo.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import com.bondi_mcp.mcp_stm_montevideo.client.Geocoder;
import com.bondi_mcp.mcp_stm_montevideo.client.GeocoderException;
import com.bondi_mcp.mcp_stm_montevideo.config.ParadasProperties;
import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.DireccionUbicada;
import com.bondi_mcp.mcp_stm_montevideo.domain.LugarUbicado;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.domain.PuntoDeReferencia;
import com.bondi_mcp.mcp_stm_montevideo.domain.ResultadoBusqueda;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaEntity;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ParadaServiceTest {

    private static final Parada PARADA =
            new Parada(546L, "CORUÑA", "PURIFICACION", new Coordenada(-34.903778, -56.196167));

    @Mock
    private ParadaRepository repository;

    @Mock
    private ParadaCacheService cacheService;

    @Mock
    private Geocoder geocoder;

    private ParadaService service() {
        return new ParadaService(repository, cacheService,
                new ParadasProperties(Duration.ofHours(24), 20), geocoder);
    }

    private static ParadaEntity entidadDe(Parada parada) {
        return entidadDe(parada, "546 CORUNA PURIFICACION");
    }

    private static ParadaEntity entidadDe(Parada parada, String busqueda) {
        return ParadaEntity.desde(parada, busqueda, java.time.Instant.now());
    }

    /** Una fila de {@code cercanasA}, que es una proyección y no una entidad. */
    private static ParadaRepository.ParadaConDistancia filaCercana(Parada parada, int metros) {
        return new ParadaRepository.ParadaConDistancia() {

            @Override
            public long getCodigo() {
                return parada.codigo();
            }

            @Override
            public String getCalle() {
                return parada.calle();
            }

            @Override
            public String getEsquina() {
                return parada.esquina();
            }

            @Override
            public Double getLatitud() {
                return parada.ubicacion().latitud();
            }

            @Override
            public Double getLongitud() {
                return parada.ubicacion().longitud();
            }

            @Override
            public double getDistancia() {
                return metros;
            }
        };
    }

    @Test
    void busca_con_un_patron_por_palabra_normalizada() {
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of(entidadDe(PARADA)));

        final ResultadoBusqueda resultado = service().buscar("Coruña y Purificación");

        assertThat(resultado.paradas()).hasSize(1);
        assertThat(resultado.paradas().getFirst().codigo()).isEqualTo(546L);
        // Están las dos palabras: es una coincidencia exacta, no una aproximación.
        assertThat(resultado.exactas()).isEqualTo(1);
        assertThat(resultado.soloAproximadas()).isFalse();

        final ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(repository).buscarPorPatrones(captor.capture(), any(Limit.class));
        // Tildes fuera, una palabra por patrón y sin el conector "y": matchean todas, en
        // cualquier orden.
        assertThat(captor.getValue()).containsExactly("%CORUNA%", "%PURIFICACION%");
    }

    @Test
    void el_conector_del_cruce_no_se_exige_como_termino() {
        // "18 DE JULIO Y EJIDO": ninguna de las dos calles contiene una Y, así que exigir "%Y%"
        // dejaba la búsqueda más natural del usuario sin un solo resultado.
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());

        service().buscar("18 de julio y ejido");

        // Sin coincidencias exactas también se consulta por cada calle, para estimar el cruce;
        // acá interesa la primera consulta, la de la frase entera.
        final ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(repository, atLeastOnce()).buscarPorPatrones(captor.capture(), any(Limit.class));
        assertThat(captor.getAllValues().getFirst()).containsExactly("%18%", "%DE%", "%JULIO%", "%EJIDO%");
    }

    @Test
    void cuando_el_cruce_no_tiene_parada_ofrece_las_mas_cercanas() {
        // El caso real: "gabriel pereira y chucarro" no existe como cruce, pero las paradas de
        // cada calle están a media cuadra, así que se puede ubicar dónde queda y decir cuál es
        // la parada más cerca.
        final Parada dePereira = new Parada(3977L, "GABRIEL A PEREIRA", "PEDRO F BERRO",
                new Coordenada(-34.9110, -56.1520));
        final Parada deChucarro = new Parada(4553L, "MIGUEL BARREIRO", "ALEJANDRO CHUCARRO",
                new Coordenada(-34.9118, -56.1528));

        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());
        given(repository.buscarPorPatrones(
                argThat(p -> p != null && List.of(p).contains("%PEREIRA%")), any()))
                .willReturn(List.of(entidadDe(dePereira, "3977 GABRIEL A PEREIRA Y PEDRO F BERRO")));
        given(repository.buscarPorPatrones(
                argThat(p -> p != null && List.of(p).contains("%CHUCARRO%")), any()))
                .willReturn(List.of(entidadDe(deChucarro, "4553 MIGUEL BARREIRO Y ALEJANDRO CHUCARRO")));
        given(repository.cercanasA(anyDouble(), anyDouble(), any())).willReturn(List.of());

        service().buscar("gabriel pereira y chucarro");

        // Se buscó alrededor del punto medio de las dos calles, no de cualquier lado.
        final ArgumentCaptor<Double> lat = ArgumentCaptor.forClass(Double.class);
        final ArgumentCaptor<Double> lon = ArgumentCaptor.forClass(Double.class);
        verify(repository).cercanasA(lat.capture(), lon.capture(), any(Limit.class));
        assertThat(lat.getValue()).isCloseTo(-34.9114, within(0.001));
        assertThat(lon.getValue()).isCloseTo(-56.1524, within(0.001));
    }

    @Test
    void no_inventa_un_cruce_entre_calles_que_no_se_tocan() {
        // Dos paradas a kilómetros: esas calles no se cruzan ahí, y un punto medio sería un
        // lugar inventado. Mejor no responder que responder cualquier cosa.
        final Parada lejana = new Parada(1L, "CORUÑA", "PURIFICACION", new Coordenada(-34.87, -56.14));
        final Parada masLejana = new Parada(2L, "AV ITALIA", "COMERCIO", new Coordenada(-34.90, -56.20));

        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());
        given(repository.buscarPorPatrones(
                argThat(p -> p != null && List.of(p).contains("%CORUNA%")), any()))
                .willReturn(List.of(entidadDe(lejana, "1 CORUNA Y PURIFICACION")));
        given(repository.buscarPorPatrones(
                argThat(p -> p != null && List.of(p).contains("%COMERCIO%")), any()))
                .willReturn(List.of(entidadDe(masLejana, "2 AV ITALIA Y COMERCIO")));

        final ResultadoBusqueda resultado = service().buscar("coruña y comercio");

        assertThat(resultado.hayCercanasAlPunto()).isFalse();
        verify(repository, never()).cercanasA(anyDouble(), anyDouble(), any());
    }

    @Test
    void una_direccion_con_numero_se_geocodifica_y_ofrece_las_paradas_mas_cercanas() {
        // El caso que motivó todo esto: "gabriel pereira 2470" no es un cruce y ninguna parada se
        // llama así, pero la dirección existe y se puede decir cuál es la parada más cerca.
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());
        given(geocoder.ubicar("gabriel pereira 2470")).willReturn(Optional.of(
                new DireccionUbicada("GABRIEL A. PEREIRA 2470", new Coordenada(-34.9100, -56.1470))));
        given(repository.cercanasA(anyDouble(), anyDouble(), any())).willReturn(List.of());

        final ResultadoBusqueda resultado = service().buscar("gabriel pereira 2470");


        // Se buscaron paradas alrededor de la puerta, no de cualquier lado de la calle.
        final ArgumentCaptor<Double> lat = ArgumentCaptor.forClass(Double.class);
        final ArgumentCaptor<Double> lon = ArgumentCaptor.forClass(Double.class);
        verify(repository).cercanasA(lat.capture(), lon.capture(), any(Limit.class));
        assertThat(lat.getValue()).isCloseTo(-34.9100, within(0.0001));
        assertThat(lon.getValue()).isCloseTo(-56.1470, within(0.0001));

        // El origen del punto no es un detalle: quien lo muestre tiene que poder decir que la
        // dirección es del padrón y no una estimación nuestra.
        assertThat(resultado.punto().origen()).isEqualTo(PuntoDeReferencia.Origen.DIRECCION_OFICIAL);
        assertThat(resultado.punto().descripcion()).isEqualTo("GABRIEL A. PEREIRA 2470");
    }

    @Test
    void con_una_direccion_ubicada_no_se_devuelven_las_coincidencias_de_texto() {
        // Buscar "gabriel pereira 2470" traía las paradas de toda Gabriel Pereira, que se llaman
        // parecido pero pueden estar a treinta cuadras de la puerta. Sabiendo dónde queda el
        // 2470, esa lista solo compite con la respuesta buena.
        final Parada lejana = new Parada(4444L, "GABRIEL A PEREIRA", "AV ITALIA",
                new Coordenada(-34.8900, -56.1200));
        final Parada cerca = new Parada(3977L, "GABRIEL A PEREIRA", "PEDRO F BERRO",
                new Coordenada(-34.9101, -56.1471));
        given(repository.buscarPorPatrones(any(), any()))
                .willReturn(List.of(entidadDe(lejana, "4444 GABRIEL A PEREIRA Y AV ITALIA")));
        given(geocoder.ubicar(any())).willReturn(Optional.of(
                new DireccionUbicada("GABRIEL A. PEREIRA 2470", new Coordenada(-34.9100, -56.1470))));
        given(repository.cercanasA(anyDouble(), anyDouble(), any()))
                .willReturn(List.of(filaCercana(cerca, 27)));

        final ResultadoBusqueda resultado = service().buscar("gabriel pereira 2470");

        assertThat(resultado.paradas()).isEmpty();
        assertThat(resultado.cercanasAlPunto()).hasSize(1);
        assertThat(resultado.cercanasAlPunto().getFirst().distanciaMetros()).isEqualTo(27);
        // Vaciar "paradas" no puede leerse como "no encontré nada": hay una parada a 27 m.
        assertThat(resultado.sinResultados()).isFalse();
        assertThat(resultado.soloAproximadas()).isFalse();
    }

    @Test
    void con_un_cruce_estimado_se_mantienen_las_coincidencias_de_texto() {
        // Acá el punto es una heurística que puede errarle, y si le erró, las coincidencias son
        // lo único que le queda al usuario para darse cuenta.
        final Parada dePereira = new Parada(3977L, "GABRIEL A PEREIRA", "PEDRO F BERRO",
                new Coordenada(-34.9110, -56.1520));
        final Parada deChucarro = new Parada(4553L, "MIGUEL BARREIRO", "ALEJANDRO CHUCARRO",
                new Coordenada(-34.9118, -56.1528));

        given(repository.buscarPorPatrones(any(), any()))
                .willReturn(List.of(entidadDe(dePereira, "3977 GABRIEL A PEREIRA Y PEDRO F BERRO")));
        given(repository.buscarPorPatrones(
                argThat(p -> p != null && List.of(p).contains("%PEREIRA%")), any()))
                .willReturn(List.of(entidadDe(dePereira, "3977 GABRIEL A PEREIRA Y PEDRO F BERRO")));
        given(repository.buscarPorPatrones(
                argThat(p -> p != null && List.of(p).contains("%CHUCARRO%")), any()))
                .willReturn(List.of(entidadDe(deChucarro, "4553 MIGUEL BARREIRO Y ALEJANDRO CHUCARRO")));
        given(repository.cercanasA(anyDouble(), anyDouble(), any())).willReturn(List.of());

        final ResultadoBusqueda resultado = service().buscar("gabriel pereira y chucarro");

        assertThat(resultado.punto().origen()).isEqualTo(PuntoDeReferencia.Origen.CRUCE_ESTIMADO);
        assertThat(resultado.paradas()).isNotEmpty();
    }

    @Test
    void un_lugar_conocido_se_ubica_y_ofrece_las_paradas_de_alrededor() {
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());
        given(geocoder.buscarLugares("estadio centenario")).willReturn(List.of(
                new LugarUbicado("ESTADIO CENTENARIO", new Coordenada(-34.8938, -56.1516))));
        given(repository.cercanasA(anyDouble(), anyDouble(), any())).willReturn(List.of());

        final ResultadoBusqueda resultado = service().buscar("estadio centenario");

        assertThat(resultado.punto().origen()).isEqualTo(PuntoDeReferencia.Origen.LUGAR_CONOCIDO);
        assertThat(resultado.punto().descripcion()).isEqualTo("ESTADIO CENTENARIO");
    }

    @Test
    void un_lugar_que_no_contiene_lo_buscado_se_descarta() {
        // El guardarraíl. Pedirle "18 de julio" al servicio de lugares contesta "BROU 19 DE JUNIO"
        // (en Minas 1434), sin ninguna marca de que está estirando: se le parece "19 DE JUNIO" a
        // "18 DE JULIO". Como el nombre no dice "JULIO", no es lo que se buscó y no se usa.
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());
        given(geocoder.buscarLugares(any())).willReturn(List.of(
                new LugarUbicado("BROU 19 DE JUNIO", new Coordenada(-34.8600, -56.1900))));

        final ResultadoBusqueda resultado = service().buscar("18 de julio");

        assertThat(resultado.punto()).isNull();
        verify(repository, never()).cercanasA(anyDouble(), anyDouble(), any());
    }

    @Test
    void entre_varios_lugares_se_elige_el_primero_que_contiene_lo_buscado() {
        // Vienen ordenados de mejor a peor, pero el mejor puede no ser lo que se pidió.
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());
        given(geocoder.buscarLugares(any())).willReturn(List.of(
                new LugarUbicado("ESTADIO CHARRUA", new Coordenada(-34.8779, -56.0886)),
                new LugarUbicado("ESTADIO CENTENARIO", new Coordenada(-34.8938, -56.1516))));
        given(repository.cercanasA(anyDouble(), anyDouble(), any())).willReturn(List.of());

        final ResultadoBusqueda resultado = service().buscar("estadio centenario");

        assertThat(resultado.punto().descripcion()).isEqualTo("ESTADIO CENTENARIO");
    }

    @Test
    void una_direccion_con_numero_no_se_busca_como_lugar() {
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());
        given(geocoder.ubicar(any())).willReturn(Optional.empty());

        service().buscar("gabriel pereira 2470");

        verify(geocoder, never()).buscarLugares(any());
    }

    @Test
    void un_cruce_que_no_se_pudo_estimar_no_se_busca_como_lugar() {
        // Una esquina es una esquina: si no se pudo estimar, preguntar por un punto de interés
        // que se llame parecido solo puede traer algo peor.
        final Parada lejana = new Parada(1L, "CORUÑA", "PURIFICACION", new Coordenada(-34.87, -56.14));
        final Parada masLejana = new Parada(2L, "AV ITALIA", "COMERCIO", new Coordenada(-34.90, -56.20));
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());
        given(repository.buscarPorPatrones(
                argThat(p -> p != null && List.of(p).contains("%CORUNA%")), any()))
                .willReturn(List.of(entidadDe(lejana, "1 CORUNA Y PURIFICACION")));
        given(repository.buscarPorPatrones(
                argThat(p -> p != null && List.of(p).contains("%COMERCIO%")), any()))
                .willReturn(List.of(entidadDe(masLejana, "2 AV ITALIA Y COMERCIO")));

        final ResultadoBusqueda resultado = service().buscar("coruña y comercio");

        assertThat(resultado.punto()).isNull();
        verify(geocoder, never()).buscarLugares(any());
    }

    @Test
    void si_el_servicio_de_lugares_se_cae_la_busqueda_por_texto_sigue_respondiendo() {
        final Parada alguna = new Parada(3977L, "GABRIEL A PEREIRA", "PEDRO F BERRO",
                new Coordenada(-34.9110, -56.1520));
        given(repository.buscarPorPatrones(any(), any()))
                .willReturn(List.of(entidadDe(alguna, "3977 GABRIEL A PEREIRA Y PEDRO F BERRO")));
        given(geocoder.buscarLugares(any()))
                .willThrow(new GeocoderException("se cayó", new RuntimeException()));

        final ResultadoBusqueda resultado = service().buscar("estadio centenario");

        assertThat(resultado.paradas()).hasSize(1);
        assertThat(resultado.punto()).isNull();
    }

    @Test
    void un_cruce_no_llama_al_geocoder() {
        // El geocoder ignora la segunda calle, así que para esquinas no sirve; y son la mayoría
        // de las consultas, que no tienen por qué pagar una llamada externa.
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());

        service().buscar("18 de julio y ejido");

        verify(geocoder, never()).ubicar(any());
    }

    @Test
    void si_el_geocoder_se_cae_la_busqueda_por_texto_sigue_respondiendo() {
        // Es un servicio de otro organismo: que se caiga no puede romper una búsqueda que antes
        // de que existiera respondía igual.
        final Parada dePereira = new Parada(3977L, "GABRIEL A PEREIRA", "PEDRO F BERRO",
                new Coordenada(-34.9110, -56.1520));
        given(repository.buscarPorPatrones(any(), any()))
                .willReturn(List.of(entidadDe(dePereira, "3977 GABRIEL A PEREIRA Y PEDRO F BERRO")));
        given(geocoder.ubicar(any())).willThrow(new GeocoderException("se cayó", new RuntimeException()));

        final ResultadoBusqueda resultado = service().buscar("gabriel pereira 2470");

        assertThat(resultado.paradas()).hasSize(1);
        assertThat(resultado.soloAproximadas()).isTrue();
        assertThat(resultado.punto()).isNull();
        assertThat(resultado.hayCercanasAlPunto()).isFalse();
    }

    @Test
    void una_direccion_que_el_geocoder_no_ubica_no_inventa_un_punto() {
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());
        given(geocoder.ubicar(any())).willReturn(Optional.empty());

        final ResultadoBusqueda resultado = service().buscar("calle que no existe 123");

        assertThat(resultado.punto()).isNull();
        verify(repository, never()).cercanasA(anyDouble(), anyDouble(), any());
    }

    @Test
    void una_consulta_de_puros_conectores_no_devuelve_todas_las_paradas() {
        assertThat(service().buscar("y esquina con").sinResultados()).isTrue();

        verify(repository, never()).buscarPorPatrones(any(), any());
    }

    @Test
    void refresca_el_cache_antes_de_buscar() {
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());

        service().buscar("ejido");

        verify(cacheService).asegurarCacheFresco();
    }

    @Test
    void una_consulta_sin_palabras_no_toca_la_base() {
        assertThat(service().buscar("   ").sinResultados()).isTrue();

        verify(cacheService, never()).asegurarCacheFresco();
        verify(repository, never()).buscarPorPatrones(any(), any());
    }

    @Test
    void busca_por_codigo_de_parada() {
        given(repository.findById(546L)).willReturn(Optional.of(entidadDe(PARADA)));

        assertThat(service().porCodigo(546L)).hasValueSatisfying(
                parada -> assertThat(parada.descripcion()).isEqualTo("CORUÑA y PURIFICACION"));
    }

    @Test
    void un_codigo_que_no_existe_da_vacio() {
        given(repository.findById(1L)).willReturn(Optional.empty());

        assertThat(service().porCodigo(1L)).isEmpty();
    }
}
