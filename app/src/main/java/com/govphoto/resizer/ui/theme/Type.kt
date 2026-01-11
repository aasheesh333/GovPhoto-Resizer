package com.govphoto.resizer.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Using system default font, similar to Public Sans
val PublicSans = FontFamily.Default

object AppTypography {
    // Headlines
    val HeadlineLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.015).sp
    )
    
    val HeadlineMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.015).sp
    )
    
    val HeadlineSmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.015).sp
    )
    
    // Titles
    val TitleLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    )
    
    val TitleMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    )
    
    val TitleSmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    
    // Body Text
    val BodyLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
    
    val BodyMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    
    val BodySmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
    
    // Labels
    val LabelLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    
    val LabelMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
    
    val LabelSmall = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
    
    // Button Text
    val ButtonLarge = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
    
    val ButtonMedium = TextStyle(
        fontFamily = PublicSans,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
}
