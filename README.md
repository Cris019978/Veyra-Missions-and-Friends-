🧭 Veyra: Missions and Friends

Veyra es una aplicación móvil de exploración urbana y gamificación desarrollada para Android. Convierte el mundo real en un tablero interactivo mediante generación procedural de misiones, seguimiento GPS en tiempo real y un sistema de progresión estilo RPG, todo impulsado por una arquitectura completamente autónoma y local.
Estado del Proyecto: Versión Alpha / v1.0 (Offline-First Architecture). Diseñada para operar de forma independiente sin depender de servidores externos o conexión constante a la nube.
✨ ¿Qué ofrece Veyra?

🗺️ Exploración y Generación Procedural
Misiones Dinámicas en Campo: El sistema genera objetivos, desafíos y puntos de interés de forma procedural alrededor de la ubicación real del jugador.
Radar de Proximidad GPS: Cálculo continuo de distancias relativas para activar misiones y eventos cuando el explorador se acerca físicamente a un objetivo.
Mission Feed Local: Tablero reactivo que muestra las misiones disponibles en el área, detallando su descripción, distancia, marca de tiempo y autor.

📈 Progresión Estilo RPG
Sistema de Niveles y Experiencia (XP): Curva de dificultad progresiva con un escalado multiplicador de 1.5\times por cada nivel alcanzado, equilibrando el avance y recompensando la exploración constante.
Estadísticas de Jugador: Seguimiento local de nivel, experiencia acumulada, misiones completadas y asistencias.

🛡️ Sistema de Roles e Identidad
Verificación Local Segura: Control de acceso interno sin servidores externos que asigna insignias y privilegios mediante códigos exclusivos para el equipo de Veyra Team (Creadores) y Beta Testers.
Personalización Material You: Interfaz moderna en modo oscuro adaptativo que permite configurar nombre visible, identificador único (@usuario), avatar y banner personalizado.
Marcadores Diferenciados en Mapa: Identificación visual personalizada según el rango del usuario o creador de la misión.

🏗️ Arquitectura Técnica
Enfoque Offline-First: Toda la persistencia de datos (perfiles, roles, inventario de misiones y progreso de nivel) se gestiona directamente en el dispositivo mediante SharedPreferences y almacenamiento local estructurado. Esto garantiza latencia cero, privacidad total y funcionamiento fluido en zonas con baja cobertura.
Plataforma: Android nativo (Kotlin / Jetpack Compose).
Geolocalización: Integración con servicios de ubicación GPS del dispositivo para detección de proximidad y renderizado de puntos de interés.

🚀 Hoja de Ruta (Roadmap)

✅ v1.0 (Actual - Alpha)
Arquitectura 100% local sin dependencias en la nube.
Generador procedural de misiones y Mission Feed local.
Sistema de verificación por códigos para roles de Creador y Beta Tester.
Fórmula de progresión de XP escalable (1.5\times).

🔜 Próximos Pasos (v1.1)
Notificaciones Locales: Alertas automáticas en segundo plano al entrar en el radio de una misión o al generarse nuevos desafíos cercanos.
Optimización GPS: Refinamiento de precisión y filtrado de ubicación en entornos urbanos densos.
Onboarding Interactivo: Guía visual integrada para nuevos exploradores (actualmente diseñado bajo filosofía aprender jugando).

📱 Instalación (Build Alpha)

Descarga el archivo .apk oficial proporcionado por Veyra Team.
Habilita la instalación desde orígenes desconocidos en tu dispositivo Android si el sistema lo solicita.
Si tienes una versión previa instalada, desinstálala antes para inicializar limpia la base de datos local.
Crea tu perfil de explorador (o ingresa tu código especial de acceso si perteneces al equipo de pruebas).

👥 Créditos
Desarrollado con dedicación por Veyra Team. Explora tu entorno. Completa desafíos. Sube de nivel.
