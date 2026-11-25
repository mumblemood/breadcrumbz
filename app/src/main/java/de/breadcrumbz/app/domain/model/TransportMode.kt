package de.breadcrumbz.app.domain.model

/** Verkehrsmittel für die Kilometer- und Linien-Darstellung. */
enum class TransportMode(val label: String) {
    WALK("Zu Fuß"),
    BIKE("Fahrrad"),
    KAYAK("Kajak"),
    CAR("Auto"),
    TRAIN("Bahn"),
    PLANE("Flugzeug"),
    CAMPER("Campingbus"),
}

/** Symbol für Menüs und Pop-ups – überall dasselbe Bild zum Verkehrsmittel. */
fun TransportMode.symbol(): String = when (this) {
    TransportMode.WALK -> "🚶"
    TransportMode.BIKE -> "🚲"
    TransportMode.KAYAK -> "🛶"
    TransportMode.CAR -> "🚗"
    TransportMode.TRAIN -> "🚆"
    TransportMode.PLANE -> "✈️"
    TransportMode.CAMPER -> "🚐"
}

/** AUTO = Linie passend zum Verkehrsmittel, DIREKT = glatter Spline. */
enum class RouteMode { AUTO, DIRECT }
