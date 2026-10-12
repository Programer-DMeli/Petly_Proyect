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

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CATALOG = "catalogo"
    const val PROFILE = "perfil"
    const val DETAIL = "mascota/{id}"
}

@Composable
fun PetlyNavHost(container: AppContainer) {
    val authenticated by container.session.authenticated.collectAsStateWithLifecycle()
    var preview by rememberSaveable { mutableStateOf(false) }
    // La vista de prueba no crea tokens, no inicia una sesión real y solo existe en debug.
    val isPreview = BuildConfig.DEBUG && preview
    key(authenticated, isPreview) {
        val nav = rememberNavController()
        val privateGraph = authenticated || isPreview
        NavHost(navController = nav, startDestination = if (privateGraph) Routes.CATALOG else Routes.LOGIN) {
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
                        onPreview = if (BuildConfig.DEBUG) ({ container.resetPreview(); preview = true }) else null)
                }
                composable(Routes.REGISTER) {
                    val factory = remember(container) {
                        viewModelFactory { initializer { AuthViewModel(container.authRepository, container.healthRepository) } }
                    }
                    val vm: AuthViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    RegisterScreen(state, vm::register, onBack = { nav.popBackStack() })
                }
            } else {
                composable(Routes.CATALOG) {
                    val factory = remember(container, isPreview) {
                        viewModelFactory { initializer { CatalogoViewModel(container.catalogo(isPreview)) } }
                    }
                    val vm: CatalogoViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    CatalogoScreen(state, isPreview, vm::edit, vm::applyFilters, vm::clearFilters,
                        vm::changePage, vm::retry,
                        onDetail = { nav.navigate("mascota/$it") { launchSingleTop = true } },
                        onProfile = { nav.navigate(Routes.PROFILE) { launchSingleTop = true } },
                        onExit = {
                            if (isPreview) { container.resetPreview(); preview = false } else container.session.clear()
                        })
                }
                composable(Routes.PROFILE) {
                    val factory = remember(container, isPreview) {
                        viewModelFactory { initializer { PerfilViewModel(container.perfil(isPreview), isPreview) } }
                    }
                    val vm: PerfilViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    CuestionarioScreen(state, isPreview, vm::edit, vm::save, vm::load, onBack = { nav.popBackStack() })
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
