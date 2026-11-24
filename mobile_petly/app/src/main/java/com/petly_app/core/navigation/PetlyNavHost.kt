package com.petly_app.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.*
import com.petly_app.BuildConfig
import com.petly_app.core.AppContainer
import com.petly_app.core.session.SessionRestore
import com.petly_app.ui.asistente.ChatViewModel
import com.petly_app.ui.auth.*
import com.petly_app.ui.catalogo.*
import com.petly_app.ui.mensajes.ContadorMensajesNoLeidos
import com.petly_app.ui.perfil.*
import com.petly_app.ui.screen.MainAppScreen
import com.petly_app.ui.screen.PetlyTab
import com.petly_app.ui.seguimiento.AgendaVacunasScreen
import com.petly_app.ui.seguimiento.AgendaVacunasViewModel
import com.petly_app.ui.seguimiento.SeguimientosScreen
import com.petly_app.ui.seguimiento.SeguimientosViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CATALOG = "catalogo"
    const val PROFILE = "perfil"
    const val DETAIL = "mascota/{id}"
    const val NOTIFICATIONS = "notificaciones"
}

@Composable
fun PetlyNavHost(
    container: AppContainer,
    resetToken: String? = null,
    pushEvent: Long? = null,
    notificationUpdates: Flow<Unit> = emptyFlow(),
    onPushHandled: () -> Unit = {},
    onResetHandled: () -> Unit = {}
) {
    val authenticated by container.session.authenticated.collectAsStateWithLifecycle()
    val restore by container.session.restoreState.collectAsStateWithLifecycle()
    val notice by container.session.notice.collectAsStateWithLifecycle()
    val restoreScope = rememberCoroutineScope()
    LaunchedEffect(container, resetToken) {
        if (resetToken == null) container.session.restore()
    }
    if (resetToken == null && restore != SessionRestore.READY) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            if (restore == SessionRestore.RETRY) {
                Text("No se pudo recuperar tu sesión. Comprueba tu conexión e intenta nuevamente.")
                Button(onClick = { restoreScope.launch { container.session.restore() } }) { Text("Reintentar") }
            } else {
                CircularProgressIndicator()
                Text("Recuperando sesión…")
            }
        }
        return
    }
    var preview by rememberSaveable { mutableStateOf(false) }
    // La vista de prueba no crea tokens, no inicia una sesión real y solo existe en debug.
    val isPreview = BuildConfig.DEBUG && preview
    LaunchedEffect(authenticated, isPreview) {
        com.petly_app.data.repository.FavoritosManager.clear()
        com.petly_app.data.repository.SolicitudAdopcionManager.clear()
        if (authenticated && !isPreview) {
            com.petly_app.data.repository.FavoritosManager.api = container.favoritosApi
            com.petly_app.data.repository.SolicitudAdopcionManager.api = container.solicitudesApi
            com.petly_app.data.repository.SolicitudAdopcionManager.fotosApi = container.fotosHogarApi
            com.petly_app.data.repository.SolicitudAdopcionManager.fichaVeterinariaApi = container.fichaVeterinariaApi
            com.petly_app.data.repository.SolicitudAdopcionManager.resenasApi = container.resenasApi
            com.petly_app.data.repository.SolicitudAdopcionManager.resolver = container.resolver
            launch { com.petly_app.data.repository.FavoritosManager.cargar() }
            runCatching { com.petly_app.data.repository.SolicitudAdopcionManager.cargar() }
        }
    }
    key(authenticated, isPreview, resetToken) {
        val nav = rememberNavController()
        var selectedTab by rememberSaveable { mutableStateOf(PetlyTab.INICIO) }
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val privateGraph = resetToken == null && (authenticated || isPreview)
        val contadorMensajes = remember(container) {
            ContadorMensajesNoLeidos { container.mensajeriaApi.contarNoLeidos().total }
        }
        val notificationsFactory = remember(container, isPreview) {
            viewModelFactory { initializer { NotificacionesViewModel(container.notificacionesApi, isPreview) } }
        }
        // Este almacén pertenece a la sesión; cerrar sesión elimina también los avisos en memoria.
        val notificationsStore = remember { ViewModelStore() }
        val notificationsOwner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = notificationsStore
            }
        }
        DisposableEffect(notificationsStore) { onDispose { notificationsStore.clear() } }
        val notificationsVm: NotificacionesViewModel = viewModel(viewModelStoreOwner = notificationsOwner, factory = notificationsFactory)
        val notificationsState by notificationsVm.state.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(privateGraph, isPreview, lifecycle, contadorMensajes) {
            if (privateGraph && !isPreview) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { contadorMensajes.observar() }
            }
        }
        val currentEntry by nav.currentBackStackEntryAsState()
        val bandejaVisible = currentEntry?.destination?.route == Routes.NOTIFICATIONS
        LaunchedEffect(privateGraph, isPreview, lifecycle, bandejaVisible, notificationUpdates) {
            if (privateGraph && !isPreview) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                notificationsVm.observe(bandejaVisible, notificationUpdates)
            }
        }
        LaunchedEffect(authenticated, resetToken, pushEvent) {
            if (authenticated && !isPreview && resetToken == null && pushEvent != null) {
                nav.navigate("notificaciones/$pushEvent") { launchSingleTop = true }
                onPushHandled()
            }
        }
        NavHost(navController = nav, startDestination = if (privateGraph) Routes.CATALOG else if (resetToken != null) "recuperacion" else Routes.LOGIN) {
            if (!privateGraph) {
                composable(Routes.LOGIN) {
                    val factory = remember(container) {
                        viewModelFactory { initializer { AuthViewModel(container.authRepository, container.healthRepository) } }
                    }
                    val vm: AuthViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    LoginScreen(state.copy(error = state.error ?: notice), vm::login,
                        onRegister = { vm.clearFormState(); nav.navigate(Routes.REGISTER) { launchSingleTop = true } },
                        onCheckConnection = vm::checkConnection,
                        onRecovery = { nav.navigate("recuperacion") },
                        onPreview = if (BuildConfig.DEBUG) ({ container.resetPreview(); preview = true }) else null)
                }
                composable("recuperacion") {
                    val factory = remember(container) { viewModelFactory { initializer { RecuperacionViewModel(container.recuperacion) } } }
                    val vm: RecuperacionViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    RecuperacionScreen(state, resetToken, vm::request, vm::confirm, onBack = {
                        if(resetToken != null) onResetHandled() else nav.popBackStack()
                    })
                }
                composable(Routes.REGISTER) {
                    val factory = remember(container) {
                        viewModelFactory { initializer { AuthViewModel(container.authRepository, container.healthRepository) } }
                    }
                    val vm: AuthViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    RegisterScreen(state, { name, email, password, confirmation, accepted ->
                        vm.register(email, password, confirmation, name, accepted)
                    }, onBack = { vm.clearFormState(); nav.popBackStack() })
                }
            } else {
                composable(Routes.CATALOG) {
                    val mensajesNoLeidos by contadorMensajes.total.collectAsStateWithLifecycle()
                    val catalogFactory = remember(container, isPreview) {
                        viewModelFactory { initializer { CatalogoViewModel(container.catalogo(isPreview)) } }
                    }
                    val perfilFactory = remember(container, isPreview) {
                        viewModelFactory { initializer { createPerfilViewModel(container, isPreview) } }
                    }
                    val disponibilidadFactory = remember(container, isPreview) {
                        viewModelFactory { initializer { DisponibilidadViewModel(container.disponibilidad(isPreview), isPreview) } }
                    }

                    val chatFactory = remember(container) {
                        viewModelFactory { initializer { ChatViewModel(container.chatRepository, container.session.authenticated) } }
                    }
                    val chatVm: ChatViewModel = viewModel(factory = chatFactory)
                    val chatState by chatVm.state.collectAsStateWithLifecycle()

                    val catalogVm: CatalogoViewModel = viewModel(factory = catalogFactory)
                    val perfilVm: PerfilViewModel = viewModel(factory = perfilFactory)
                    val disponibilidadVm: DisponibilidadViewModel = viewModel(factory = disponibilidadFactory)

                    val catalogState by catalogVm.state.collectAsStateWithLifecycle()
                    val perfilState by perfilVm.state.collectAsStateWithLifecycle()
                    val disponibilidadState by disponibilidadVm.state.collectAsStateWithLifecycle()
                    val submittedApplications by com.petly_app.data.repository.SolicitudAdopcionManager.submitted.collectAsStateWithLifecycle()
                    LaunchedEffect(perfilState.saveRevision) {
                        if (perfilState.saveRevision > 0) catalogVm.retry()
                    }

                    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        catalogVm.retry()
                    }

                    MainAppScreen(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it },
                        snackbarHostState = snackbarHostState,
                        unreadMessages = mensajesNoLeidos,
                        onMessagesRead = contadorMensajes::actualizar,
                        onOpenSeguimientos = { nav.navigate("seguimientos") { launchSingleTop = true } },
                        onOpenAgendaVacunas = { solicitud -> nav.navigate("agenda-vacunas/$solicitud") { launchSingleTop = true } },
                        historiasRepository = container.historiasAdopcion,
                        reportReminders = { RecordatoriosReporte(container.reportesSeguimiento) },
                        mensajeriaApi = if (isPreview) null else container.mensajeriaApi,
                        contentResolver = container.resolver,
                        solicitudesChat = submittedApplications.mapNotNull { draft ->
                            com.petly_app.data.repository.SolicitudAdopcionManager.solicitudesActivas()
                                .firstOrNull { it.second == draft.mascotaId }
                        },
                        notificacionesState = notificationsState,
                        onOpenNotifications = { nav.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true } },
                        chatState = chatState,
                        onOpenChat = chatVm::open,
                        onEditChat = chatVm::edit,
                        onSendChat = chatVm::send,
                        onRetryChat = chatVm::retry,
                        onEditFailedChat = chatVm::editFailed,
                        onClearChat = chatVm::clear,
                        catalogoState = catalogState,
                        perfilState = perfilState,
                        disponibilidadState = disponibilidadState,
                        isPreview = isPreview,
                        onEditFilters = catalogVm::edit,
                        onApplyFilters = catalogVm::applyFilters,
                        onClearFilters = catalogVm::clearFilters,
                        onChangePage = catalogVm::changePage,
                        onRetryCatalog = catalogVm::retry,
                        onDetailPet = { nav.navigate("mascota/$it") { launchSingleTop = true } },
                        onEditPerfil = perfilVm::edit,
                        onSavePerfil = perfilVm::save,
                        onLoadPerfil = perfilVm::load,
                        onRefreshProfile = perfilVm::refreshSummary,
                        onAddFranja = disponibilidadVm::addFranja,
                        onRemoveFranja = disponibilidadVm::removeFranja,
                        onSaveDisponibilidad = disponibilidadVm::save,
                        onShareAutorizaciones = if (isPreview) null else ({ nav.navigate("autorizaciones") }),
                        onOpenKyc = { nav.navigate("verificacion-dni") },
                        onExit = {
                            if (isPreview) {
                                container.resetPreview()
                                preview = false
                            } else {
                                scope.launch {
                                    try {
                                        container.session.logout()
                                    } catch (error: Exception) {
                                        if (error is kotlinx.coroutines.CancellationException) throw error
                                        snackbarHostState.showSnackbar("No se pudo cerrar la sesión. Intenta nuevamente.")
                                    }
                                }
                            }
                        }
                    )
                }
                composable(Routes.PROFILE) {
                    val factory = remember(container, isPreview) {
                        viewModelFactory { initializer { createPerfilViewModel(container, isPreview) } }
                    }
                    val vm: PerfilViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    CuestionarioScreen(state, onRefreshProfile = vm::refreshSummary, preview = isPreview, onEdit = vm::edit, onSave = vm::save, onRetry = vm::load, onBack = { nav.popBackStack() }, onShare = if(isPreview) null else ({ nav.navigate("autorizaciones") }), onOpenNotifications = { nav.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true } }, onOpenSeguimientos = { nav.navigate("seguimientos") { launchSingleTop = true } },
                        onOpenAgendaVacunas = { solicitud -> nav.navigate("agenda-vacunas/$solicitud") { launchSingleTop = true } },
                        historiasRepository = container.historiasAdopcion,
                        reportReminders = { RecordatoriosReporte(container.reportesSeguimiento) })
                }
                if (!isPreview) composable(
                    "agenda-vacunas/{solicitud}",
                    arguments = listOf(navArgument("solicitud") { type = NavType.LongType }),
                ) { entry ->
                    val solicitud = requireNotNull(entry.arguments).getLong("solicitud")
                    val factory = remember(container, solicitud) {
                        viewModelFactory { initializer { AgendaVacunasViewModel(container.agendaVacunasApi, solicitud) } }
                    }
                    val vm: AgendaVacunasViewModel = viewModel(factory = factory)
                    AgendaVacunasScreen(vm, onBack = { nav.popBackStack() })
                }
                composable(
                    "seguimientos?actividad={actividad}",
                    arguments = listOf(navArgument("actividad") { type = NavType.LongType; defaultValue = 0L }),
                ) { entry ->
                    val factory = remember(container, isPreview) {
                        viewModelFactory { initializer { SeguimientosViewModel(container.seguimientosApi, isPreview) } }
                    }
                    val vm: SeguimientosViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    SeguimientosScreen(state, vm::load, vm::select, vm::edit, vm::submit,
                        onBack = { nav.popBackStack() },
                        onOpenReporte = { nav.navigate("reportes-seguimiento/$it") },
                        objetivoId = entry.arguments?.getLong("actividad")?.takeIf { it > 0 })
                }
                composable(
                    "reportes-seguimiento/{entrega}",
                    arguments = listOf(navArgument("entrega") { type = NavType.LongType })
                ) { entry ->
                    ReportesSeguimientoScreen(
                        container.reportesSeguimiento,
                        requireNotNull(entry.arguments).getLong("entrega"),
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    "notificaciones/{evento}",
                    arguments = listOf(navArgument("evento") { type = NavType.LongType })
                ) { entry ->
                    val event = requireNotNull(entry.arguments).getLong("evento")
                    AvisoSolicitudScreen(notificationsVm::openNotice, event) { nav.popBackStack() }
                }
                composable(Routes.NOTIFICATIONS) {
                    NotificacionesScreen(notificationsState, { notificationsVm.load() }, notificationsVm::read,
                        notificationsVm::readAll, { notificationsVm.load(more = true) },
                        onBack = { nav.popBackStack() },
                        onOpenPet = { nav.navigate("mascota/$it") { launchSingleTop = true } },
                        onLoadCriterios = notificationsVm::loadCriterios,
                        onSaveCriterios = notificationsVm::saveCriterios,
                        onOpenReporte = { nav.navigate("reportes-seguimiento/$it") },
                        onOpenSeguimiento = { nav.navigate("seguimientos?actividad=$it") })
                }
                composable("autorizaciones") {
                    val factory = remember(container) { viewModelFactory { initializer { AutorizacionViewModel(container.autorizaciones) } } }
                    val vm: AutorizacionViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    AutorizacionesScreen(state, vm::change, vm::load, onBack = { nav.popBackStack() })
                }
                composable("verificacion-dni") {
                    val factory = remember(container) {
                        viewModelFactory { initializer { com.petly_app.ui.perfil.KycViewModel(container.kycRepository) } }
                    }
                    val vm: com.petly_app.ui.perfil.KycViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    com.petly_app.ui.perfil.VerificacionDniScreen(
                        state, vm::edit, vm::selectFront, vm::selectBack, vm::upload, vm::consultar, vm::load,
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    val id = requireNotNull(entry.arguments).getLong("id")
                    val factory = remember(container, isPreview, id) {
                        viewModelFactory { initializer { DetalleMascotaViewModel(container.catalogo(isPreview), id) } }
                    }
                    val vm: DetalleMascotaViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    DetalleMascotaScreen(
                        state, isPreview, vm::load,
                        onBack = { nav.popBackStack() },
                        onConsejos = vm::consejos,
                        onAuthorizeShelter = if (isPreview) null else container.autorizaciones::allow,
                        onSolicitudEnviada = {
                            selectedTab = PetlyTab.PERFIL
                            nav.popBackStack(Routes.CATALOG, inclusive = false)
                            scope.launch { snackbarHostState.showSnackbar("Solicitud enviada") }
                        }
                    )
                }
            }
        }
    }
}

private fun createPerfilViewModel(container: AppContainer, preview: Boolean) = PerfilViewModel(
    container.perfil(preview), preview,
    loadUser = { container.usuarioRepository.currentUser() },
    loadApplications = {
        var count = 0
        var page = 1
        do {
            val response = container.solicitudesApi.listar(page++)
            count += if (response.isJsonArray) response.asJsonArray.size()
                else response.asJsonObject.getAsJsonArray("results").size()
            val more = !response.isJsonArray && response.asJsonObject.get("next")?.let { !it.isJsonNull } == true
        } while (more)
        count
    },
    loadVerified = { container.kycRepository.load()?.estado in listOf("VERIFICADO", "APROBADO_MANUAL") }
)
