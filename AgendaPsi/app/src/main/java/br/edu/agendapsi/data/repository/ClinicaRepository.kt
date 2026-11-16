package br.edu.agendapsi.data.repository

import br.edu.agendapsi.data.local.ProfissionalEntity
import br.edu.agendapsi.data.model.AgendamentoResumo
import kotlinx.coroutines.flow.Flow

interface ClinicaRepository {
    fun observarAgenda(dia: Long, profissionalId: Long?): Flow<List<AgendamentoResumo>>
    fun observarProfissionais(): Flow<List<ProfissionalEntity>>
}
