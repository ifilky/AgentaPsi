package br.edu.agendapsi.data.model

enum class StatusAgendamento(val rotulo: String) {
    AGENDADO("Agendado"), CONFIRMADO("Confirmado"), CONCLUIDO("Concluído"),
    FALTOU("Faltou"), CANCELADO("Cancelado")
}
