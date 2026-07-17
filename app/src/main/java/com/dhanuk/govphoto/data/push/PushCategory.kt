package com.dhanuk.govphoto.data.push

enum class PushCategory(val storageKey: String, val defaultEnabled: Boolean) {
    RELEASE_NOTES(storageKey = "push_release_notes", defaultEnabled = true),
    EXAM_DEADLINES(storageKey = "push_exam_deadlines", defaultEnabled = false),
    SUPPORT_REPLIES(storageKey = "push_support_replies", defaultEnabled = true),
}
