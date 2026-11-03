package br.com.kallopolli.parceiro.painel;

import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas de leitura do dashboard (docs/contrato-api.md). Só GET: não há escrita além de login e
 * logout. O {@link Acesso} vem do {@link FiltroPainel}, lido de acessos_dashboard; nada que o
 * navegador envie (cabeçalho, parâmetro ou corpo) muda papel ou loja.
 *
 * <p>Fora do MVP: PATCH /api/relatos/{protocolo} e POST /api/relatorios/gerar.
 */
@RestController
@RequestMapping("/api")
class PainelController {

    private static final Set<String> STATUS_ALERTA = Set.of("ABERTO", "ATENDIDO", "SEM_RETORNO_24H");
    private static final Set<String> DESTINOS = Set.of("GERENTE", "RH");
    private static final Set<String> STATUS_RELATO = Set.of("NOVO", "EM_ANALISE", "CONCLUIDO");

    private final ServicoPainel servico;

    PainelController(ServicoPainel servico) {
        this.servico = servico;
    }

    @GetMapping("/perfil")
    ResponseEntity<Object> perfil(@RequestAttribute(name = FiltroPainel.ATRIBUTO_ACESSO, required = false) Acesso acesso) {
        if (acesso == null) {
            return semSessao();
        }
        return ResponseEntity.ok(ServicoPainel.perfil(acesso));
    }

    @GetMapping("/kpis")
    ResponseEntity<Object> kpis(@RequestAttribute(name = FiltroPainel.ATRIBUTO_ACESSO, required = false) Acesso acesso) {
        if (acesso == null) {
            return semSessao();
        }
        return ResponseEntity.ok(servico.kpis(acesso));
    }

    @GetMapping("/kpis/serie-mensal")
    ResponseEntity<Object> serieMensal(
            @RequestAttribute(name = FiltroPainel.ATRIBUTO_ACESSO, required = false) Acesso acesso) {
        if (acesso == null) {
            return semSessao();
        }
        return ResponseEntity.ok(servico.serieMensal(acesso));
    }

    @GetMapping("/colaboradores")
    ResponseEntity<Object> colaboradores(
            @RequestAttribute(name = FiltroPainel.ATRIBUTO_ACESSO, required = false) Acesso acesso) {
        if (acesso == null) {
            return semSessao();
        }
        return ResponseEntity.ok(servico.colaboradores(acesso));
    }

    @GetMapping("/colaboradores/{id}/linha-do-tempo")
    ResponseEntity<Object> linhaDoTempo(
            @RequestAttribute(name = FiltroPainel.ATRIBUTO_ACESSO, required = false) Acesso acesso,
            @PathVariable("id") String id) {
        if (acesso == null) {
            return semSessao();
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return naoEncontrado();
        }
        return servico.linhaDoTempo(acesso, uuid)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(PainelController::naoEncontrado);
    }

    @GetMapping("/alertas")
    ResponseEntity<Object> alertas(
            @RequestAttribute(name = FiltroPainel.ATRIBUTO_ACESSO, required = false) Acesso acesso,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "destino", required = false) String destino) {
        if (acesso == null) {
            return semSessao();
        }
        String s = vazioComoNulo(status);
        String d = vazioComoNulo(destino);
        if ((s != null && !STATUS_ALERTA.contains(s)) || (d != null && !DESTINOS.contains(d))) {
            return RespostasPainel.erro(HttpStatus.BAD_REQUEST, "FILTRO_INVALIDO",
                    "Use status ABERTO, ATENDIDO ou SEM_RETORNO_24H e destino GERENTE ou RH.");
        }
        return ResponseEntity.ok(servico.alertas(acesso, s, d));
    }

    @GetMapping("/relatos")
    ResponseEntity<Object> relatos(
            @RequestAttribute(name = FiltroPainel.ATRIBUTO_ACESSO, required = false) Acesso acesso,
            @RequestParam(name = "status", required = false) String status) {
        if (acesso == null) {
            return semSessao();
        }
        if (!acesso.rh()) {
            return RespostasPainel.erro(HttpStatus.FORBIDDEN, "SO_RH", RespostasPainel.SO_RH);
        }
        String s = vazioComoNulo(status);
        if (s != null && !STATUS_RELATO.contains(s)) {
            return RespostasPainel.erro(HttpStatus.BAD_REQUEST, "FILTRO_INVALIDO",
                    "Use status NOVO, EM_ANALISE ou CONCLUIDO.");
        }
        return ResponseEntity.ok(servico.relatos(acesso, s));
    }

    @GetMapping("/bem-estar")
    ResponseEntity<Object> bemEstar(@RequestAttribute(name = FiltroPainel.ATRIBUTO_ACESSO, required = false) Acesso acesso) {
        if (acesso == null) {
            return semSessao();
        }
        return ResponseEntity.ok(servico.bemEstar(acesso));
    }

    /** Defesa extra: o filtro já respondeu 401 antes de chegar aqui. */
    private static ResponseEntity<Object> semSessao() {
        return RespostasPainel.erro(HttpStatus.UNAUTHORIZED, "SESSAO_INVALIDA", RespostasPainel.SESSAO_EXPIRADA);
    }

    private static ResponseEntity<Object> naoEncontrado() {
        return RespostasPainel.erro(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", RespostasPainel.NAO_ENCONTRADO);
    }

    private static String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
