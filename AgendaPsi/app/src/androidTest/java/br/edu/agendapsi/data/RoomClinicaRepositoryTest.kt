package br.edu.agendapsi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.edu.agendapsi.data.local.ClinicaDatabase
import br.edu.agendapsi.data.model.StatusAgendamento
import br.edu.agendapsi.data.repository.RoomClinicaRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomClinicaRepositoryTest {
    private val dia = LocalDate.of(2026, 10, 9)
    private val fuso = ZoneId.of("America/Sao_Paulo")
    private val clock = Clock.fixed(dia.atTime(7, 0).atZone(fuso).toInstant(), fuso)
    private lateinit var contexto: Context
    private lateinit var db: ClinicaDatabase
    private lateinit var repository: RoomClinicaRepository

    @Before fun preparar() {
        contexto = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(contexto, ClinicaDatabase::class.java).build()
        repository = RoomClinicaRepository(db, clock)
    }

    @After fun fechar() { db.close() }

    @Test fun consultaAgendaOrdenadaComNomesEFiltroPorProfissional() = runBlocking<Unit> {
        val profissionais = repository.observarProfissionais().first()
        val agenda = repository.observarAgenda(dia.toEpochDay(), null).first()

        assertEquals(listOf("Ana Costa", "Bruno Lima"), profissionais.map { it.nome })
        assertEquals(8, agenda.size)
        assertEquals(agenda.map { it.inicioMinutos }.sorted(), agenda.map { it.inicioMinutos })
        assertEquals("Marina Alves", agenda.first().pacienteNome)
        assertEquals("Ana Costa", agenda.first().profissionalNome)
        assertTrue(agenda.any { it.status == StatusAgendamento.CANCELADO })
        val profissionalId = profissionais.first().id
        assertEquals(agenda.filter { it.profissionalId == profissionalId },
            repository.observarAgenda(dia.toEpochDay(), profissionalId).first())
        assertTrue(repository.observarAgenda(dia.plusDays(1).toEpochDay(), null).first().isEmpty())
    }

    @Test fun conservaAgendamentosAoReabrirOBanco() = runBlocking<Unit> {
        val nome = "agenda-teste-${UUID.randomUUID()}.db"

        suspend fun lerAgenda() = Room.databaseBuilder(contexto, ClinicaDatabase::class.java, nome).build().let { banco ->
            try {
                RoomClinicaRepository(banco, clock).observarAgenda(dia.toEpochDay(), null).first()
            } finally {
                banco.close()
            }
        }
        try {
            val antes = lerAgenda()
            val depois = lerAgenda()
            assertEquals(8, antes.size)
            assertEquals(antes, depois)
        } finally {
            contexto.deleteDatabase(nome)
        }
    }
}
