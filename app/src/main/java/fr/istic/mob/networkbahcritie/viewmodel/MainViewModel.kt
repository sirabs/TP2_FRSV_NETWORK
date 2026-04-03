package fr.istic.mob.networkbahcritie.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import fr.istic.mob.networkbahcritie.R
import fr.istic.mob.networkbahcritie.model.ConnectedObject
import fr.istic.mob.networkbahcritie.model.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import fr.istic.mob.networkbahcritie.model.Connection


enum class EditMode {
    ADD_NODE, ADD_EDGE, EDIT
}

data class AddObjectDialogState(
    val show: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _graph = MutableStateFlow(Graph())
    val graph: StateFlow<Graph> = _graph
    private val _tempConnection = MutableStateFlow<Pair<Pair<Float,Float>, Pair<Float,Float>>?>(null)
    val tempConnection: StateFlow<Pair<Pair<Float,Float>, Pair<Float,Float>>?> = _tempConnection

    private var edgeStartObject: ConnectedObject? = null

    private val _addObjectDialogState = MutableStateFlow(AddObjectDialogState())
    val addObjectDialogState: StateFlow<AddObjectDialogState> = _addObjectDialogState

    private val _mode = MutableStateFlow(EditMode.ADD_NODE)
    val mode: StateFlow<EditMode> = _mode

    private val _selectedPlan = MutableStateFlow(R.drawable.plan1)
    val selectedPlan: StateFlow<Int> = _selectedPlan

    private val gson = Gson()
    private var selectedObject: ConnectedObject? = null
    private var viewWidth = 0f
    private var viewHeight = 0f

    fun setViewBounds(width: Float, height: Float) {
        viewWidth = width
        viewHeight = height
    }

    fun setMode(mode: EditMode) {
        _mode.value = mode
    }

    fun hasSelectedObject(): Boolean = selectedObject != null

    fun selectPlan(planResId: Int) {
        _selectedPlan.value = planResId
        _graph.value = Graph(selectedPlan = planResId)
    }

    fun onAddObjectRequested(x: Float, y: Float) {
        _addObjectDialogState.value = AddObjectDialogState(show = true, x = x, y = y)
    }

    fun onAddObjectDialogDismiss() {
        _addObjectDialogState.value = AddObjectDialogState(show = false)
    }

    fun addObject(label: String, iconRes: Int? = null) {
        val currentState = _addObjectDialogState.value
        if (currentState.show) {
            val newObject = ConnectedObject(
                label = label,
                x = currentState.x,
                y = currentState.y,
                iconRes = iconRes
            )
            viewModelScope.launch {
                _graph.update { currentGraph ->
                    currentGraph.copy(objects = (currentGraph.objects + newObject).toMutableList())
                }
                onAddObjectDialogDismiss()
            }
        }
    }

    fun startConnection(x: Float, y: Float) {
        edgeStartObject = _graph.value.objects.find { obj ->
            x >= obj.x - 60f && x <= obj.x + 60f && y >= obj.y - 60f && y <= obj.y + 60f
        }
        edgeStartObject?.let {
            _tempConnection.value = Pair(Pair(it.x, it.y), Pair(x, y))
        }
    }
    fun updateConnection(x: Float, y: Float) {
        edgeStartObject?.let {
            _tempConnection.value = Pair(Pair(it.x, it.y), Pair(x, y))
        }
    }
    fun addConnection(fromId: String, toId: String, label: String) {
        val connection = Connection(fromId = fromId, toId = toId, label = label)
        val newConnections = _graph.value.connections.toMutableList()


        val alreadyExists = newConnections.any {
            (it.fromId == fromId && it.toId == toId) ||
                    (it.fromId == toId && it.toId == fromId)
        }

        if (!alreadyExists) {
            newConnections.add(connection)
            _graph.value = _graph.value.copy(
                connections = newConnections
            )
        } else {
            _connectionError.value = true
        }
    }

    fun canAddConnection(fromId: String, toId: String): Boolean {
        if (fromId == toId) return false
        val exists = _graph.value.connections.any {
            (it.fromId == fromId && it.toId == toId) ||
                    (it.fromId == toId && it.toId == fromId)
        }
        return !exists
    }

    private val _connectionError = MutableStateFlow(false)
    val connectionError: StateFlow<Boolean> = _connectionError

    fun clearConnectionError() {
        _connectionError.value = false
    }

    fun endConnection(x: Float, y: Float): Pair<String, String>? {
        _tempConnection.value = null
        val start = edgeStartObject ?: return null
        val end = _graph.value.objects.find { obj ->
            x >= obj.x - 60f && x <= obj.x + 60f && y >= obj.y - 60f && y <= obj.y + 60f
        }
        edgeStartObject = null
        if (end == null || end.id == start.id) return null
        return Pair(start.id, end.id)
    }

    fun selectObjectAt(x: Float, y: Float) {
        selectedObject = _graph.value.objects.find { obj ->
            x >= obj.x - 60f && x <= obj.x + 60f && y >= obj.y - 60f && y <= obj.y + 60f
        }
    }

    fun moveSelectedObjectBy(dx: Float, dy: Float) {
        selectedObject?.let { obj ->
            obj.x += dx
            obj.y += dy
            _graph.value = _graph.value.copy(
                objects = _graph.value.objects.toMutableList(),
                connections = _graph.value.connections.toMutableList()
            )
        }
    }


    fun unselectObject() {
        selectedObject = null
    }

    fun resetNetwork() {
        viewModelScope.launch {
            _graph.value = Graph()
        }
    }

    fun saveNetwork(name: String = "network_save") {
        viewModelScope.launch {
            try {

                _graph.value.objects.forEach { obj ->
                    obj.iconName = when (obj.iconRes) {
                        R.drawable.ic_thermostat -> "ic_thermostat"
                        R.drawable.ic_camera     -> "ic_camera"
                        R.drawable.ic_serrure    -> "ic_serrure"
                        R.drawable.ic_ampoule    -> "ic_ampoule"
                        R.drawable.ic_detecteur  -> "ic_detecteur"
                        else -> null
                    }
                }
                val json = gson.toJson(_graph.value)
                val file = File(getApplication<Application>().filesDir, "$name.json")
                file.writeText(json)
            } catch (e: Exception) {
                android.util.Log.e("DEBUG", "Erreur sauvegarde : ${e.message}")
            }
        }
    }


    fun loadNetwork(name: String = "network_save"): Boolean {
        return try {
            val file = File(getApplication<Application>().filesDir, "$name.json")
            if (!file.exists()) return false
            val json = file.readText()
            val loadedGraph = gson.fromJson(json, Graph::class.java) ?: return false


            loadedGraph.objects.forEach { obj ->
                obj.iconRes = when (obj.iconName) {
                    "ic_thermostat" -> R.drawable.ic_thermostat
                    "ic_camera"     -> R.drawable.ic_camera
                    "ic_serrure"    -> R.drawable.ic_serrure
                    "ic_ampoule"    -> R.drawable.ic_ampoule
                    "ic_detecteur"  -> R.drawable.ic_detecteur
                    else -> null
                }
            }

            _graph.value = loadedGraph
            if (loadedGraph.selectedPlan != 0) {
                _selectedPlan.value = loadedGraph.selectedPlan
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("DEBUG", "Erreur chargement : ${e.message}", e)
            false
        }
    }

    fun removeObject(objectId: String) {
        val newObjects = _graph.value.objects.toMutableList().also { it.removeAll { obj -> obj.id == objectId } }
        val newConnections = _graph.value.connections.toMutableList().also { it.removeAll { conn -> conn.fromId == objectId || conn.toId == objectId } }
        _graph.value = _graph.value.copy(
            objects = newObjects,
            connections = newConnections
        )
    }

    fun editConnection(connectionId: String, label: String, color: Int, thickness: Float) {
        val newConnections = _graph.value.connections.map { conn ->
            if (conn.id == connectionId) conn.copy(label = label, color = color, thickness = thickness)
            else conn
        }.toMutableList()
        _graph.value = _graph.value.copy(connections = newConnections)
    }

    fun updateObject(objectId: String, label: String, color: Int) {
        val newObjects = _graph.value.objects.map { obj ->
            if (obj.id == objectId) obj.copy(label = label, color = color)
            else obj
        }.toMutableList()
        _graph.value = _graph.value.copy(objects = newObjects)
    }


    fun removeConnection(connectionId: String) {
        val newConnections = _graph.value.connections.toMutableList()
            .also { it.removeAll { conn -> conn.id == connectionId } }
        _graph.value = _graph.value.copy(
            objects = _graph.value.objects.toMutableList(),
            connections = newConnections
        )
    }



    fun findConnectionAt(x: Float, y: Float): Connection? {
        return _graph.value.connections.find { connection ->
            val fromObj = _graph.value.objects.find { it.id == connection.fromId } ?: return@find false
            val toObj = _graph.value.objects.find { it.id == connection.toId } ?: return@find false

            val midX = (fromObj.x + toObj.x) / 2
            val midY = (fromObj.y + toObj.y) / 2
            val dx = toObj.x - fromObj.x
            val dy = toObj.y - fromObj.y
            val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

            val ctrlX = if (len > 1f) midX + (-dy / len) * connection.curvature * 2 else midX
            val ctrlY = if (len > 1f) midY + (dx / len) * connection.curvature * 2 else midY

            // Utiliser PathMeasure pour trouver le vrai milieu
            val path = android.graphics.Path()
            path.moveTo(fromObj.x, fromObj.y)
            path.quadTo(ctrlX, ctrlY, toObj.x, toObj.y)

            val pm = android.graphics.PathMeasure(path, false)
            val pos = FloatArray(2)
            pm.getPosTan(pm.length / 2, pos, null)

            val dist = Math.sqrt(((x - pos[0]) * (x - pos[0]) + (y - pos[1]) * (y - pos[1])).toDouble()).toFloat()
            dist <= 60f
        }
    }

    fun updateConnectionCurvature(connectionId: String, x: Float, y: Float) {
        val connection = _graph.value.connections.find { it.id == connectionId } ?: return
        val fromObj = _graph.value.objects.find { it.id == connection.fromId } ?: return
        val toObj = _graph.value.objects.find { it.id == connection.toId } ?: return
        val dx = toObj.x - fromObj.x
        val dy = toObj.y - fromObj.y
        val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        if (len < 1f) return
        val midX = (fromObj.x + toObj.x) / 2
        val midY = (fromObj.y + toObj.y) / 2
        val perpX = -dy / len
        val perpY = dx / len
        connection.curvature = (x - midX) * perpX + (y - midY) * perpY
        _graph.value = _graph.value.copy(
            objects = _graph.value.objects.toMutableList(),
            connections = _graph.value.connections.toMutableList()
        )
    }

    fun findObjectAt(x: Float, y: Float): ConnectedObject? {
        return _graph.value.objects.find { obj ->
            x >= obj.x - 60f && x <= obj.x + 60f && y >= obj.y - 60f && y <= obj.y + 60f
        }
    }


    fun getSavedNetworkNames(): List<String> {
        return getApplication<Application>().filesDir
            .listFiles { f: File -> f.extension == "json" }
            ?.map { it.nameWithoutExtension } ?: emptyList()
    }
}