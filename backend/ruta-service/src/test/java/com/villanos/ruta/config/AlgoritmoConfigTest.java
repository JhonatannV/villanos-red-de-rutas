package com.villanos.ruta.config;

import com.villanos.ruta.calculo.ModoAlgoritmo;
import com.villanos.ruta.calculo.SelectorAlgoritmo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlgoritmoConfigTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(AlgoritmoConfig.class);

    @Test
    void conModoJavaElSelectorUsaJava() {
        contexto.withPropertyValues("app.algorithm.mode=java").run(ctx -> {
            SelectorAlgoritmo s = ctx.getBean(SelectorAlgoritmo.class);
            assertThat(s.modoPedido()).isEqualTo(ModoAlgoritmo.JAVA);
            assertThat(s.modoEfectivo()).isEqualTo(ModoAlgoritmo.JAVA);
        });
    }

    @Test
    void sinPropiedadPideNative() {
        contexto.run(ctx -> assertThat(ctx.getBean(SelectorAlgoritmo.class).modoPedido())
                .isEqualTo(ModoAlgoritmo.NATIVE));
    }

    @Test
    void conModoNativePideNativeYSiNoCargaUsaJava() {
        contexto.withPropertyValues("app.algorithm.mode=native").run(ctx -> {
            SelectorAlgoritmo s = ctx.getBean(SelectorAlgoritmo.class);
            assertThat(s.modoPedido()).isEqualTo(ModoAlgoritmo.NATIVE);
            assertThat(s.modoEfectivo()).isIn(ModoAlgoritmo.NATIVE, ModoAlgoritmo.JAVA);
            assertThat(s.motivo()).isNotBlank();
        });
    }

    @Test
    void unValorInvalidoDetieneElArranque() {
        contexto.withPropertyValues("app.algorithm.mode=rust").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("app.algorithm.mode");
        });
    }

    @Test
    void elInfoPublicaElModoPedidoYElActivo() {
        contexto.withPropertyValues("app.algorithm.mode=java").run(ctx -> {
            Info.Builder builder = new Info.Builder();
            ctx.getBean(InfoContributor.class).contribute(builder);
            @SuppressWarnings("unchecked")
            Map<String, Object> algoritmo = (Map<String, Object>) builder.build().get("algoritmo");
            assertThat(algoritmo).containsEntry("modoPedido", "java").containsEntry("modoEfectivo", "java");
        });
    }
}
