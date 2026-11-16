package br.edu.agendapsi.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET

interface TarefasApi {
    @GET("c/b2c8-0fa1-4745-a7b6")
    suspend fun consultarTarefas(): RespostaTarefas
}

@Serializable
data class RespostaTarefas(val todos: List<TarefaDemo>)

@Serializable
data class TarefaDemo(val todo: String, val completed: Boolean)
