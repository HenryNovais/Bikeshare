package br.ufal.ic.p2.bikeshare;

/**
 Facade
 */
public class Facade {
    private java.util.Map<String, java.util.Map<String,String>> usuarios = new java.util.LinkedHashMap<>();
    private int proximoId = 1;
    private static final String ARQUIVO = "bikeshare.ser";

    @SuppressWarnings("unchecked")
    public Facade() {
        java.io.File arquivo = new java.io.File(ARQUIVO);
        if (arquivo.exists()) {
            try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(new java.io.FileInputStream(arquivo))) {
                usuarios = (java.util.Map<String, java.util.Map<String,String>>) in.readObject();
                proximoId = in.readInt();
            } catch (java.io.IOException | ClassNotFoundException e) { throw new IllegalStateException("Erro ao carregar dados.", e); }
        }
    }
    private static void obrigatorio(String valor, String erro) {
        if (valor == null || valor.trim().isEmpty()) throw new IllegalArgumentException(erro);
    }
    private String cadastrar(String nome, String email, String senha, String tipo, String documento, String razao, String plano, String mensalidade, boolean juridica) {
        obrigatorio(nome, "Nome nao pode ser nulo.");
        obrigatorio(email, "Email nao pode ser nulo.");
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Email invalido.");
        obrigatorio(senha, "Senha nao pode ser nula.");
        if (!tipo.equals(juridica ? "pessoaJuridica" : "pessoaFisica")) throw new IllegalArgumentException("Tipo invalido.");
        obrigatorio(documento, juridica ? "CNPJ nao pode ser nulo." : "CPF nao pode ser nulo.");
        if (juridica) obrigatorio(razao, "Razao social nao pode ser nula.");
        for (java.util.Map<String,String> u : usuarios.values()) if (u.get("email").equalsIgnoreCase(email)) throw new IllegalArgumentException("Email ja cadastrado.");
        if (!"avulso".equals(plano) && !"assinante".equals(plano)) throw new IllegalArgumentException("Plano invalido.");
        String valor = null;
        if ("assinante".equals(plano)) {
            obrigatorio(mensalidade, "Mensalidade nao pode ser nula.");
            java.math.BigDecimal numero;
            try { numero = new java.math.BigDecimal(mensalidade.replace(',', '.')); } catch (NumberFormatException e) { throw new IllegalArgumentException("Mensalidade deve ser numerica."); }
            if (numero.signum() < 0) throw new IllegalArgumentException("Mensalidade deve ser nao-negativa.");
            valor = numero.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        } else if (mensalidade != null && !mensalidade.trim().isEmpty()) throw new IllegalArgumentException("Plano nao possui mensalidade.");
        java.util.Map<String,String> u = new java.util.HashMap<>();
        u.put("nome", nome); u.put("email", email); u.put("senha", senha); u.put("tipo", tipo); u.put("estado", "ativo");
        u.put("plano", plano); u.put("metodoPagamento", "nenhum"); u.put(juridica ? "cnpj" : "cpf", documento);
        if (juridica) u.put("razaoSocial", razao);
        if (valor != null) u.put("mensalidade", valor);
        String id = "U" + proximoId++; usuarios.put(id, u); return id;
    }


    private static final String AINDA_NAO_IMPLEMENTADO = "Ainda não implementado.";

    // =====================================================================
    // US1 - Cadastro de usuario
    // =====================================================================

    public String criarUsuario(String nome, String email, String senha, String tipo, String cpfOuCnpj) {
        return cadastrar(nome, email, senha, tipo, cpfOuCnpj, null, "avulso", null, false);
    }

    public String criarUsuario(String nome, String email, String senha, String tipo, String cnpj, String razaoSocial) {
        return cadastrar(nome, email, senha, tipo, cnpj, razaoSocial, "avulso", null, true);
    }

    public String criarUsuario(String nome, String email, String senha, String tipo, String cpf,
                                String plano, String mensalidade) {
        return cadastrar(nome, email, senha, tipo, cpf, null, plano, mensalidade, false);
    }

    public String criarUsuario(String nome, String email, String senha, String tipo, String cnpj,
                                String razaoSocial, String plano, String mensalidade) {
        return cadastrar(nome, email, senha, tipo, cnpj, razaoSocial, plano, mensalidade, true);
    }

    // =====================================================================
    // US2 - Consulta de usuario
    // =====================================================================

    public String getAtributoUsuario(String usuario, String atributo) {
        obrigatorio(usuario, "Identificacao do usuario nao pode ser nula.");
        java.util.Map<String,String> u = usuarios.get(usuario);
        if (u == null) throw new IllegalArgumentException("Usuario nao existe.");
        if ("aluguelAtivo".equals(atributo)) return u.getOrDefault(atributo, "nenhum");
        if ("totalPendencias".equals(atributo)) return u.getOrDefault(atributo, "0,00");
        if (("numeroCartao".equals(atributo) || "validadeCartao".equals(atributo)) && !"cartao".equals(u.get("metodoPagamento"))) throw new IllegalArgumentException("Usuario nao usa cartao.");
        if ("chavePix".equals(atributo) && !"pix".equals(u.get("metodoPagamento"))) throw new IllegalArgumentException("Usuario nao usa pix.");
        if ("saldo".equals(atributo) && !"saldoPreCarregado".equals(u.get("metodoPagamento"))) throw new IllegalArgumentException("Usuario nao usa saldo pre-carregado.");
        if (("cnpj".equals(atributo) || "razaoSocial".equals(atributo)) && !"pessoaJuridica".equals(u.get("tipo"))) throw new IllegalArgumentException("Usuario nao eh pessoa juridica.");
        if ("cpf".equals(atributo) && !"pessoaFisica".equals(u.get("tipo"))) throw new IllegalArgumentException("Usuario nao eh pessoa fisica.");
        if ("mensalidade".equals(atributo) && !u.containsKey(atributo)) throw new IllegalArgumentException("Plano nao possui mensalidade.");
        if (!u.containsKey(atributo)) throw new IllegalArgumentException("Atributo nao existe.");
        return u.get(atributo);
    }

    public String getUsuarioPorEmail(String email) {
        obrigatorio(email, "Email nao pode ser nulo.");
        for (java.util.Map.Entry<String, java.util.Map<String,String>> entrada : usuarios.entrySet()) {
            if (entrada.getValue().get("email").equalsIgnoreCase(email)) return entrada.getKey();
        }
        throw new IllegalArgumentException("Usuario nao existe.");
    }

    // =====================================================================
    // US3 - Alteracao de usuario (inclui metodo de pagamento)
    // =====================================================================

    public void alterarUsuario(String usuario, String atributo, String valor) {
        java.util.Map<String,String> u = usuarioExistente(usuario);
        switch (atributo) {
            case "nome": obrigatorio(valor, "Nome nao pode ser nulo."); break;
            case "senha": obrigatorio(valor, "Senha nao pode ser nula."); break;
            case "email":
                obrigatorio(valor, "Email nao pode ser nulo.");
                if (!valor.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Email invalido.");
                for (java.util.Map.Entry<String, java.util.Map<String,String>> entrada : usuarios.entrySet())
                    if (!entrada.getKey().equals(usuario) && entrada.getValue().get("email").equalsIgnoreCase(valor)) throw new IllegalArgumentException("Email ja cadastrado.");
                break;
            case "plano":
                if (!"avulso".equals(valor) && !"assinante".equals(valor)) throw new IllegalArgumentException("Plano invalido.");
                if (!valor.equals(u.get("plano"))) {
                    if ("assinante".equals(valor)) u.put("mensalidade", "30,00");
                    else u.remove("mensalidade");
                }
                break;
            case "metodoPagamento":
                if (!"saldoPreCarregado".equals(valor)) throw new IllegalArgumentException("Valor de metodo de pagamento invalido.");
                u.putIfAbsent("saldo", "0,00"); break;
            case "saldo":
                if (!"saldoPreCarregado".equals(u.get("metodoPagamento"))) throw new IllegalArgumentException("Usuario nao usa saldo pre-carregado.");
                obrigatorio(valor, "Valor nao pode ser nulo.");
                java.math.BigDecimal quantia;
                try { quantia = new java.math.BigDecimal(valor.replace(',', '.')); } catch (NumberFormatException e) { throw new IllegalArgumentException("Valor deve ser numerico."); }
                if (quantia.signum() <= 0) throw new IllegalArgumentException("Valor deve ser positivo.");
                valor = new java.math.BigDecimal(u.get("saldo").replace(',', '.')).add(quantia).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
                break;
            default: throw new IllegalArgumentException("Atributo nao pode ser alterado.");
        }
        u.put(atributo, valor);
    }

    public void alterarUsuario(String usuario, String atributo, String valor1, String chave) {
        java.util.Map<String,String> u = usuarioExistente(usuario);
        if (!"metodoPagamento".equals(atributo)) throw new IllegalArgumentException("Atributo nao pode ser alterado.");
        if (!"pix".equals(valor1)) throw new IllegalArgumentException("Valor de metodo de pagamento invalido.");
        obrigatorio(chave, "Chave pix nao pode ser nula.");
        u.put("metodoPagamento", valor1); u.put("chavePix", chave);
    }

    public void alterarUsuario(String usuario, String atributo, String valor1, String numero, String validade) {
        java.util.Map<String,String> u = usuarioExistente(usuario);
        if (!"metodoPagamento".equals(atributo)) throw new IllegalArgumentException("Atributo nao pode ser alterado.");
        if (!"cartao".equals(valor1)) throw new IllegalArgumentException("Valor de metodo de pagamento invalido.");
        obrigatorio(numero, "Numero do cartao nao pode ser nulo.");
        obrigatorio(validade, "Validade do cartao nao pode ser nula.");
        if (!validade.matches("(0[1-9]|1[0-2])/\\d{4}")) throw new IllegalArgumentException("Validade do cartao invalida.");
        u.put("metodoPagamento", valor1); u.put("numeroCartao", numero); u.put("validadeCartao", validade);
    }

    private java.util.Map<String,String> usuarioExistente(String id) {
        obrigatorio(id, "Identificacao do usuario nao pode ser nula.");
        java.util.Map<String,String> usuario = usuarios.get(id);
        if (usuario == null) throw new IllegalArgumentException("Usuario nao existe.");
        return usuario;
    }

    // =====================================================================
    // US4 - Remocao de usuario
    // =====================================================================

    public void removerUsuario(String usuario) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    // =====================================================================
    // US5 - Cadastro de estacao
    // =====================================================================

    public String criarEstacao(String nome, String endereco, String capacidade) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    // =====================================================================
    // US6 - Gestao de bicicletas na estacao
    // =====================================================================

    public void adicionarBicicleta(String estacao, String bicicleta, String tipo) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    public void adicionarBicicleta(String estacao, String bicicleta, String tipo, String nivelBateria) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    public void removerBicicleta(String estacao, String bicicleta) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    public String getAtributoBicicleta(String bicicleta, String atributo) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    // =====================================================================
    // US7 - Consulta de disponibilidade
    // =====================================================================

    public String getBicicletasDisponiveis(String estacao) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    public String getVagasDisponiveis(String estacao) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    public String getEstadoBicicleta(String bicicleta) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    // =====================================================================
    // US8 - Retirada de bicicleta
    // =====================================================================

    public String retirarBicicleta(String usuario, String estacao, String bicicleta, String data) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    // =====================================================================
    // US9 - Devolucao de bicicleta
    // =====================================================================

    public void devolverBicicleta(String aluguel, String estacao, String data) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    // =====================================================================
    // US10 - Consulta de historico de alugueis
    // =====================================================================

    public String getHistoricoAlugueis(String usuario) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    public String getHistoricoAlugueis(String usuario, String atributo) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    public String getAtributoAluguel(String aluguel, String atributo) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    // =====================================================================
    // US11/US12 - Planos e tarifacao / criacao de novos planos
    // =====================================================================

    public String criarPlano(String nome, String franquiaMinutos, String valorMinutoExcedente, String mensalidade) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    // =====================================================================
    // US13 - Regras de bloqueio / lancamento manual de penalidade
    // =====================================================================

    public void lancaPenalidade(String usuario, String tipo, String valor, String motivo) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    // =====================================================================
    // US14 - Lancamento e conclusao de manutencao
    // =====================================================================

    public void lancaDefeito(String bicicleta, String descricao) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    public void concluiManutencao(String bicicleta) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    // =====================================================================
    // US15 - Relatorios
    // =====================================================================

    public String getTotalFaturado(String dataInicial, String dataFinal) {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    // =====================================================================
    // US16 - Undo/redo
    // =====================================================================

    public void undo() {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    public void redo() {
        throw new UnsupportedOperationException(AINDA_NAO_IMPLEMENTADO);
    }

    // =====================================================================
    // Ciclo de vida do sistema
    // =====================================================================

    public void zerarSistema() {
        usuarios.clear(); proximoId = 1;
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(ARQUIVO)); } catch (java.io.IOException e) { throw new IllegalStateException("Erro ao limpar dados.", e); }
    }

    public void encerrarSistema() {
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(new java.io.FileOutputStream(ARQUIVO))) {
            out.writeObject(usuarios); out.writeInt(proximoId);
        } catch (java.io.IOException e) { throw new IllegalStateException("Erro ao salvar dados.", e); }
    }
}
