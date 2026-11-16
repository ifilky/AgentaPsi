package br.edu.agendapsi.ui

import androidx.lifecycle.SavedStateHandle
import br.edu.agendapsi.data.local.ProfissionalEntity
import br.edu.agendapsi.data.model.*
import br.edu.agendapsi.data.remote.RespostaTarefas
import br.edu.agendapsi.data.remote.TarefasApi
import br.edu.agendapsi.data.remote.TarefaDemo
import br.edu.agendapsi.data.repository.ClinicaRepository
import br.edu.agendapsi.ui.agenda.*
import br.edu.agendapsi.ui.inicio.*
import java.io.IOException
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = RelogioTeste()
    private val repository = RepositorioTeste()
    private val dia = LocalDate.of(2026, 10, 9)
    private val tarefasApi = object : TarefasApi {
        override suspend fun consultarTarefas() = RespostaTarefas(emptyList())
    }

    @Before fun preparar() { Dispatchers.setMain(dispatcher) }
    @After fun finalizar() { Dispatchers.resetMain() }

    @Test fun agendaRestauraFiltrosEObservaMudancasSemReabrirTela() = runTest(dispatcher) {
        val salvo = SavedStateHandle(mapOf("diaEpoch" to dia.plusDays(1).toEpochDay(), "profissionalId" to 2L))
        val vm = AgendaViewModel(repository, clock, salvo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        runCurrent()
        assertEquals(dia.plusDays(1), vm.state.value.dia)
        assertEquals(2L, vm.state.value.profissionalId)
        assertTrue((vm.state.value.conteudo as AgendaState.Disponivel).atendimentos.isEmpty())

        vm.selecionarDia(dia)
        vm.selecionarProfissional(1)
        runCurrent()
        assertEquals(dia.toEpochDay(), salvo.get<Long>("diaEpoch"))
        assertEquals(1, (vm.state.value.conteudo as AgendaState.Disponivel).atendimentos.size)
        repository.registros.value = repository.registros.value.map { it.copy(pacienteNome = "Nome corrigido") }
        runCurrent()
        assertEquals("Nome corrigido", (vm.state.value.conteudo as AgendaState.Disponivel).atendimentos.single().paciente)
    }

    @Test fun falhaDeLeituraMostraErroETentarNovamenteRecupera() = runTest(dispatcher) {
        repository.falhar = true
        val vm = AgendaViewModel(repository, clock, SavedStateHandle())
        assertEquals(AgendaState.Carregando, vm.state.value.conteudo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        runCurrent()
        assertEquals(AgendaState.Erro, vm.state.value.conteudo)
        repository.falhar = false
        vm.tentarNovamente()
        runCurrent()
        assertTrue(vm.state.value.conteudo is AgendaState.Disponivel)
    }

    @Test fun inicioAtualizaProximosComRelogioETrocaDiaSemInventarRegistros() = runTest(dispatcher) {
        val vm = InicioViewModel(repository, clock, SavedStateHandle(), tarefasApi)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        runCurrent()
        assertEquals(1, (vm.state.value.conteudo as InicioState.Disponivel).resumo.proximos.size)
        clock.horario = dia.atTime(8, 1)
        vm.atualizarRelogio()
        runCurrent()
        val resumo = (vm.state.value.conteudo as InicioState.Disponivel).resumo
        assertTrue(resumo.proximos.isEmpty())
        assertEquals(1, resumo.pendentes)
        clock.horario = dia.plusDays(1).atStartOfDay()
        vm.atualizarRelogio()
        runCurrent()
        assertEquals(dia.plusDays(1), vm.state.value.dia)
        assertEquals(0, (vm.state.value.conteudo as InicioState.Disponivel).resumo.total)
    }

    @Test fun inicioMantemResumoDuranteFalhaDaApiEPermiteRepetirConsulta() = runTest(dispatcher) {
        val tarefas = listOf(
            TarefaDemo("Tarefa 1", true),
            TarefaDemo("Tarefa 2", false),
            TarefaDemo("Tarefa 3", false)
        )
        var chamadas = 0
        val api = object : TarefasApi {
            override suspend fun consultarTarefas(): RespostaTarefas {
                assertEquals(3, limite)
                return when (++chamadas) {
                    1 -> throw IOException("Sem conexão")
                    2 -> RespostaTarefas(tarefas)
                    else -> RespostaTarefas(emptyList())
                }
            }
        }
        val vm = InicioViewModel(repository, clock, SavedStateHandle(), api)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }

        assertEquals(TarefasDemoState.Carregando, vm.tarefas.value)
        vm.carregarTarefas()
        runCurrent()
        assertEquals(1, chamadas)
        assertEquals(TarefasDemoState.Erro, vm.tarefas.value)
        assertTrue(vm.state.value.conteudo is InicioState.Disponivel)

        vm.carregarTarefas()
        runCurrent()
        assertEquals(TarefasDemoState.Disponivel(tarefas), vm.tarefas.value)

        vm.carregarTarefas()
        runCurrent()
        assertEquals(TarefasDemoState.Disponivel(emptyList()), vm.tarefas.value)
    }

    private class RelogioTeste : Clock() {
        var horario: LocalDateTime = LocalDateTime.of(2026, 10, 9, 7, 0)
        override fun getZone(): ZoneId = ZoneId.of("America/Sao_Paulo")
        override fun withZone(zone: ZoneId): Clock = fixed(instant(), zone)
        override fun instant(): Instant = horario.atZone(zone).toInstant()
    }

    private class RepositorioTeste : ClinicaRepository {
        var falhar = false
        val registros = MutableStateFlow(listOf(AgendamentoResumo(1, 1, "Paciente", 1, "Ana Costa",
            LocalDate.of(2026, 10, 9).toEpochDay(), 480, 50, Modalidade.PRESENCIAL, StatusAgendamento.AGENDADO)))
        override fun observarAgenda(dia: Long, profissionalId: Long?): Flow<List<AgendamentoResumo>> = flow {
            if (falhar) error("Falha de leitura")
            emitAll(registros.map { lista -> lista.filter { it.diaEpoch == dia && (profissionalId == null || it.profissionalId == profissionalId) } })
        }
        override fun observarProfissionais() = flowOf(listOf(ProfissionalEntity(1, "Ana Costa"), ProfissionalEntity(2, "Bruno Lima")))
    }
}
