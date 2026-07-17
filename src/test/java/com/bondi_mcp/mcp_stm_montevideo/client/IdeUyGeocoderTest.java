package com.bondi_mcp.mcp_stm_montevideo.client;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bondi_mcp.mcp_stm_montevideo.config.GeocoderProperties;
import com.bondi_mcp.mcp_stm_montevideo.domain.DireccionUbicada;
import com.bondi_mcp.mcp_stm_montevideo.domain.LugarUbicado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Los JSON de acá son respuestas reales del servicio, recortadas a los campos que leemos.
 */
class IdeUyGeocoderTest {

    /** "Gabriel Pereira 2470": la puerta existe y el servicio la ubica. */
    private static final String EXACTA = """
            [{"direccion":{"departamento":{"idDepartamento":1,"nombre_normalizado":"MONTEVIDEO"},
              "calle":{"idCalle":10870,"nombre_normalizado":"GABRIEL A. PEREIRA"},
              "numero":{"nro_puerta":2470}},
              "puntoX":-56.14950237118904,"puntoY":-34.90780412337651,
              "idPunto":512340,"srid":4326,"idTipoClasificacion":1,"error":""}]""";

    /** "Gabriel Pereira 99999": no existe, y el servicio contesta otra puerta cualquiera. */
    private static final String APROXIMADA = """
            [{"direccion":{"departamento":{"idDepartamento":1,"nombre_normalizado":"MONTEVIDEO"},
              "calle":{"idCalle":10870,"nombre_normalizado":"GABRIEL A. PEREIRA"},
              "numero":{"nro_puerta":3346}},
              "puntoX":-56.14585381023099,"puntoY":-34.911568049115964,
              "srid":4326,"idTipoClasificacion":1,
              "error":"PUNTO NO ENCONTRADO.\\nAPROXIMADO POR CALLE: \\n"}]""";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer servicio = MockRestServiceServer.bindTo(builder).build();
    private final Geocoder geocoder =
            new IdeUyGeocoder(new GeocoderProperties("https://direcciones.ide.uy/api", "MONTEVIDEO"),
                    builder);

    @Test
    void ubica_una_direccion_con_numero_de_puerta() {
        servicio.expect(requestTo(containsString("/v0/geocode/BusquedaDireccion")))
                .andRespond(withSuccess(EXACTA, MediaType.APPLICATION_JSON));

        final Optional<DireccionUbicada> ubicada = geocoder.ubicar("Gabriel Pereira 2470");

        assertThat(ubicada).isPresent();
        // El padrón le pone la inicial: es el nombre oficial, no el que escribió el usuario.
        assertThat(ubicada.get().direccionOficial()).isEqualTo("GABRIEL A. PEREIRA 2470");
        // puntoY es la latitud y puntoX la longitud: cruzarlas dejaría el punto en China.
        assertThat(ubicada.get().coordenada().latitud()).isCloseTo(-34.9078041, within(0.000001));
        assertThat(ubicada.get().coordenada().longitud()).isCloseTo(-56.1495023, within(0.000001));
        servicio.verify();
    }

    @Test
    void manda_la_direccion_entera_y_el_departamento_configurado() {
        // El parámetro se llama "calle" pero lleva el número: así lo espera el servicio. Y sin
        // departamento, "Gabriel Pereira 2470" se resuelve en Pando.
        servicio.expect(requestTo(containsString("calle=Gabriel%20Pereira%202470")))
                .andExpect(requestTo(containsString("departamento=MONTEVIDEO")))
                .andRespond(withSuccess(EXACTA, MediaType.APPLICATION_JSON));

        geocoder.ubicar("Gabriel Pereira 2470");

        servicio.verify();
    }

    @Test
    void no_ubica_nada_cuando_el_servicio_aproxima_por_calle() {
        // El caso peligroso: contesta 200, con una dirección de pinta impecable y un punto real,
        // pero es otra puerta. Si no miráramos "error", mandaríamos al usuario a otra cuadra.
        servicio.expect(requestTo(containsString("BusquedaDireccion")))
                .andRespond(withSuccess(APROXIMADA, MediaType.APPLICATION_JSON));

        assertThat(geocoder.ubicar("Gabriel Pereira 99999")).isEmpty();
    }

    @Test
    void se_queda_con_el_primer_candidato_exacto() {
        // "18 de julio 1360" matchea exacto en dos calles distintas; vienen ordenados y la buena
        // es la primera.
        final String dosExactas = """
                [{"direccion":{"calle":{"nombre_normalizado":"AVENIDA 18 DE JULIO"},
                  "numero":{"nro_puerta":1360}},
                  "puntoX":-56.186173009905936,"puntoY":-34.90569632999515,"srid":4326,"error":""},
                 {"direccion":{"calle":{"nombre_normalizado":"14 DE JULIO"},
                  "numero":{"nro_puerta":1360}},
                  "puntoX":-56.1484373007342,"puntoY":-34.90417432365223,"srid":4326,"error":""}]""";
        servicio.expect(requestTo(containsString("BusquedaDireccion")))
                .andRespond(withSuccess(dosExactas, MediaType.APPLICATION_JSON));

        assertThat(geocoder.ubicar("18 de julio 1360"))
                .hasValueSatisfying(d -> assertThat(d.direccionOficial())
                        .isEqualTo("AVENIDA 18 DE JULIO 1360"));
    }

    @Test
    void una_calle_sin_numero_no_se_ubica() {
        // Sin puerta el servicio devuelve un punto cualquiera de la calle, con error vacío: el
        // único indicio de que no es una dirección es que no viene "numero".
        final String soloCalle = """
                [{"direccion":{"calle":{"nombre_normalizado":"GABRIEL A. PEREIRA"}},
                  "puntoX":-56.15033066994043,"puntoY":-34.9067890274692,
                  "srid":4326,"idTipoClasificacion":27,"error":""}]""";
        servicio.expect(requestTo(containsString("BusquedaDireccion")))
                .andRespond(withSuccess(soloCalle, MediaType.APPLICATION_JSON));

        assertThat(geocoder.ubicar("Gabriel Pereira")).isEmpty();
    }

    @Test
    void una_direccion_que_no_existe_no_se_ubica() {
        servicio.expect(requestTo(containsString("BusquedaDireccion")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(geocoder.ubicar("Calle Que No Existe 123")).isEmpty();
    }

    @Test
    void busca_lugares_conocidos() {
        // Respuesta real de direcPuntoNotable. Ojo: este endpoint es de la v1 y trae lat/lng
        // directo, no puntoY/puntoX como el de direcciones.
        final String poi = """
                [{"type":"POI","id":"9670",
                  "address":"ESTADIO CENTENARIO - AVENIDA DOCTOR AMERICO RICALDONI 0, MONTEVIDEO",
                  "inmueble":"ESTADIO CENTENARIO","nomVia":"AVENIDA DOCTOR AMERICO RICALDONI",
                  "lat":-34.893886732752726,"lng":-56.15166102213667,"state":1,"stateMsg":""}]""";
        servicio.expect(requestTo(containsString("/v1/geocode/direcPuntoNotable")))
                .andExpect(requestTo(containsString("nombre=Estadio%20Centenario")))
                .andExpect(requestTo(containsString("departamento=MONTEVIDEO")))
                .andRespond(withSuccess(poi, MediaType.APPLICATION_JSON));

        final List<LugarUbicado> lugares = geocoder.buscarLugares("Estadio Centenario");

        assertThat(lugares).hasSize(1);
        // Se usa "inmueble" (el nombre solo) y no "address", que arrastra la calle.
        assertThat(lugares.getFirst().nombre()).isEqualTo("ESTADIO CENTENARIO");
        assertThat(lugares.getFirst().coordenada().latitud()).isCloseTo(-34.8938867, within(0.000001));
        assertThat(lugares.getFirst().coordenada().longitud()).isCloseTo(-56.1516610, within(0.000001));
        servicio.verify();
    }

    @Test
    void los_lugares_flojos_se_devuelven_igual_porque_el_servicio_no_los_marca() {
        // "18 de julio" contesta esto, con state 1 y stateMsg vacío: idéntico a un acierto. El
        // adaptador no puede distinguirlo, así que lo pasa; descartarlo es de ParadaService.
        final String brou = """
                [{"type":"POI","inmueble":"BROU 19 DE JUNIO","lat":-34.86,"lng":-56.19,
                  "state":1,"stateMsg":""}]""";
        servicio.expect(requestTo(containsString("direcPuntoNotable")))
                .andRespond(withSuccess(brou, MediaType.APPLICATION_JSON));

        assertThat(geocoder.buscarLugares("18 de julio"))
                .singleElement()
                .satisfies(l -> assertThat(l.nombre()).isEqualTo("BROU 19 DE JUNIO"));
    }

    @Test
    void un_lugar_sin_coordenada_no_se_puede_usar() {
        servicio.expect(requestTo(containsString("direcPuntoNotable")))
                .andRespond(withSuccess("[{\"inmueble\":\"ALGO\",\"lat\":null,\"lng\":null}]",
                        MediaType.APPLICATION_JSON));

        assertThat(geocoder.buscarLugares("algo")).isEmpty();
    }

    @Test
    void una_falla_del_servicio_no_se_confunde_con_una_direccion_inexistente() {
        servicio.expect(requestTo(containsString("BusquedaDireccion"))).andRespond(withServerError());

        assertThatThrownBy(() -> geocoder.ubicar("Gabriel Pereira 2470"))
                .isInstanceOf(GeocoderException.class)
                .hasMessageContaining("Gabriel Pereira 2470");
    }
}
