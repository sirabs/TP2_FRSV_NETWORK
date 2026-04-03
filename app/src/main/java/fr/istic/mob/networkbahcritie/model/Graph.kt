package fr.istic.mob.networkbahcritie.model

data class Graph(

    val objects: MutableList<ConnectedObject> = mutableListOf(),
    val connections: MutableList<Connection> = mutableListOf(),
    var selectedPlan: Int = 0

) {

        



    fun addObject(obj: ConnectedObject) {
        objects.add(obj)
    }

    fun removeObject(objectId: String) {
        // Supprimer l'objet
        objects.removeAll { it.id == objectId }

        // Supprimer toutes les connexions associées
        connections.removeAll {
            it.fromId == objectId || it.toId == objectId
        }
    }

    fun getObjectById(id: String): ConnectedObject? {
        return objects.find { it.id == id }
    }





    fun addConnection(connection: Connection): Boolean {


        if (connection.fromId == connection.toId) return false

        // 2️⃣ Vérifier que les objets existent
        val fromExists = objects.any { it.id == connection.fromId }
        val toExists = objects.any { it.id == connection.toId }

        if (!fromExists || !toExists) return false

        // 3️⃣ Pas de double connexion (A→B ou B→A)
        val exists = connections.any {
            (it.fromId == connection.fromId && it.toId == connection.toId) ||
                    (it.fromId == connection.toId && it.toId == connection.fromId)
        }

        if (exists) return false

        connections.add(connection)
        return true
    }

    fun removeConnection(connectionId: String) {
        connections.removeAll { it.id == connectionId }
    }

    fun getConnectionById(id: String): Connection? {
        return connections.find { it.id == id }
    }





    fun reset() {
        objects.clear()
        connections.clear()
    }
}