package com.petly_app.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.petly_app.PetlyApplication

class PetlyMessagingService : FirebaseMessagingService() {
    private val push get() = (application as PetlyApplication).push

    override fun onNewToken(token: String) {
        push.rotate(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val event = message.data["evento"]?.toLongOrNull()?.takeIf { it > 0 } ?: return
        val binding = message.data["vinculo"] ?: return
        push.show(event, binding)
    }
}
