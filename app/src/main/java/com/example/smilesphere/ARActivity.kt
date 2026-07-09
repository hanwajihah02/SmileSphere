package com.example.smilesphere

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import kotlin.math.abs
import kotlin.math.pow

class ARActivity : BaseActivity() {

    private lateinit var arSceneView: ARSceneView
    private lateinit var tvStatus: TextView
    private lateinit var cardToothInfo: LinearLayout
    private lateinit var tvToothPart: TextView
    private lateinit var tvToothFunction: TextView
    private lateinit var tvPartIndex: TextView
    private lateinit var btnPrevPart: Button
    private lateinit var btnNextPart: Button
    private lateinit var btnClosePart: Button
    private lateinit var btnDone: Button

    private lateinit var hotspotOverlay: HotspotOverlayView

    // progress label for sequenced lessons (lesson 3 brushing steps)
    private lateinit var tvProgress: TextView

    private var modelNode: AnchorNode? = null
    private var modelPlaced     = false
    private var currentZoneIndex = 0

    // Session info
    private var school     = ""
    private var date       = ""
    private var sessionKey = ""
    private var lessonOrder = 1

    // ── Gesture tracking ──────────────────────────────────────────
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var doubleTapGestureDetector: android.view.GestureDetector
    private var currentScale    = 0.70f
    private val minScale        = 0.30f
    private val maxScale        = 1.00f
    private val zoomSensitivity = 0.55f
    private var lastTouchX      = 0f
    private var isDragging      = false
    private var isMultiTouchGesture = false
    private val dragThresholdPx = 12f
    // ──────────────────────────────────────────────────────────────

    private var activeHotspots: List<HotspotDef> = emptyList()


    data class HotspotDef(
        val label: String,
        val offsetX: Float,
        val offsetY: Float,
        val offsetZ: Float = 0f,
        val colorHex: String? = null
    )

    // ── sequenced-lesson state (lesson 3 = brushing technique) ─────
    private var isSequencedLesson = false
    private var currentStep = 1
    private val lesson3StepOrder = listOf("Step1", "Step2", "Step3", "Step4", "Step5")
    private val totalSteps = lesson3StepOrder.size
    // ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar)
        setupNavigation()

        school      = intent.getStringExtra("school")      ?: ""
        date        = intent.getStringExtra("date")        ?: ""
        sessionKey  = intent.getStringExtra("sessionKey")  ?: ""
        lessonOrder = intent.getIntExtra("lessonOrder", 1)

        arSceneView   = findViewById(R.id.arSceneView)
        tvStatus      = findViewById(R.id.tvStatus)
        cardToothInfo = findViewById(R.id.cardToothInfo)
        tvToothPart   = findViewById(R.id.tvToothPart)
        tvToothFunction = findViewById(R.id.tvToothFunction)
        tvPartIndex   = findViewById(R.id.tvPartIndex)
        btnPrevPart   = findViewById(R.id.btnPrevPart)
        btnNextPart   = findViewById(R.id.btnNextPart)
        btnClosePart  = findViewById(R.id.btnClosePart)
        btnDone       = findViewById(R.id.btnDone)

        hotspotOverlay = findViewById(R.id.hotspotOverlay)
        tvProgress      = findViewById(R.id.tvProgress)

        // lesson 3 uses sequenced tap-in-order brushing steps
        isSequencedLesson = (lessonOrder == 3)
        if (isSequencedLesson) {
            tvProgress.visibility = View.VISIBLE
            tvProgress.text = "Langkah $currentStep/$totalSteps"
        } else {
            tvProgress.visibility = View.GONE
        }

        arSceneView.sessionConfiguration = { _, config ->
            config.lightEstimationMode =
                Config.LightEstimationMode.ENVIRONMENTAL_HDR
        }

        arSceneView.onSessionUpdated = { _, frame ->
            runOnUiThread {
                if (!modelPlaced) {
                    tvStatus.text =
                        if (frame.camera.trackingState == TrackingState.TRACKING)
                            "Camera ready! Tap on a surface to place model"
                        else
                            "Move your phone to find a flat surface"
                } else {
                    updateHotspotPositions(frame)
                }
            }
        }

        // ── Pinch-to-zoom setup ───────────────────────────────────
        scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    if (!modelPlaced) return false

                    val adjustedScaleFactor = detector.scaleFactor.pow(zoomSensitivity)
                    currentScale = (currentScale * adjustedScaleFactor)
                        .coerceIn(minScale, maxScale)
                    Log.d(
                        "ZOOM_DEBUG",
                        "scaleFactor=${detector.scaleFactor} adjusted=$adjustedScaleFactor -> currentScale=$currentScale"
                    )

                    val toothNode =
                        modelNode?.childNodes?.firstOrNull() as? ModelNode
                    toothNode?.scale =
                        Scale(currentScale, currentScale, currentScale)

                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {}
            }
        )
        // ──────────────────────────────────────────────────────────

        // ── Touch listener: pinch + drag-rotate + tap ─────────────
        doubleTapGestureDetector = android.view.GestureDetector(
            this,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (!modelPlaced) return false

                    currentScale = 0.70f
                    val toothNode = modelNode?.childNodes?.firstOrNull() as? ModelNode
                    toothNode?.scale = Scale(currentScale, currentScale, currentScale)

                    Toast.makeText(this@ARActivity, "Zoom reset", Toast.LENGTH_SHORT).show()
                    return true
                }
            }
        )

        arSceneView.setOnTouchListener { _, event ->
            Log.d("ZOOM_DEBUG", "action=${event.action} pointerCount=${event.pointerCount}")

            scaleGestureDetector.onTouchEvent(event)
            doubleTapGestureDetector.onTouchEvent(event)

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    isDragging = false
                    isMultiTouchGesture = false
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    isMultiTouchGesture = true
                    isDragging = true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (modelPlaced && event.pointerCount == 1 && !isMultiTouchGesture) {
                        val deltaX = event.x - lastTouchX
                        if (abs(deltaX) > dragThresholdPx) {
                            isDragging = true
                            val toothNode =
                                modelNode?.childNodes?.firstOrNull() as? ModelNode
                            toothNode?.let {
                                val newY = it.rotation.y + (deltaX * 0.5f)
                                it.rotation = it.rotation.copy(y = newY)
                            }
                            lastTouchX = event.x
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (!modelPlaced) {
                        placeModel(event)
                    } else if (!isDragging && !isMultiTouchGesture) {
                        handleHotspotTap(event.x, event.y)
                    }
                    isDragging = false
                    isMultiTouchGesture = false
                }

                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    isMultiTouchGesture = false
                }
            }
            true
        }
        // ──────────────────────────────────────────────────────────

        setupButtons()
    }

    // branches on isSequencedLesson to enforce step order for lesson 3
    private fun handleHotspotTap(x: Float, y: Float) {
        val tappedLabel = hotspotOverlay.findTappedDot(x, y)

        if (tappedLabel == null) {
            tvStatus.text = "Tap a glowing dot to learn about that part"
            return
        }

        if (isSequencedLesson) {
            val tappedIndex = lesson3StepOrder.indexOf(tappedLabel) // 0-based
            val expectedIndex = currentStep - 1

            if (tappedIndex == -1) {
                // shouldn't happen for lesson 3, but guard anyway
                tvStatus.text = "Tap a glowing dot to learn about that part"
                return
            }

            if (tappedIndex == expectedIndex) {
                showZoneInfo(tappedLabel)
                currentStep++
                if (currentStep > totalSteps) {
                    tvProgress.text = "Selesai! 🎉"
                    tvStatus.text   = "Teknik memberus selesai!"
                    Toast.makeText(this, "Teknik memberus selesai! 🎉", Toast.LENGTH_LONG).show()
                } else {
                    tvProgress.text = "Langkah $currentStep/$totalSteps"
                }
                // refresh dot highlight to point at the new expected step
                hotspotOverlay.updateDots(
                    hotspotOverlay.currentDots,
                    expectedLabel = lesson3StepOrder.getOrNull(currentStep - 1)
                )
            } else {
                Toast.makeText(this, "Cuba ikut urutan yang betul 🙂", Toast.LENGTH_SHORT).show()
            }
        } else {
            // free-tap behavior for lessons 1, 2, 4, 5, 6
            showZoneInfo(tappedLabel)
        }
    }

    // shared helper — pulls description from getZonesForLesson() and shows the card
    private fun showZoneInfo(label: String) {
        val zone = getZonesForLesson(lessonOrder).find { it.first == label }
        if (zone != null) {
            tvToothPart.text        = zone.second
            tvToothFunction.text    = zone.third
            tvPartIndex.text        = ""
            cardToothInfo.visibility = View.VISIBLE
            tvStatus.text           = "Exploring: ${zone.first}"
        }
    }


    private fun updateHotspotPositions(frame: com.google.ar.core.Frame) {
        val anchor = modelNode ?: return
        val anchorPose = anchor.anchor?.pose ?: return
        val toothNode = anchor.childNodes.firstOrNull() as? ModelNode


        val baselineScale = 0.10f
        val modelScale = toothNode?.scale?.x ?: baselineScale
        val scaleFactor = modelScale / baselineScale

        val rotationYDeg = toothNode?.rotation?.y ?: 0f
        val rotationYRad = Math.toRadians(rotationYDeg.toDouble())
        val cosY = kotlin.math.cos(rotationYRad).toFloat()
        val sinY = kotlin.math.sin(rotationYRad).toFloat()

        val projectionMatrix = FloatArray(16)
        val viewMatrix       = FloatArray(16)
        frame.camera.getProjectionMatrix(projectionMatrix, 0, 0.01f, 100f)
        frame.camera.getViewMatrix(viewMatrix, 0)

        val newDots = mutableListOf<HotspotOverlayView.ScreenDot>()

        activeHotspots.forEach { hotspot ->
            // Scale the offset to match current pinch-zoom level
            val sx = hotspot.offsetX * scaleFactor
            val sy = hotspot.offsetY * scaleFactor
            val sz = hotspot.offsetZ * scaleFactor

            // Rotate the offset around Y to match current drag-rotation
            val rx = sx * cosY + sz * sinY
            val rz = -sx * sinY + sz * cosY

            // Transform through the anchor's full pose (rotation + position), not just
// its position. This makes dots land correctly no matter which direction
// you were facing when you tapped to place the model.
            val localOffset = floatArrayOf(rx, sy, rz)
            val worldPos = anchorPose.transformPoint(localOffset)
            val worldPoint = floatArrayOf(worldPos[0], worldPos[1], worldPos[2], 1f)

            val viewPoint = FloatArray(4)
            val clipPoint = FloatArray(4)
            android.opengl.Matrix.multiplyMV(viewPoint, 0, viewMatrix,       0, worldPoint, 0)
            android.opengl.Matrix.multiplyMV(clipPoint, 0, projectionMatrix, 0, viewPoint,  0)

            if (clipPoint[3] <= 0f) return@forEach

            val ndcX = clipPoint[0] / clipPoint[3]
            val ndcY = clipPoint[1] / clipPoint[3]

            val screenX = (ndcX + 1f) / 2f * arSceneView.width
            val screenY = (1f - ndcY) / 2f * arSceneView.height

            if (screenX < 0 || screenX > arSceneView.width  ||
                screenY < 0 || screenY > arSceneView.height) return@forEach

            newDots.add(
                HotspotOverlayView.ScreenDot(
                    label = hotspot.label,
                    x = screenX,
                    y = screenY,
                    colorHex = hotspot.colorHex
                )
            )
        }

        val expectedLabel = if (isSequencedLesson) lesson3StepOrder.getOrNull(currentStep - 1) else null
        hotspotOverlay.updateDots(newDots, expectedLabel)
    }


    private fun getHotspotOffsetsForLesson(order: Int): List<HotspotDef> = when (order) {
        1 -> listOf(
            HotspotDef("Crown",    offsetX =  0.00f, offsetY =  0.06f),
            HotspotDef("Enamel",   offsetX =  0.03f, offsetY =  0.04f),
            HotspotDef("Dentin",   offsetX = -0.03f, offsetY =  0.02f),
            HotspotDef("Pulp",     offsetX =  0.00f, offsetY =  0.01f),
            HotspotDef("Root",     offsetX =  0.00f, offsetY = -0.05f),
            HotspotDef("Gum",      offsetX =  0.03f, offsetY = -0.01f)
        )
        2 -> listOf(
            HotspotDef("Incisor",  offsetX = -0.017000003f, offsetY = -0.013000001f, offsetZ = 0.006f),
            HotspotDef("Canine",   offsetX = -0.01f, offsetY = -0.011f, offsetZ = 0.01f),
            HotspotDef("Premolar", offsetX = 0.0069999993f, offsetY = 9.999998E-4f, offsetZ = 0.0020000003f),
            HotspotDef("Molar",    offsetX = 0.017f, offsetY = -0.011000001f, offsetZ = -0.006f)

        )
        3 -> listOf(
            HotspotDef("Step1",    offsetX =  0.04f, offsetY =  0.05f),
            HotspotDef("Step2",    offsetX = -0.04f, offsetY =  0.03f),
            HotspotDef("Step3",    offsetX =  0.04f, offsetY =  0.00f),
            HotspotDef("Step4",    offsetX = -0.04f, offsetY = -0.03f),
            HotspotDef("Step5",    offsetX =  0.04f, offsetY = -0.05f)
        )
        4 -> listOf(
            HotspotDef("Plaque",      offsetX =  -2.3283064E-10f, offsetY =  -0.0039999997f, offsetZ = -0.001f, colorHex ="#E8D44D"),
            HotspotDef("Cavity",      offsetX = -0.01f, offsetY =  -0.011f, offsetZ = 0.0f, colorHex = "#5C3A21"),
            HotspotDef("Fluoride",    offsetX = 0.013f, offsetY = 0.008000001f, offsetZ = 0.003f, colorHex = "#2E9AA5")

        )
        5 -> listOf(
            HotspotDef("Gingivitis",   offsetX =  -0.017000003f, offsetY =  -0.013000001f, colorHex = "#D9534F"),
            HotspotDef("Signs",        offsetX = -0.01f, offsetY =  -0.011f, colorHex = "#E8842E"),
            HotspotDef("Scaling",      offsetX =  0.0069999993f, offsetY =  9.999998E-4f, colorHex = "#2E9AA5")

        )
        else -> listOf(
            HotspotDef("SDS",         offsetX =  0.00f, offsetY =  0.04f),
            HotspotDef("Services",    offsetX =  0.04f, offsetY =  0.00f),
            HotspotDef("Prevention",  offsetX = -0.04f, offsetY = -0.04f)
        )
    }

    private fun getModelForLesson(order: Int): String {
        return when (order) {
            1    -> "AR/tooth.glb"
            2    -> "AR/teeth.glb"
            3    -> "AR/teeth.glb"
            4    -> "AR/teeth.glb"
            5    -> "AR/teeth.glb"
            6    -> "AR/tooth.glb"
            else -> "AR/tooth.glb"
        }
    }

    private fun getZonesForLesson(order: Int):
            List<Triple<String, String, String>> = when (order) {
        1 -> listOf(
            Triple("Crown","Crown (Korona)",
                "Bahagian gigi yang kelihatan di atas gusi. Dilindungi enamel — bahan paling keras dalam badan manusia."),
            Triple("Enamel","Enamel",
                "Lapisan luar paling keras (96% mineral). Melindungi gigi daripada serangan asid bakteria."),
            Triple("Dentin","Dentin",
                "Lapisan di bawah enamel. Mengandungi tiub kecil yang menyebabkan sensitiviti gigi."),
            Triple("Pulp","Pulpa",
                "Tisu lembut mengandungi saraf dan salur darah. Jangkitan pulpa menyebabkan sakit gigi."),
            Triple("Root","Akar Gigi (Root)",
                "Mengikat gigi ke tulang rahang. Gigi molar boleh ada sehingga 3 akar."),
            Triple("Gum","Gusi (Gum)",
                "Tisu merah jambu melindungi pangkal gigi. Gusi berdarah = tanda awal gingivitis.")
        )
        2 -> listOf(
            Triple("Incisor","Gigi Insisif",
                "8 gigi di hadapan. Digunakan untuk MEMOTONG makanan. Kanak-kanak ada 8 gigi insisif susu."),
            Triple("Canine","Gigi Kanin",
                "4 gigi tajam berbentuk taring. Digunakan untuk MENGOYAK makanan yang keras."),
            Triple("Premolar","Gigi Premolar",
                "8 gigi antara kanin dan molar. Digunakan untuk MENGHANCURKAN makanan."),
            Triple("Molar","Gigi Molar",
                "12 gigi besar di belakang. Digunakan untuk MENGISAR makanan sebelum ditelan.")
        )
        3 -> listOf(
            Triple("Step1","Langkah 1: Sudut 45°",
                "Letakkan berus pada sudut 45° ke arah garis gusi."),
            Triple("Step2","Langkah 2: Gerakan Bulat",
                "Gerakkan berus dengan gerakan bulat kecil yang lembut."),
            Triple("Step3","Langkah 3: 2 Minit",
                "Berus selama 2 minit. 12.7% remaja Malaysia tidak berus cukup lama."),
            Triple("Step4","Langkah 4: Semua Permukaan",
                "Berus permukaan luar, dalam dan kunyah gigi."),
            Triple("Step5","Langkah 5: Tukar Berus",
                "Tukar berus setiap 3 bulan. Guna ubat gigi fluorida.")
        )
        4 -> listOf(
            Triple("Plaque","Plak Gigi",
                "Lapisan bakteria yang menghasilkan asid dan merosakkan enamel."),
            Triple("Cavity","Karies (Lubang Gigi)",
                "71.3% kanak-kanak prasekolah Malaysia ada karies (NOHPS 2015)."),
            Triple("Sugar","Bahaya Gula",
                "Kurangkan minuman manis untuk melindungi gigi."),
            Triple("Fluoride","Perlindungan Fluorida",
                "Air paip Malaysia mengandungi fluorida 0.5 ppm untuk menguatkan enamel."),
            Triple("Prevention","Pencegahan",
                "Berus 2x sehari + floss + jumpa doktor gigi 6 bulan sekali.")
        )
        5 -> listOf(
            Triple("Gingivitis","Gingivitis",
                "96% kanak-kanak Orang Asli Malaysia ada gingivitis (BMC 2019)."),
            Triple("Signs","Tanda-tanda",
                "Gusi merah, bengkak dan berdarah semasa memberus gigi."),
            Triple("Scaling","Skaling Percuma SDS",
                "SDS beri rawatan skaling PERCUMA kepada semua murid sekolah kerajaan."),
            Triple("Prevention","Pencegahan",
                "Berus + floss + skaling 6 bulan sekali untuk gusi yang sihat.")
        )
        else -> listOf(
            Triple("SDS","Perkhidmatan SDS",
                "SDS percuma untuk murid Tahun 1 hingga Tingkatan 5."),
            Triple("Services","Perkhidmatan",
                "Pemeriksaan, tampalan, cabutan, skaling, varnis fluorida."),
            Triple("Prevention","Pencegahan",
                "Amalan oral yang baik semasa kecil kekal sepanjang hayat.")
        )
    }




    private fun placeModel(event: MotionEvent) {
        try {
            val hitResult = arSceneView.hitTestAR(
                xPx          = event.x,
                yPx          = event.y,
                planeTypes   = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING),
                trackingStates = setOf(TrackingState.TRACKING)
            )

            if (hitResult == null) {
                tvStatus.text = "No flat surface found. Try tapping a detected plane."
                return
            }

            val modelPath     = getModelForLesson(lessonOrder)
            val modelInstance = arSceneView.modelLoader.createModelInstance(modelPath)
            val anchorNode    = AnchorNode(arSceneView.engine, hitResult.createAnchor())

            currentScale = 0.70f   // reset to baseline every time a model is placed

            val toothNode = ModelNode(modelInstance).apply {
                if (animationCount > 0) playAnimation(0, loop = true)
                scale = Scale(currentScale, currentScale, currentScale)
            }

            anchorNode.addChildNode(toothNode)

            try {
                val lightEntity = arSceneView.engine.entityManager.create()
                com.google.android.filament.LightManager
                    .Builder(com.google.android.filament.LightManager.Type.DIRECTIONAL)
                    .color(1.0f, 0.98f, 0.95f)
                    .intensity(100_000f)
                    .direction(0.5f, -1.0f, -0.5f)
                    .castShadows(false)
                    .build(arSceneView.engine, lightEntity)
                arSceneView.scene.addEntity(lightEntity)
            } catch (e: Exception) { }

            arSceneView.addChildNode(anchorNode)
            modelNode        = anchorNode
            modelPlaced      = true
            currentZoneIndex = 0

            activeHotspots = getHotspotOffsetsForLesson(lessonOrder)



            // reset sequencing state whenever a fresh model is placed
            if (isSequencedLesson) {
                currentStep = 1
                tvProgress.text = "Langkah $currentStep/$totalSteps"
            }

            tvStatus.text = if (isSequencedLesson)
                "Model placed! Tap dots in order to learn brushing steps"
            else
                "Model placed! Drag to rotate · Pinch to zoom · Tap dots to explore"

            Toast.makeText(this, "Model loaded!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            tvStatus.text = "Error: ${e.message}"
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateInfoCard() {
        val zones = getZonesForLesson(lessonOrder)
        val idx   = currentZoneIndex % zones.size
        val zone  = zones[idx]
        tvToothPart.text      = zone.second
        tvToothFunction.text  = zone.third
        tvPartIndex.text      = "${idx + 1} / ${zones.size}"
        btnPrevPart.isEnabled = idx > 0
        btnNextPart.isEnabled = idx < zones.size - 1
        tvStatus.text         = "Exploring: ${zone.first}"
    }

    private fun setupButtons() {
        btnClosePart.setOnClickListener {
            cardToothInfo.visibility = View.GONE
        }
        btnNextPart.setOnClickListener {
            val size = getZonesForLesson(lessonOrder).size
            if (currentZoneIndex < size - 1) {
                currentZoneIndex++
                updateInfoCard()
            }
        }
        btnPrevPart.setOnClickListener {
            if (currentZoneIndex > 0) {
                currentZoneIndex--
                updateInfoCard()
            }
        }
        btnDone.setOnClickListener { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        arSceneView.destroy()
    }
}
