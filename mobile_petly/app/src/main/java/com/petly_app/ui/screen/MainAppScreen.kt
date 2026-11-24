package com.petly_app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petly_app.R
import com.petly_app.ui.asistente.*
import com.petly_app.data.model.CatalogoFilters
import com.petly_app.data.model.PerfilDraft
import com.petly_app.ui.auth.AccessIcons
import com.petly_app.ui.catalogo.CatalogoScreen
import com.petly_app.ui.catalogo.CatalogoUiState
import com.petly_app.ui.perfil.CuestionarioScreen
import com.petly_app.ui.perfil.DisponibilidadUiState
import com.petly_app.ui.perfil.PerfilUiState
import com.petly_app.ui.perfil.NotificacionesState
import com.petly_app.data.remote.MensajeriaApi
import com.petly_app.data.repository.HistoriasAdopcionRepository
import com.petly_app.ui.mensajes.MensajesScreen

enum class PetlyTab { INICIO, CATALOGO, NOVEDADES, MENSAJES, PERFIL }

@Composable
fun MainAppScreen(
    selectedTab: PetlyTab,
    onSelectTab: (PetlyTab) -> Unit,
    snackbarHostState: SnackbarHostState,
    catalogoState: CatalogoUiState,
    perfilState: PerfilUiState,
    disponibilidadState: DisponibilidadUiState = DisponibilidadUiState(),
    isPreview: Boolean,
    onEditFilters: (CatalogoFilters) -> Unit,
    onApplyFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onChangePage: (Int) -> Unit,
    onRetryCatalog: () -> Unit,
    onDetailPet: (Long) -> Unit,
    onEditPerfil: (PerfilDraft) -> Unit,
    onSavePerfil: (Boolean) -> Unit,
    onLoadPerfil: () -> Unit,
    onRefreshProfile: () -> Unit = {},
    onAddFranja: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onRemoveFranja: (Long) -> Unit = {},
    onSaveDisponibilidad: () -> Unit = {},
    onShareAutorizaciones: (() -> Unit)?,
    onOpenKyc: () -> Unit,
    onExit: () -> Unit,
    chatState: ChatUiState = ChatUiState(),
    onOpenChat: (Boolean) -> Unit = {},
    onEditChat: (String) -> Unit = {},
    onSendChat: () -> Unit = {},
    onRetryChat: () -> Unit = {},
    onEditFailedChat: () -> Unit = {},
    onClearChat: () -> Unit = {},
    notificacionesState: NotificacionesState = NotificacionesState(),
    onOpenNotifications: () -> Unit = {},
    onOpenSeguimientos: () -> Unit = {},
    mensajeriaApi: MensajeriaApi? = null,
    unreadMessages: Int = 0,
    onMessagesRead: () -> Unit = {},
    contentResolver: android.content.ContentResolver? = null,
    solicitudesChat: List<Triple<Long, Long, String>> = emptyList(),
    onOpenAgendaVacunas: ((Long) -> Unit)? = null,
    historiasRepository: HistoriasAdopcionRepository? = null,
    reportReminders: @Composable () -> Unit = {}
) {
    ChatPanel(chatState, onOpenChat, onEditChat, onSendChat, onRetryChat, onEditFailedChat, onClearChat)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { if (!isPreview && !chatState.open) ChatButton { onOpenChat(true) } },
        bottomBar = {
            PetlyBottomNavigationBar(
                selectedTab = selectedTab,
                unreadNotifications = notificacionesState.noLeidas,
                unreadMessages = unreadMessages,
                onSelectTab = onSelectTab
            )
        },
        containerColor = ScreenMintBackground
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                PetlyTab.INICIO -> {
                    HomeScreen(
                        onNavigateCatalog = { especie ->
                            if (especie != null) {
                                onEditFilters(catalogoState.filters.copy(especie = especie))
                                onApplyFilters()
                            }
                            onSelectTab(PetlyTab.CATALOGO)
                        },
                        onNavigateProfile = { onSelectTab(PetlyTab.PERFIL) },
                        onNavigateDetail = onDetailPet
                    )
                }
                PetlyTab.CATALOGO -> {
                    CatalogoScreen(
                        state = catalogoState,
                        preview = isPreview,
                        onEdit = onEditFilters,
                        onApply = onApplyFilters,
                        onClear = onClearFilters,
                        onPage = onChangePage,
                        onRetry = onRetryCatalog,
                        onDetail = onDetailPet,
                        onProfile = { onSelectTab(PetlyTab.PERFIL) },
                        onExit = onExit
                    )
                }
                PetlyTab.NOVEDADES -> {
                    NovedadesScreen(onOpenNotifications, notificacionesState.noLeidas)
                }
                PetlyTab.MENSAJES -> {
                    if (mensajeriaApi != null && contentResolver != null) {
                        MensajesScreen(mensajeriaApi, contentResolver, solicitudesChat, onMessagesRead)
                    } else {
                        Text("Inicia sesión para ver tus conversaciones.")
                    }
                }
                PetlyTab.PERFIL -> {
                    CuestionarioScreen(
                        state = perfilState,
                        onRefreshProfile = onRefreshProfile,
                        disponibilidadState = disponibilidadState,
                        preview = isPreview,
                        onEdit = onEditPerfil,
                        onSave = onSavePerfil,
                        onRetry = onLoadPerfil,
                        onBack = { onSelectTab(PetlyTab.INICIO) },
                        onShare = onShareAutorizaciones,
                        onOpenKyc = onOpenKyc,
                        onOpenNotifications = onOpenNotifications,
                        onOpenSeguimientos = onOpenSeguimientos,
                        onOpenAgendaVacunas = onOpenAgendaVacunas,
                        historiasRepository = historiasRepository,
                        reportReminders = reportReminders,
                        onDetailPet = onDetailPet,
                        onAddFranja = onAddFranja,
                        onRemoveFranja = onRemoveFranja,
                        onSaveDisponibilidad = onSaveDisponibilidad,
                        onExit = onExit
                    )
                }
            }
        }
    }
}

@Composable
fun PetlyBottomNavigationBar(
    selectedTab: PetlyTab,
    unreadNotifications: Int = 0,
    unreadMessages: Int = 0,
    onSelectTab: (PetlyTab) -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                label = "Inicio",
                icon = if (selectedTab == PetlyTab.INICIO) HomeIcons.HomeFilled else HomeIcons.HomeOutlined,
                isSelected = selectedTab == PetlyTab.INICIO,
                onClick = { onSelectTab(PetlyTab.INICIO) }
            )
            BottomNavItem(
                label = "Catálogo",
                icon = HomeIcons.Cat,
                isSelected = selectedTab == PetlyTab.CATALOGO,
                onClick = { onSelectTab(PetlyTab.CATALOGO) }
            )
            BottomNavItem(
                label = "Novedades",
                icon = HomeIcons.Bell,
                isSelected = selectedTab == PetlyTab.NOVEDADES,
                unreadCount = unreadNotifications,
                unreadPlural = R.plurals.avisos_sin_leer,
                onClick = { onSelectTab(PetlyTab.NOVEDADES) }
            )
            BottomNavItem(
                label = "Mensajes",
                icon = HomeIcons.Chat,
                isSelected = selectedTab == PetlyTab.MENSAJES,
                unreadCount = unreadMessages,
                unreadPlural = R.plurals.mensajes_sin_leer,
                onClick = { onSelectTab(PetlyTab.MENSAJES) }
            )
            BottomNavItem(
                label = "Perfil",
                icon = AccessIcons.Person,
                isSelected = selectedTab == PetlyTab.PERFIL,
                onClick = { onSelectTab(PetlyTab.PERFIL) }
            )
        }
    }
}

@Composable
private fun RowScope.BottomNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    unreadCount: Int = 0,
    @androidx.annotation.PluralsRes unreadPlural: Int = R.plurals.avisos_sin_leer,
    onClick: () -> Unit
) {
    val unreadDescription = if (unreadCount > 0) {
        pluralStringResource(unreadPlural, unreadCount, unreadCount)
    } else {
        null
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                selected = isSelected
                if (unreadDescription != null) stateDescription = unreadDescription
            }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BadgedBox(badge = {
            if (unreadCount > 0) {
                Badge(Modifier.clearAndSetSemantics {}) {
                    Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                }
            }
        }) {
            Surface(
                shape = CircleShape,
                color = if (isSelected) Color(0xFFC8E8DA) else Color.Transparent
            ) {
                Box(Modifier.padding(horizontal = if (isSelected) 14.dp else 0.dp, vertical = 4.dp)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) PetlyDarkGreen else Color(0xFF6B7D73),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) PetlyDarkGreen else Color(0xFF6B7D73)
        )
    }
}
