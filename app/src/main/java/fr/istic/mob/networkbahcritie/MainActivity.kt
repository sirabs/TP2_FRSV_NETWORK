package fr.istic.mob.networkbahcritie

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import fr.istic.mob.networkbahcritie.ui.GraphView
import fr.istic.mob.networkbahcritie.viewmodel.EditMode
import fr.istic.mob.networkbahcritie.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import android.widget.Toast
import android.graphics.Bitmap
import java.io.File

class MainActivity : AppCompatActivity() {

    private var selectedConnectionId: String? = null
    private val viewModel: MainViewModel by viewModels()
    private lateinit var graphView: GraphView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        graphView = findViewById(R.id.graphView)
        setupTouchListener()

        lifecycleScope.launch {
            viewModel.graph.collect { graph ->
                graphView.setGraph(graph)
            }
        }

        lifecycleScope.launch {
            viewModel.addObjectDialogState.collect { state ->
                if (state.show) {
                    showAddObjectDialog()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.tempConnection.collect { coords ->
                graphView.tempConnectionCoords = coords
                graphView.invalidate()
            }
        }

        lifecycleScope.launch {
            viewModel.selectedPlan.collect { planResId ->
                if (planResId != 0) {
                    val bitmap = BitmapFactory.decodeResource(resources, planResId)
                    if (bitmap != null) {
                        graphView.setFloorPlan(bitmap)
                        viewModel.setViewBounds(bitmap.width.toFloat(), bitmap.height.toFloat())
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.connectionError.collect { hasError ->
                if (hasError) {
                    Toast.makeText(this@MainActivity, getString(R.string.edge_exists), Toast.LENGTH_SHORT).show()
                    viewModel.clearConnectionError()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_reset -> { viewModel.resetNetwork(); true }
            R.id.menu_save -> { showSaveDialog(); true }
            R.id.menu_load -> { showLoadDialog(); true }
            R.id.menu_mode_add_node -> { viewModel.setMode(EditMode.ADD_NODE); true }
            R.id.menu_mode_add_edge -> { viewModel.setMode(EditMode.ADD_EDGE); true }
            R.id.menu_mode_edit -> { viewModel.setMode(EditMode.EDIT); true }
            R.id.menu_choose_plan -> { showPlanDialog(); true }
            R.id.menu_send_mail -> { sendNetworkByEmail(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val plans by lazy {
        listOf(
            Pair("${getString(R.string.plan)} 1", R.drawable.plan1),
            Pair("${getString(R.string.plan)} 2", R.drawable.plan2),
            Pair("${getString(R.string.plan)} 3", R.drawable.plan3)
        )
    }

    private val objectTypes by lazy {
        listOf(
            Pair(getString(R.string.type_thermostat), R.drawable.ic_thermostat),
            Pair(getString(R.string.type_camera),     R.drawable.ic_camera),
            Pair(getString(R.string.type_lock),       R.drawable.ic_serrure),
            Pair(getString(R.string.type_bulb),       R.drawable.ic_ampoule),
            Pair(getString(R.string.type_smoke),      R.drawable.ic_detecteur)
        )
    }

    private val colors by lazy {
        listOf(
            Pair(getString(R.string.color_red), android.graphics.Color.RED),
            Pair(getString(R.string.color_green), android.graphics.Color.GREEN),
            Pair(getString(R.string.color_blue), android.graphics.Color.BLUE),
            Pair(getString(R.string.color_orange), android.graphics.Color.parseColor("#FF8800")),
            Pair(getString(R.string.color_cyan), android.graphics.Color.CYAN),
            Pair(getString(R.string.color_magenta), android.graphics.Color.MAGENTA),
            Pair(getString(R.string.color_black), android.graphics.Color.BLACK)
        )
    }

    private fun showPlanDialog() {
        val names = plans.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_plan))
            .setItems(names) { _, which ->
                viewModel.selectPlan(plans[which].second)
            }
            .show()
    }

    private fun showSaveDialog() {
        val input = EditText(this).apply { hint = getString(R.string.save_hint) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_title))
            .setView(input)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val name = input.text.toString().ifBlank { "network_save" }
                viewModel.saveNetwork(name)
                Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showLoadDialog() {
        val names = viewModel.getSavedNetworkNames()
        if (names.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_save), Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.load_title))
            .setItems(names.toTypedArray()) { _, which ->
                val loaded = viewModel.loadNetwork(names[which])
                if (!loaded) Toast.makeText(this, getString(R.string.load_error), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    @SuppressLint("ClickableViewAccessibility")

    private fun setupTouchListener() {
        var isDragging = false
        var lastX = 0f
        var lastY = 0f

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (isDragging) return
                viewModel.unselectObject()
                when (viewModel.mode.value) {
                    EditMode.EDIT -> {
                        val obj = viewModel.findObjectAt(e.x, e.y)
                        if (obj != null) {
                            showObjectContextMenu(obj.id, obj.label, obj.color)
                        } else {
                            val conn = viewModel.findConnectionAt(e.x, e.y)
                            if (conn != null) {
                                showConnectionContextMenu(conn)
                            }
                        }
                    }
                    EditMode.ADD_NODE -> {
                        val conn = viewModel.findConnectionAt(e.x, e.y)
                        if (conn != null) {
                            showConnectionContextMenu(conn)
                        } else {
                            viewModel.onAddObjectRequested(e.x, e.y)
                        }
                    }
                    else -> {}
                }
            }
        })

        graphView.setOnTouchListener { _, event ->
            when (viewModel.mode.value) {
                EditMode.ADD_EDGE -> {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            viewModel.startConnection(event.x, event.y)
                            graphView.parent.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            viewModel.updateConnection(event.x, event.y)
                            graphView.invalidate()
                        }
                        MotionEvent.ACTION_UP -> {
                            graphView.parent.requestDisallowInterceptTouchEvent(false)
                            val result = viewModel.endConnection(event.x, event.y)
                            if (result != null) {
                                showAddEdgeDialog(result.first, result.second)
                            }
                            graphView.invalidate()
                        }
                    }
                }
                EditMode.ADD_NODE, EditMode.EDIT -> {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            isDragging = false
                            lastX = event.x
                            lastY = event.y
                            gestureDetector.onTouchEvent(event)
                            val obj = viewModel.findObjectAt(event.x, event.y)
                            if (obj != null) {
                                viewModel.selectObjectAt(event.x, event.y)
                                graphView.parent.requestDisallowInterceptTouchEvent(true)
                            } else {
                                val conn = viewModel.findConnectionAt(event.x, event.y)
                                if (conn != null) {
                                    selectedConnectionId = conn.id
                                    graphView.parent.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (viewModel.hasSelectedObject()) {
                                isDragging = true // ← marquer qu'on drague
                                graphView.parent.requestDisallowInterceptTouchEvent(true)
                                val dx = event.x - lastX
                                val dy = event.y - lastY
                                viewModel.moveSelectedObjectBy(dx, dy)
                                lastX = event.x
                                lastY = event.y
                                graphView.invalidate()
                            } else if (selectedConnectionId != null) {
                                isDragging = true
                                viewModel.updateConnectionCurvature(selectedConnectionId!!, event.x, event.y)
                                graphView.invalidate()
                            } else {
                                gestureDetector.onTouchEvent(event)
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            graphView.parent.requestDisallowInterceptTouchEvent(false)
                            viewModel.unselectObject()
                            selectedConnectionId = null
                            isDragging = false
                            if (!isDragging) {
                                gestureDetector.onTouchEvent(event)
                            }
                        }
                    }
                }
            }
            true
        }
    }

    private fun showAddObjectDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        val editText = EditText(this).apply { hint = getString(R.string.object_name) }
        layout.addView(android.widget.TextView(this).apply { text = getString(R.string.object_name) })
        layout.addView(editText)
        layout.addView(android.widget.TextView(this).apply { text = getString(R.string.object_type) })

        val spinner = android.widget.Spinner(this)
        spinner.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, objectTypes.map { it.first })
        layout.addView(spinner)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_object_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                val label = editText.text.toString().ifBlank { getString(R.string.object_default) }
                val iconRes = objectTypes[spinner.selectedItemPosition].second
                viewModel.addObject(label, iconRes)
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                viewModel.onAddObjectDialogDismiss()
            }
            .setOnDismissListener {
                viewModel.onAddObjectDialogDismiss()
            }
            .show()
    }

    private fun showAddEdgeDialog(fromId: String, toId: String) {
        if (!viewModel.canAddConnection(fromId, toId)) {
            Toast.makeText(this, getString(R.string.edge_exists), Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply { hint = getString(R.string.edge_label_hint) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_edge_title))
            .setView(input)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val label = input.text.toString()
                viewModel.addConnection(fromId, toId, label)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showObjectContextMenu(objectId: String, currentLabel: String, currentColor: Int) {
        val options = arrayOf(getString(R.string.edit), getString(R.string.delete))
        AlertDialog.Builder(this)
            .setTitle(currentLabel)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditObjectDialog(objectId, currentLabel, currentColor)
                    1 -> viewModel.removeObject(objectId)
                }
            }
            .show()
    }

    private fun showEditObjectDialog(objectId: String, currentLabel: String, currentColor: Int) {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        val labelInput = EditText(this).apply { setText(currentLabel) }
        layout.addView(android.widget.TextView(this).apply { text = getString(R.string.object_name) })
        layout.addView(labelInput)
        layout.addView(android.widget.TextView(this).apply { text = getString(R.string.object_color) })

        val spinner = android.widget.Spinner(this)
        spinner.adapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_spinner_item, colors.map { it.first }
        )
        layout.addView(spinner)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_object_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val label = labelInput.text.toString().ifBlank { currentLabel }
                val color = colors[spinner.selectedItemPosition].second
                viewModel.updateObject(objectId, label, color)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun sendNetworkByEmail() {
        // Capturer la GraphView en bitmap
        val bitmap = Bitmap.createBitmap(graphView.width, graphView.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        graphView.draw(canvas)


        val file = File(getExternalFilesDir(null), "network_screenshot.png")
        try {
            val out = java.io.FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.email_error), Toast.LENGTH_SHORT).show()
            return
        }


        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        // Créer l'intent d'envoi
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.email_subject))
            putExtra(android.content.Intent.EXTRA_TEXT, getString(R.string.email_body))
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(android.content.Intent.createChooser(intent, getString(R.string.send_by_email)))
    }

    private fun showConnectionContextMenu(connection: fr.istic.mob.networkbahcritie.model.Connection) {
        val options = arrayOf(getString(R.string.edit), getString(R.string.delete))
        AlertDialog.Builder(this)
            .setTitle(connection.label.ifBlank { getString(R.string.connection_label) })
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditConnectionDialog(connection)
                    1 -> viewModel.removeConnection(connection.id)
                }
            }
            .show()
    }

    private fun showEditConnectionDialog(connection: fr.istic.mob.networkbahcritie.model.Connection) {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        val labelInput = EditText(this).apply { setText(connection.label) }
        layout.addView(android.widget.TextView(this).apply { text = getString(R.string.object_name) })
        layout.addView(labelInput)
        layout.addView(android.widget.TextView(this).apply { text = getString(R.string.object_color) })

        val colorSpinner = android.widget.Spinner(this)
        colorSpinner.adapter = android.widget.ArrayAdapter(
            this, android.R.layout.simple_spinner_item, colors.map { it.first }
        )
        layout.addView(colorSpinner)
        layout.addView(android.widget.TextView(this).apply { text = getString(R.string.object_thickness) })
        val thicknessInput = EditText(this).apply { setText(connection.thickness.toString()) }
        layout.addView(thicknessInput)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_connection_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val label = labelInput.text.toString()
                val color = colors[colorSpinner.selectedItemPosition].second
                val thickness = thicknessInput.text.toString().toFloatOrNull() ?: connection.thickness
                viewModel.editConnection(connection.id, label, color, thickness)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}