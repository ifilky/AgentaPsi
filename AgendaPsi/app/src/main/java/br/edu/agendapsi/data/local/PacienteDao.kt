package br.edu.agendapsi.data.local

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface PacienteDao {
    @Insert suspend fun inserir(paciente: PacienteEntity): Long
}
