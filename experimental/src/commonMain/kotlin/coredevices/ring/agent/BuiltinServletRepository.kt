package coredevices.ring.agent

import coredevices.indexai.agent.ServletRepository
import coredevices.mcp.client.McpIntegration
import coredevices.ring.agent.builtin_servlets.calendar.CalendarServlet
import coredevices.ring.agent.builtin_servlets.clock.ClockServlet
import coredevices.ring.agent.builtin_servlets.googlehome.GoogleHomeServlet
import coredevices.ring.agent.builtin_servlets.js.JsServlet
import coredevices.ring.agent.builtin_servlets.messaging.MessagingServlet
import coredevices.ring.agent.builtin_servlets.notes.CreateNoteTool
import coredevices.ring.agent.builtin_servlets.notes.NoteServlet
import coredevices.ring.agent.builtin_servlets.reminders.ReminderServlet
import coredevices.indexai.data.McpServerDefinition
import coredevices.ring.BuildKonfig
import coredevices.util.Platform
import coredevices.util.isAndroid
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

class BuiltinServletRepository: KoinComponent, ServletRepository {
    private val platform: Platform by inject()

    override fun getAllServlets(): List<McpServerDefinition> {
        return buildList {
            addAll(
                listOf(
                    McpServerDefinition(
                        name = NoteServlet.NAME,
                        title = "Note Creation"
                    ),
                    /*McpServerDefinition(
                        name = JsServlet.name,
                        title = "JavaScript Evaluation"
                    ),*/
                    McpServerDefinition(
                        name = ReminderServlet.NAME,
                        title = "Reminders"
                    ),
                    McpServerDefinition(
                        name = CalendarServlet.NAME,
                        title = "Calendar"
                    )
                )
            )
            if (platform.isAndroid) {
                addAll(
                    listOf(
                        McpServerDefinition(
                            name = ClockServlet.name,
                            title = "Timers & Alarms"
                        ),
                        McpServerDefinition(
                            name = MessagingServlet.name,
                            title = "Beeper Messaging"
                        )
                    )
                )
                if (BuildKonfig.GOOGLE_HOME_ENABLED) {
                    add(
                        McpServerDefinition(
                            name = GoogleHomeServlet.NAME,
                            title = "Google Home"
                        )
                    )
                }
            }
        }
    }

    override fun resolveName(name: String): McpIntegration? {
        return when (name) {
            NoteServlet.NAME -> NoteServlet(
                createNoteTool = CreateNoteTool(get())
            )
            ClockServlet.name -> ClockServlet
            JsServlet.name -> JsServlet
            ReminderServlet.NAME -> ReminderServlet(get(), get())
            CalendarServlet.NAME -> CalendarServlet
            MessagingServlet.name -> {
                require(platform.isAndroid) { "Messaging servlet is only available on Android" }
                MessagingServlet
            }
            GoogleHomeServlet.NAME -> {
                require(platform.isAndroid && BuildKonfig.GOOGLE_HOME_ENABLED) {
                    "Google Home servlet is unavailable in this build"
                }
                GoogleHomeServlet
            }
            else -> null
        }
    }
}
