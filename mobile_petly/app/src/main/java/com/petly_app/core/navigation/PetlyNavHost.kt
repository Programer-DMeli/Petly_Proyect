package com.petly_app.core.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.*
import com.petly_app.BuildConfig
import com.petly_app.core.AppContainer
import com.petly_app.ui.auth.*
import com.petly_app.ui.catalogo.*
import com.petly_app.ui.perfil.*
import com.petly_app.ui.screen.MainAppScreen

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CATALOG = "catalogo"
    const val PROFILE = "perfil"
    const val DETAIL = "mascota/{id}"
}

@Composable
fun PetlyNavHost(container: AppContainer, resetToken: String? = null, onResetHandled: () -> Unit = {}) {
    val authenticated by container.session.authenticated.collectAsStateWithLifecycle()
    var preview by rememberSaveable { mutableStateOf(false) }
    // La vista de prueba no crea tokens, no inicia una sesión real y solo existe en debug.
    val isPreview = BuildConfig.DEBUG && preview
    key(authenticated, isPreview, resetToken) {
        val nav = rememberNavController()
        val privateGraph = resetToken == null && (authenticated || isPreview)
        NavHost(navController = nav, startDestination = if (privateGraph) Routes.CATALOG else if (resetToken != null) "recuperacion" else Routes.LOGIN) {
            if (!privateGraph) {
                composable(Routes.LOGIN) {
                    val factory = remember(container) {
                        viewModelFactory { initializer { AuthViewModel(container.authRepository, container.healthRepository) } }
                    }
                    val vm: AuthViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    LoginScreen(state, vm::login,
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
                    val catalogFactory = remember(container, isPreview) {
                        viewModelFactory { initializer { CatalogoViewModel(container.catalogo(isPreview)) } }
                    }
                    val perfilFactory = remember(container, isPreview) {
                        viewModelFactory { initializer { PerfilViewModel(container.perfil(isPreview), isPreview) } }
                    }
                    val catalogVm: CatalogoViewModel = viewModel(factory = catalogFactory)
                    val perfilVm: PerfilViewModel = viewModel(factory = perfilFactory)
                    val catalogState by catalogVm.state.collectAsStateWithLifecycle()
                    val perfilState by perfilVm.state.collectAsStateWithLifecycle()

                    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        catalogVm.retry()
                    }

                    MainAppScreen(
                        catalogoState = catalogState,
                        perfilState = perfilState,
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
                        onShareAutorizaciones = if (isPreview) null else ({ nav.navigate("autorizaciones") }),
                        onExit = {
                            if (isPreview) { container.resetPreview(); preview = false } else container.session.clear()
                        }
                    )
                }
                composable(Routes.PROFILE) {
                    val factory = remember(container, isPreview) {
                        viewModelFactory { initializer { PerfilViewModel(container.perfil(isPreview), isPreview) } }
                    }
                    val vm: PerfilViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    CuestionarioScreen(state, isPreview, vm::edit, vm::save, vm::load, onBack = { nav.popBackStack() }, onShare = if(isPreview) null else ({ nav.navigate("autorizaciones") }))
                }
                composable("autorizaciones") {
                    val factory = remember(container) { viewModelFactory { initializer { AutorizacionViewModel(container.autorizaciones) } } }
                    val vm: AutorizacionViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    AutorizacionesScreen(state, vm::change, vm::load, onBack = { nav.popBackStack() })
                }
                composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    val id = requireNotNull(entry.arguments).getLong("id")
                    val factory = remember(container, isPreview, id) {
                        viewModelFactory { initializer { DetalleMascotaViewModel(container.catalogo(isPreview), id) } }
                    }
                    val vm: DetalleMascotaViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    DetalleMascotaScreen(state, isPreview, vm::load, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
