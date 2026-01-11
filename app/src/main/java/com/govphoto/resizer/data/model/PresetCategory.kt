package com.govphoto.resizer.data.model

/**
 * Categories for organizing photo presets.
 */
enum class PresetCategory(
    val displayName: String,
    val displayNameHi: String,
    val icon: String,
    val sortOrder: Int
) {
    IDENTITY_CARDS(
        displayName = "Identity Cards",
        displayNameHi = "पहचान पत्र",
        icon = "fingerprint",
        sortOrder = 1
    ),
    TRAVEL_VISAS(
        displayName = "Travel & Visas",
        displayNameHi = "यात्रा और वीज़ा",
        icon = "public",
        sortOrder = 2
    ),
    CENTRAL_EXAMS(
        displayName = "Central Government Exams",
        displayNameHi = "केंद्र सरकार परीक्षाएं",
        icon = "account_balance",
        sortOrder = 3
    ),
    STATE_EXAMS(
        displayName = "State Government Exams",
        displayNameHi = "राज्य सरकार परीक्षाएं",
        icon = "location_city",
        sortOrder = 4
    ),
    BANKING(
        displayName = "Banking & Finance",
        displayNameHi = "बैंकिंग और वित्त",
        icon = "account_balance_wallet",
        sortOrder = 5
    ),
    DEFENCE(
        displayName = "Defence & Paramilitary",
        displayNameHi = "रक्षा और अर्धसैनिक",
        icon = "military_tech",
        sortOrder = 6
    ),
    RAILWAYS(
        displayName = "Railways",
        displayNameHi = "रेलवे",
        icon = "train",
        sortOrder = 7
    ),
    TEACHING(
        displayName = "Teaching & Education",
        displayNameHi = "शिक्षण और शिक्षा",
        icon = "school",
        sortOrder = 8
    ),
    EDUCATION(
        displayName = "Education Entrance",
        displayNameHi = "प्रवेश परीक्षा",
        icon = "school",
        sortOrder = 10
    ),
    JOB_EXAMS(
        displayName = "Job Recruitment",
        displayNameHi = "नौकरी भर्ती",
        icon = "work",
        sortOrder = 11
    ),
    CUSTOM(
        displayName = "Custom Size",
        displayNameHi = "कस्टम साइज़",
        icon = "tune",
        sortOrder = 9
    )
}
