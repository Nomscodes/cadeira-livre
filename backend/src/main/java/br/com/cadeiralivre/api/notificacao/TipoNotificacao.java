package br.com.cadeiralivre.api.notificacao;

/** O que aconteceu. Cada tipo já sabe o título que aparece no sino. */
public enum TipoNotificacao {

    // para o dono da barbearia
    NOVO_AGENDAMENTO("Novo pedido de agendamento"),
    CANCELAMENTO("Agendamento cancelado"),
    LEMBRETE_DONO("Atendimento daqui a pouco"),

    // para o cliente
    HORARIO_APROVADO("Horário confirmado"),
    HORARIO_RECUSADO("Pedido não aceito"),
    ATENDIMENTO_CONCLUIDO("Como foi seu atendimento?"),
    LEMBRETE_CLIENTE("Seu horário é amanhã");

    private final String titulo;

    TipoNotificacao(String titulo) {
        this.titulo = titulo;
    }

    public String getTitulo() {
        return titulo;
    }
}
