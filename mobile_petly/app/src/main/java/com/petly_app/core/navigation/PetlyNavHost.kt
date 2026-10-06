package com.petly_app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.petly_app.core.AppContainer
import com.petly_app.ui.auth.AuthViewModel
import com.petly_app.ui.auth.LoginScreen
import com.petly_app.ui.auth.RegisterScreen
import com.petly_app.ui.catalogo.CatalogoScreen
import com.petly_app.ui.catalogo.CatalogoViewModel

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CATALOG = "catalogo"
}

@Composable
fun PetlyNavHost(container: AppContainer) {
    val authenticated by container.session.authenticated.collectAsStateWithLifecycle()
    // Al entrar o salir, reemplazar el grafo evita volver a pantallas privadas con Atrás.
    key(authenticated) {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = if (authenticated) Routes.CATALOG else Routes.LOGIN
        ) {
            if (!authenticated) {
                composable(Routes.LOGIN) {
                    val factory = remember(container) {
                        viewModelFactory {
                            initializer { AuthViewModel(container.authRepository, container.healthRepository) }
                        }
                    }
                    val vm: AuthViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    LoginScreen(
                        state = state,
                        onLogin = vm::login,
                        onRegister = {
                            vm.clearFormState()
                            navController.navigate(Routes.REGISTER) { launchSingleTop = true }
                        },
                        onCheckConnection = vm::checkConnection
                    )
                }
                composable(Routes.REGISTER) {
                    val factory = remember(container) {
                        viewModelFactory {
                            initializer { AuthViewModel(container.authRepository, container.healthRepository) }
                        }
                    }
                    val vm: AuthViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    RegisterScreen(
                        state = state,
                        onRegister = vm::register,
                        onBack = { navController.popBackStack() }
                    )
                }
            } else {
                composable(Routes.CATALOG) {
                    val factory = remember(container) {
                        viewModelFactory { initializer { CatalogoViewModel(container.usuarioRepository) } }
                    }
                    val vm: CatalogoViewModel = viewModel(factory = factory)
                    val state by vm.state.collectAsStateWithLifecycle()
                    CatalogoScreen(state, vm::loadUser, vm::logout)
                }
            }
        }
    }
}
