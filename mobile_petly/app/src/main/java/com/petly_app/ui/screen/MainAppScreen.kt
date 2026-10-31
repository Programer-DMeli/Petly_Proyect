package com.petly_app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petly_app.data.model.CatalogoFilters
import com.petly_app.data.model.PerfilDraft
import com.petly_app.ui.auth.AccessIcons
import com.petly_app.ui.catalogo.CatalogoScreen
import com.petly_app.ui.catalogo.CatalogoUiState
import com.petly_app.ui.perfil.CuestionarioScreen
import com.petly_app.ui.perfil.DisponibilidadUiState
import com.petly_app.ui.perfil.PerfilUiState

enum class PetlyTab { INICIO, CATALOGO, NOVEDADES, PERFIL }

@Composable
fun MainAppScreen(
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
    onAddFranja: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onRemoveFranja: (Long) -> Unit = {},
    onSaveDisponibilidad: () -> Unit = {},
    onShareAutorizaciones: (() -> Unit)?,
    onOpenKyc: () -> Unit,
    onExit: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(PetlyTab.INICIO) }

    Scaffold(
        bottomBar = {
            PetlyBottomNavigationBar(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
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
                            selectedTab = PetlyTab.CATALOGO
                        },
                        onNavigateProfile = { selectedTab = PetlyTab.PERFIL },
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
                        onProfile = { selectedTab = PetlyTab.PERFIL },
                        onExit = onExit
                    )
                }
                PetlyTab.NOVEDADES -> {
                    NovedadesScreen()
                }
                PetlyTab.PERFIL -> {
                    CuestionarioScreen(
                        state = perfilState,
                        disponibilidadState = disponibilidadState,
                        preview = isPreview,
                        onEdit = onEditPerfil,
                        onSave = onSavePerfil,
                        onRetry = onLoadPerfil,
                        onBack = { selectedTab = PetlyTab.INICIO },
                        onShare = onShareAutorizaciones,
                        onOpenKyc = onOpenKyc,
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
                onClick = { onSelectTab(PetlyTab.NOVEDADES) }
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
private fun BottomNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isSelected) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFC8E8DA)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = PetlyDarkGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF6B7D73),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) PetlyDarkGreen else Color(0xFF6B7D73)
        )
    }
}
