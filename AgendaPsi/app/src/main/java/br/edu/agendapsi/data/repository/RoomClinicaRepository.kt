package br.edu.agendapsi.data.repository

import androidx.room.withTransaction
import br.edu.agendapsi.data.local.*
import br.edu.agendapsi.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import java.time.Clock
import java.time.LocalDateTime

class RoomClinicaRepository(private val db: ClinicaDatabase, private val clock: Clock) : ClinicaRepository {
    private val pacientes = db.pacienteDao()
    private val profissionais = db.profissionalDao()
    private val agendamentos = db.agendamentoDao()

    private suspend fun inicializar() {
        db.withTransaction {
            if (profissionais.contar() != 0) return@withTransaction
            val ana = profissionais.inserir(ProfissionalEntity(nome = "Ana Costa"))
            val bruno = profissionais.inserir(ProfissionalEntity(nome = "Bruno Lima"))
            val ids = listOf("Marina Alves", "João Silva", "Clara Souza", "João Silva").map {
                pacientes.inserir(PacienteEntity(nome = it))
            }
            val agora = LocalDateTime.now(clock)
            val dia = agora.toLocalDate()
            listOf(8, 9, 10, 11, 13, 14, 16, 17).forEachIndexed { i, hora ->
                val encerrado = !agora.isBefore(dia.atTime(hora, 50))
                val status = when {
                    i == 2 -> StatusAgendamento.CANCELADO
                    encerrado && i == 1 -> StatusAgendamento.FALTOU
                    encerrado -> StatusAgendamento.CONCLUIDO
                    i % 2 == 0 -> StatusAgendamento.CONFIRMADO
                    else -> StatusAgendamento.AGENDADO
                }
                agendamentos.inserir(AgendamentoEntity(pacienteId = ids[i % ids.size],
                    profissionalId = if (i % 2 == 0) ana else bruno, diaEpoch = dia.toEpochDay(),
                    inicioMinutos = hora * 60, duracaoMinutos = 50,
                    modalidade = if (i % 2 == 0) Modalidade.PRESENCIAL else Modalidade.ONLINE, status = status))
            }
        }
    }

    // Todas as entradas aguardam a carga inicial. Uma falha reverte a transação e permite tentar de novo.
    override fun observarAgenda(dia: Long, profissionalId: Long?): Flow<List<AgendamentoResumo>> =
        agendamentos.observarAgenda(dia, profissionalId).onStart { inicializar() }
    override fun observarProfissionais(): Flow<List<ProfissionalEntity>> =
        profissionais.observarTodos().onStart { inicializar() }
}
