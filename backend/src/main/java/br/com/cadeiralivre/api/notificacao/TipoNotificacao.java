package br.com.cadeiralivre.api.notificacao;

/** O que aconteceu. Cada tipo já sabe o título que aparece no sino. */
public enum TipoNotificacao {

    // para o dono da barbearia
    NOVO_AGENDAMENTO("Novo pedido de agendamento"),
    CANCELAMENTO("Agendamento cancelado"),
    LEMBRETE_DONO("Atendimento daqui a pouco"),
    CONCLUIDO_PARA_DONO("Atendimento concluído"),
    // entre a equipe (texto livre, com remetente)
    AVISO_EQUIPE("Aviso do responsável"),
    ALERTA_FUNCIONARIO("Alerta de funcionário"),

    // para o cliente
    HORARIO_APROVADO("Horário confirmado"),
    HORARIO_RECUSADO("Pedido não aceito"),
    ATENDIMENTO_CONCLUIDO("Como foi seu atendimento?"),
    LEMBRETE_CLIENTE("Seu horário é amanhã"),
    PEDIDO_AVALIACAO("O salão pediu sua avaliação");

    private final String titulo;

    TipoNotificacao(String titulo) {
        this.titulo = titulo;
    }

    public String getTitulo() {
        return titulo;
    }
}
