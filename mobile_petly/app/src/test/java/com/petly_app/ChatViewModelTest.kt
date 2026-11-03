package com.petly_app

import com.petly_app.data.remote.ChatMessage
import com.petly_app.data.repository.ChatRepository
import com.petly_app.ui.asistente.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @Test fun conservaContextoAlCerrarYReintentaSinDuplicarMensajes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val requests = mutableListOf<List<ChatMessage>>()
            var fail = true
            val vm = ChatViewModel(ChatRepository { messages ->
                requests.add(messages)
                if (fail) throw java.io.IOException()
                "¿Cuántas horas estaría sola la mascota?"
            })
            vm.open(true); vm.edit("Vivo en departamento"); vm.send(); vm.send(); advanceUntilIdle()
            assertEquals(1, requests.size)
            assertNotNull(vm.state.value.error)
            vm.open(false); vm.open(true)
            assertEquals(1, vm.state.value.messages.size)
            fail = false; vm.retry(); advanceUntilIdle()
            assertEquals(requests[0], requests[1])
            assertEquals(2, vm.state.value.messages.size)
            vm.edit("Cuatro horas"); vm.send(); advanceUntilIdle()
            assertEquals(listOf("user", "model", "user"), requests.last().map { it.role })
            assertEquals("Cuatro horas", requests.last().last().text)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun cerrarSesionBorraHistorialYDescartaRespuestaPendiente() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val authenticated = MutableStateFlow(true)
            val vm = ChatViewModel(ChatRepository { delay(1000); "Respuesta anterior" }, authenticated)
            vm.open(true); vm.edit("Ayúdame a adoptar"); vm.send(); runCurrent()
            authenticated.value = false; advanceUntilIdle()
            assertTrue(vm.state.value.messages.isEmpty())
            assertFalse(vm.state.value.sending)
            assertFalse(vm.state.value.open)
            assertEquals("", vm.state.value.draft)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun recortaContextoPorParesYPermiteEditarMensajeFallido() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val requests = mutableListOf<List<ChatMessage>>()
            var fail = false
            val vm = ChatViewModel(ChatRepository { messages ->
                requests.add(messages)
                if (fail) throw java.io.IOException()
                "Respuesta breve"
            })
            repeat(15) { vm.edit("Consulta $it"); vm.send(); advanceUntilIdle() }
            assertEquals(21, requests.last().size)
            assertEquals("user", requests.last().first().role)
            assertEquals("Consulta 14", requests.last().last().text)
            fail = true; vm.edit("Revisar mensaje"); vm.send(); advanceUntilIdle()
            vm.editFailed()
            assertEquals("Revisar mensaje", vm.state.value.draft)
            assertNull(vm.state.value.error)
            assertEquals("model", vm.state.value.messages.last().role)
            vm.clear()
            assertTrue(vm.state.value.messages.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
}
