package br.com.kallopolli.parceiro.simulador;

import br.com.kallopolli.parceiro.alerta.Responsavel;
import br.com.kallopolli.parceiro.alerta.ResponsavelRepository;
import br.com.kallopolli.parceiro.jornada.ColaboradorRepository;
import br.com.kallopolli.parceiro.whatsapp.MensagemRecebida;
import br.com.kallopolli.parceiro.whatsapp.ProcessadorMensagens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulador de chat local (parceiro.whatsapp.canal=simulador; fora disso não existe e tudo dá
 * 404). A mensagem digitada entra pelo mesmo caminho do webhook ({@link MensagemRecebida} e
 * {@link ProcessadorMensagens}); a resposta do robô sai pelo {@link SimuladorCanal}. Só atende a
 * própria máquina ({@link FiltroSimuladorLocal}).
 */
@RestController
@ConditionalOnProperty(name = "parceiro.whatsapp.canal", havingValue = "simulador")
class SimuladorController {

    private static final int MAX_TEXTO = 4096;
    private static final Map<String, MediaType> ARQUIVOS = Map.of(
            "app.js", MediaType.valueOf("text/javascript;charset=UTF-8"),
            "estilo.css", MediaType.valueOf("text/css;charset=UTF-8"));

    record Contato(String telefone, String nome, List<String> papeis) {}

    record Mensagens(long ultima, List<SimuladorCanal.Saida> mensagens) {}

    private final ColaboradorRepository colaboradores;
    private final ResponsavelRepository responsaveis;
    private final ProcessadorMensagens processador;
    private final SimuladorCanal canal;
    private final ObjectMapper json;

    SimuladorController(ColaboradorRepository colaboradores, ResponsavelRepository responsaveis,
                        ProcessadorMensagens processador, SimuladorCanal canal, ObjectMapper json) {
        this.colaboradores = colaboradores;
        this.responsaveis = responsaveis;
        this.processador = processador;
        this.canal = canal;
        this.json = json;
    }

    @GetMapping({"/simulador", "/simulador/"})
    ResponseEntity<byte[]> pagina() throws IOException {
        return arquivo("index.html", MediaType.valueOf("text/html;charset=UTF-8"));
    }

    @GetMapping("/simulador/{nome:app\\.js|estilo\\.css}")
    ResponseEntity<byte[]> recurso(@PathVariable("nome") String nome) throws IOException {
        return arquivo(nome, ARQUIVOS.get(nome));
    }

    /** Telefones cadastrados (colaboradores e responsáveis ativos), um por número, com os papéis. */
    @GetMapping("/simulador/api/contatos")
    List<Contato> contatos() {
        Map<String, Contato> porTelefone = new LinkedHashMap<>();
        colaboradores.colaboradoresAtivos().forEach(c -> juntar(porTelefone, c.numeroWhatsApp(), c.nome(), "Colaborador"));
        for (Responsavel r : responsaveis.responsaveisAtivos()) {
            String papel = Responsavel.GERENTE.equals(r.papel()) ? "Gerente · " + r.loja() : "RH";
            juntar(porTelefone, r.numeroWhatsApp(), r.nome(), papel);
        }
        return List.copyOf(porTelefone.values());
    }

    @PostMapping(path = "/simulador/api/mensagens", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Object> enviar(@RequestBody(required = false) byte[] corpo) {
        String telefone;
        String texto;
        try {
            JsonNode no = json.readTree(corpo == null ? new byte[0] : corpo);
            telefone = digitos(no.path("telefone").asText(""));
            texto = no.path("texto").isTextual() ? no.path("texto").asText() : null;
        } catch (IOException e) {
            return erro("Corpo fora do formato: use {\"telefone\":\"...\",\"texto\":\"...\"}.");
        }
        if (!telefone.matches("\\d{8,15}")) {
            return erro("Informe o telefone com DDI e DDD, só números (ex.: 5511999990000).");
        }
        if (texto == null || texto.isEmpty() || texto.length() > MAX_TEXTO) {
            return erro("Escreva uma mensagem de até " + MAX_TEXTO + " caracteres.");
        }
        String id = "sim-" + UUID.randomUUID();
        processador.processar(new MensagemRecebida(id, telefone, MensagemRecebida.TEXTO, texto));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("id", id));
    }

    /** Mensagens do robô para o telefone, depois da seq informada. A página chama a cada 1,5 s. */
    @GetMapping("/simulador/api/mensagens")
    ResponseEntity<Object> mensagens(@RequestParam(name = "telefone") String telefone,
                                     @RequestParam(name = "depois", defaultValue = "0") long depois) {
        String numero = digitos(telefone);
        if (!numero.matches("\\d{8,15}")) {
            return erro("Telefone inválido.");
        }
        List<SimuladorCanal.Saida> lista = canal.depois(numero, depois);
        long ultima = lista.isEmpty() ? depois : lista.get(lista.size() - 1).seq();
        return ResponseEntity.ok(new Mensagens(ultima, lista));
    }

    private static void juntar(Map<String, Contato> mapa, String telefone, String nome, String papel) {
        Contato atual = mapa.get(telefone);
        List<String> papeis = new ArrayList<>(atual == null ? List.of() : atual.papeis());
        papeis.add(papel);
        mapa.put(telefone, new Contato(telefone, atual == null ? nome : atual.nome(), papeis));
    }

    private static String digitos(String telefone) {
        return telefone == null ? "" : telefone.replaceAll("\\D", "");
    }

    private static ResponseEntity<Object> erro(String mensagem) {
        return ResponseEntity.badRequest().body(Map.of("erro", Map.of("codigo", "PEDIDO_INVALIDO", "mensagem", mensagem)));
    }

    private static ResponseEntity<byte[]> arquivo(String nome, MediaType tipo) throws IOException {
        var recurso = new ClassPathResource("simulador/" + nome);
        if (!recurso.exists()) {
            return ResponseEntity.notFound().build();
        }
        try (InputStream in = recurso.getInputStream()) {
            return ResponseEntity.ok().contentType(tipo).body(in.readAllBytes());
        }
    }
}
