package br.edu.agendapsi.data.local

import androidx.room.*
import br.edu.agendapsi.data.model.AgendamentoResumo
import kotlinx.coroutines.flow.Flow

private const val RESUMO = """
    SELECT a.id, a.pacienteId, p.nome AS pacienteNome,
        a.profissionalId, f.nome AS profissionalNome, a.diaEpoch,
        a.inicioMinutos, a.duracaoMinutos, a.modalidade, a.status
    FROM agendamentos a
    JOIN pacientes p ON p.id = a.pacienteId
    JOIN profissionais f ON f.id = a.profissionalId
"""

@Dao
interface AgendamentoDao {
    @Query(RESUMO + " WHERE a.diaEpoch = :dia AND (:profissionalId IS NULL OR a.profissionalId = :profissionalId) ORDER BY a.inicioMinutos, a.id")
    fun observarAgenda(dia: Long, profissionalId: Long?): Flow<List<AgendamentoResumo>>
    @Insert suspend fun inserir(valor: AgendamentoEntity)
}
