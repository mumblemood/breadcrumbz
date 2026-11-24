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

/** AUTO = Linie passend zum Verkehrsmittel, DIREKT = glatter Spline. */
enum class RouteMode { AUTO, DIRECT }
