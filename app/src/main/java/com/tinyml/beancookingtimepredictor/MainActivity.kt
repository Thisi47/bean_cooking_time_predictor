package com.tinyml.beancookingtimepredictor


import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tinyml.beancookingtimepredictor.ui.theme.PredicteurTempsCuissonTheme


val RMSE = 26
val MAE = 16.40

class MainActivity : ComponentActivity() {
    private lateinit var introManager: IntroManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        introManager = IntroManager(this)

        setContent {
            PredicteurTempsCuissonTheme {
                val navController = rememberNavController()
                val showIntro = remember { mutableStateOf(introManager.shouldShowIntro()) }

                if (showIntro.value) {
                    IntroScreen(onDismiss = { dontShowAgain ->
                        if (dontShowAgain) {
                            introManager.setShowIntro(false)
                        }
                        showIntro.value = false
                    })
                } else {
                    NavHost(navController, startDestination = "prediction") {
                        composable("prediction") { PredictionScreen(navController) }
                        composable("info") { InfoScreen(navController) }
                    }
                }
            }
        }
    }
}

@Composable
fun IntroScreen(onDismiss: (Boolean) -> Unit) {
    var dontShowAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss(dontShowAgain) },
        title = { Text("Bienvenue !") },
        text = {
            Column {
                Text("Prenez une photo de votre haricot et obtenez instantanément le temps de cuisson recommandé.")
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Text("Ne plus afficher")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onDismiss(dontShowAgain) }) {
                Text("Compris")
            }
        }
    )
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel = viewModel()
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imageBitmap by mainViewModel.imageBitmap
    val predictionResult by mainViewModel.predictionResult


    // Launcher pour la galerie
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = if (Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, it)
                ImageDecoder.decodeBitmap(source)
            }
            mainViewModel.runPrediction(context, bitmap)
        }
    }

    // Launcher pour la caméra
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            mainViewModel.runPrediction(context, it)
        }
    }

    // Permission caméra
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launch(null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prédicteur du Temps de Cuisson") },
                actions = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Plus d'info") },
                            onClick = {
                                expanded = false
                                navController.navigate("info")
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
//            // --- Titre principal ---
//            Text(
//                text = "Prédicteur du Temps de Cuisson",
//                style = MaterialTheme.typography.headlineMedium,
//                fontWeight = FontWeight.Bold
//            )

            // --- Zone d'image ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap!!.asImageBitmap(),
                        contentDescription = "Image à analyser",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = "Veuillez sélectionner une image pour commencer.",
                        textAlign = TextAlign.Center
                    )
                }
            }

            // --- Résultat prédiction ---
            if (predictionResult != null) {
                Text(
                    text = predictionResult!!,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "RMSE : $RMSE min\nMAE : $MAE min",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // --- Boutons Importer & Prendre Photo ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Importer")
                }
                Button(
                    onClick = {
                        when (PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                                cameraLauncher.launch(null)
                            }
                            else -> {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Prendre Photo")
                }
            }

            // --- Bouton Réinitialiser ---
            Button(
                onClick = { mainViewModel.reset() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                enabled = imageBitmap != null
            ) {
                Text("Réinitialiser")
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Informations") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "À propos de l'application",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text("Cette application prédit le temps de cuisson des haricots en utilisant des modèles TinyML optimisés pour mobile.")

            Spacer(Modifier.height(16.dp))
            Text(
                "Interprétation des métriques",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            Text("• MAE (Mean Absolute Error) : moyenne des erreurs absolues entre les prédictions et les valeurs réelles. Plus il est faible, plus les prédictions sont proches de la réalité.")
            Spacer(Modifier.height(8.dp))
            Text("• RMSE (Root Mean Squared Error) : met plus de poids sur les grandes erreurs. Utile pour voir si certaines prédictions sont très éloignées.")

            Spacer(Modifier.height(16.dp))
            Text(
                "Dans le contexte de la cuisson des haricots :\n" +
                        "- Un MAE de $MAE minutes signifie qu’en moyenne le modèle se trompe de ±$MAE minutes.\n" +
                        "- Un RMSE ($RMSE minutes) plus élevé que le MAE indique qu’il existe quelques prédictions avec de grandes erreurs."
            )
        }
    }
}
